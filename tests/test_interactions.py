import unittest
from apexcraft.simulation.world import SimulationWorld
from apexcraft.actions.interaction import InteractionAction
from apexcraft.agent.task import Task, TaskStatus
from apexcraft.runtime.events import EventBus
from apexcraft.world.model import WorldModel


class InteractionTests(unittest.TestCase):
    def setUp(self):
        self.bus = EventBus()
        self.model = WorldModel(self.bus)
        self.game = SimulationWorld()

    def test_mine_collect_and_place_verified_observations(self):
        p = (1,0,0)
        self.game.blocks[p] = 'log'
        operations = [
            (lambda: self.game.mine(p), lambda w: p not in self.game.blocks),
            (lambda: self.game.collect(p), lambda w: w.inventory.get('log') == 1),
            (lambda: self.game.place(p, 'log'), lambda w: self.game.blocks.get(p) == 'log'),
        ]
        for index, (operation, predicate) in enumerate(operations):
            task = Task(str(index), InteractionAction(operation, predicate, self.game.release_inputs), self.bus)
            task.tick(self.game.advance(self.model))
            self.assertFalse(task.is_complete())
            task.tick(self.game.advance(self.model))
            self.assertTrue(task.is_complete())

    def test_full_inventory_keeps_dropped_item(self):
        self.game.capacity = 0
        self.game.drops[(1,0,0)] = ('log',1)
        with self.assertRaisesRegex(RuntimeError, 'full'):
            self.game.collect((1,0,0))
        self.assertIn((1,0,0), self.game.drops)

    def test_impossible_mining_rejected_without_world_mutation(self):
        self.game.blocks[(1,0,0)] = 'diamond'
        with self.assertRaisesRegex(RuntimeError, 'tool'):
            self.game.mine((1,0,0))
        self.assertEqual(self.game.blocks[(1,0,0)], 'diamond')

    def test_missing_resource_fails_task(self):
        action = InteractionAction(lambda: self.game.mine((1,0,0)), lambda w: True, self.game.release_inputs)
        task = Task('missing', action, self.bus)
        task.tick(self.game.advance(self.model))
        self.assertEqual(task.status, TaskStatus.FAILED)

    def test_rate_limit_and_cancel(self):
        self.game.blocks[(1,0,0)] = 'log'
        self.game.mine((1,0,0))
        with self.assertRaisesRegex(RuntimeError, 'rate'):
            self.game.collect((1,0,0))
        self.game.inputs.add('attack')
        self.game.release_inputs()
        self.assertFalse(self.game.inputs)
