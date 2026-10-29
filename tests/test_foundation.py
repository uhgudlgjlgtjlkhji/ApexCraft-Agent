import io
import json
import logging
import unittest
from apexcraft.actions.base import Action
from apexcraft.agent.controller import AgentController
from apexcraft.agent.scheduler import TaskScheduler
from apexcraft.agent.task import Task, TaskStatus, TaskChanged
from apexcraft.debug.trace import DecisionTrace
from apexcraft.runtime.cancellation import CancellationToken, CancelledError
from apexcraft.runtime.events import EventBus
from apexcraft.world.model import WorldModel, WorldUpdated


class ObservedAction:
    """Simulation adapter: completion signals alone cannot prove acquisition."""
    def __init__(self, required=1, complete=True):
        self.required, self.complete = required, complete
        self.starts = self.steps = self.stops = 0

    def start(self, token):
        token.check()
        self.starts += 1

    def tick(self, world):
        self.steps += 1

    def is_complete(self):
        return self.complete

    def verify(self, world):
        return world.inventory.get('stone', 0) >= self.required

    def cancel(self):
        self.stops += 1


class FoundationTests(unittest.TestCase):
    def setUp(self):
        self.bus = EventBus()
        self.world = WorldModel(self.bus)
        self.scheduler = TaskScheduler()

    def task(self, name='goal', **kwargs):
        return Task(name, ObservedAction(), self.bus, **kwargs)

    def test_world_copies_inventory_and_invalidates_by_version(self):
        inventory = {'stone': 2}
        old = self.world.observe(inventory=inventory)
        inventory['stone'] = 10
        self.assertEqual(old.inventory['stone'], 2)
        with self.assertRaises(TypeError):
            old.inventory['stone'] = 4
        self.world.observe(inventory={})
        self.assertGreater(self.world.snapshot.version, old.version)
        self.assertNotIn('stone', self.world.snapshot.inventory)

    def test_invalid_observation_is_atomic(self):
        with self.assertRaises(ValueError):
            self.world.observe(inventory={'stone': -1})
        self.assertEqual(self.world.snapshot.version, 0)

    def test_event_unsubscribe_and_errors(self):
        seen = []
        unsubscribe = self.bus.subscribe(WorldUpdated, seen.append)
        self.world.observe()
        unsubscribe()
        unsubscribe()
        self.world.observe()
        self.assertEqual(len(seen), 1)
        def broken(event):
            raise RuntimeError('subscriber failed')
        self.bus.subscribe(WorldUpdated, broken)
        with self.assertRaises(RuntimeError):
            self.world.observe()

    def test_action_attempt_does_not_mean_success(self):
        task = self.task()
        self.scheduler.submit(task)
        self.scheduler.tick(self.world.snapshot)
        self.assertEqual(task.status, TaskStatus.BLOCKED)
        self.scheduler.tick(self.world.snapshot)
        self.assertEqual(task.action.steps, 1)
        self.assertIn('verification', task.get_failure_reason())

    def test_verified_success_and_terminal_idempotence(self):
        task = self.task()
        self.world.observe(inventory={'stone': 1})
        task.tick(self.world.snapshot)
        task.tick(self.world.snapshot)
        task.cancel()
        self.assertTrue(task.is_complete())
        self.assertEqual(task.action.steps, 1)

    def test_child_before_parent(self):
        parent, child = self.task('parent'), self.task('child')
        parent.add_child(child)
        self.scheduler.submit(parent)
        self.world.observe(inventory={'stone': 1})
        self.scheduler.tick(self.world.snapshot)
        self.assertTrue(child.is_complete())
        self.assertEqual(parent.action.steps, 0)
        self.scheduler.tick(self.world.snapshot)
        self.assertTrue(parent.is_complete())

    def test_cycles_and_duplicates_rejected(self):
        parent, child = self.task('p'), self.task('c')
        parent.add_child(child)
        with self.assertRaises(ValueError):
            child.add_child(parent)
        self.scheduler.submit(parent)
        with self.assertRaises(ValueError):
            self.scheduler.submit(child)
        self.assertEqual(len(self.scheduler.tasks), 2)

    def test_child_failure_propagates(self):
        parent, child = self.task('p'), self.task('c')
        parent.add_child(child)
        child._transition(TaskStatus.FAILED, 'tool broke')
        self.scheduler.submit(parent)
        self.scheduler.tick(self.world.snapshot)
        self.assertEqual(parent.status, TaskStatus.FAILED)

    def test_bounded_recovery_with_new_observation(self):
        task = self.task(max_retries=1)
        task.tick(self.world.snapshot)
        task.recover(ObservedAction())
        self.world.observe(inventory={'stone': 1})
        task.tick(self.world.snapshot)
        self.assertTrue(task.is_complete())
        with self.assertRaises(RuntimeError):
            task.recover(ObservedAction())

    def test_timeout_does_not_spin(self):
        task = Task('slow', ObservedAction(complete=False), self.bus, max_ticks=2)
        self.scheduler.submit(task)
        for _ in range(10):
            self.scheduler.tick(self.world.snapshot)
        self.assertEqual(task.status, TaskStatus.BLOCKED)
        self.assertEqual(task.action.steps, 2)

    def test_priority_preemption_preserves_original(self):
        low = Task('mine', ObservedAction(complete=False), self.bus)
        self.scheduler.submit(low)
        self.scheduler.tick(self.world.snapshot)
        high = self.task('survive', priority=100)
        self.world.observe(inventory={'stone': 1})
        self.scheduler.submit(high)
        self.scheduler.tick(self.world.snapshot)
        self.assertTrue(high.is_complete())
        self.assertEqual(low.status, TaskStatus.PENDING)
        self.assertEqual(low.action.stops, 1)
        self.scheduler.tick(self.world.snapshot)
        self.assertEqual(low.action.starts, 2)

    def test_pause_resume_and_emergency_stop_tree(self):
        parent, child = self.task('p'), self.task('c')
        parent.add_child(child)
        self.scheduler.submit(parent)
        released = []
        controller = AgentController(self.world, self.scheduler, lambda: released.append(True))
        controller.pause()
        controller.tick()
        self.assertEqual(child.action.steps, 0)
        controller.resume()
        controller.tick()
        controller.stop()
        controller.resume()
        controller.tick()
        self.assertEqual(parent.status, TaskStatus.CANCELLED)
        self.assertEqual(child.status, TaskStatus.CANCELLED)
        self.assertEqual(len(released), 2)

    def test_token_propagation_is_downward(self):
        root = CancellationToken()
        child = CancellationToken(root)
        child.cancel()
        self.assertFalse(root.cancelled)
        root.cancel()
        with self.assertRaises(CancelledError):
            CancellationToken(root).check()

    def test_action_error_reports_failure_and_cleans_up(self):
        task = self.task()
        def broken(world):
            raise RuntimeError('tool broke')
        task.action.tick = broken
        task.tick(self.world.snapshot)
        self.assertEqual(task.status, TaskStatus.FAILED)
        self.assertIn('tool broke', task.failure_reason)
        self.assertEqual(task.action.stops, 1)

    def test_stop_releases_inputs_even_when_adapter_cleanup_fails(self):
        task = self.task()
        def broken():
            raise RuntimeError('adapter cleanup failed')
        task.action.cancel = broken
        self.scheduler.submit(task)
        released = []
        controller = AgentController(self.world, self.scheduler, lambda: released.append(True))
        with self.assertRaises(ExceptionGroup):
            controller.stop()
        self.assertEqual(released, [True])
        self.assertEqual(task.status, TaskStatus.CANCELLED)

    def test_blocked_direct_tick_does_not_repeat_attempt(self):
        task = self.task()
        task.tick(self.world.snapshot)
        task.tick(self.world.snapshot)
        self.assertEqual(task.action.steps, 1)

    def test_child_cleanup_failure_still_cancels_siblings(self):
        parent, first, second = self.task('p'), self.task('a'), self.task('b')
        parent.add_child(first)
        parent.add_child(second)
        def broken():
            raise RuntimeError('cannot clean first')
        first.action.cancel = broken
        with self.assertRaises(ExceptionGroup):
            parent.cancel()
        self.assertEqual(parent.status, TaskStatus.CANCELLED)
        self.assertEqual(second.status, TaskStatus.CANCELLED)
        self.assertEqual(second.action.stops, 1)

    def test_stop_continues_when_running_action_cannot_suspend(self):
        running = Task('run', ObservedAction(complete=False), self.bus)
        other = self.task('other')
        self.scheduler.submit(running)
        self.scheduler.submit(other)
        self.scheduler.tick(self.world.snapshot)
        def broken():
            raise RuntimeError('release failure')
        running.action.cancel = broken
        released = []
        controller = AgentController(self.world, self.scheduler, lambda: released.append(True))
        with self.assertRaises(ExceptionGroup):
            controller.stop()
        self.assertEqual(running.status, TaskStatus.CANCELLED)
        self.assertEqual(other.status, TaskStatus.CANCELLED)
        self.assertEqual(released, [True])

    def test_structured_trace(self):
        stream = io.StringIO()
        logger = logging.Logger('test')
        logger.addHandler(logging.StreamHandler(stream))
        trace = DecisionTrace(logger)
        self.bus.subscribe(TaskChanged, trace.record)
        task = self.task()
        task.tick(self.world.snapshot)
        entries = [json.loads(line) for line in stream.getvalue().splitlines()]
        self.assertEqual(entries[-1]['status'], 'BLOCKED')
        self.assertEqual(entries[-1]['task_id'], 'goal')


if __name__ == '__main__':
    unittest.main()
