import unittest
from apexcraft.actions.acquisition import CollectResourceAction
from apexcraft.agent.task import Task, TaskStatus
from apexcraft.runtime.events import EventBus
from apexcraft.simulation.world import SimulationWorld
from apexcraft.world.model import WorldModel


class AcquisitionTests(unittest.TestCase):
    def setUp(self):
        self.bus = EventBus()
        self.world = WorldModel(self.bus)
        self.game = SimulationWorld()

    def run_task(self, task):
        for _ in range(300):
            task.tick(self.game.advance(self.world))
            if task.status in {TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.BLOCKED}:
                return
        self.fail('Acquisition exceeded budget')

    def test_navigate_mine_collect_exact_quantity(self):
        self.game.blocks.update({(5,0,0):'log',(6,0,0):'log',(7,0,0):'log'})
        action = CollectResourceAction(self.game,'log',2)
        task = Task('logs',action,self.bus)
        self.run_task(task)
        self.assertTrue(task.is_complete(),task.failure_reason)
        self.assertEqual(self.game.inventory['log'],2)
        self.assertEqual(self.game.mined,2)

    def test_resource_disappears_selects_alternative(self):
        self.game.blocks.update({(5,0,0):'log',(6,0,2):'log'})
        action = CollectResourceAction(self.game,'log',1)
        task = Task('logs',action,self.bus)
        task.tick(self.game.advance(self.world))
        del self.game.blocks[(5,0,0)]
        self.run_task(task)
        self.assertTrue(task.is_complete(),task.failure_reason)
        self.assertIn((5,0,0),action.rejected)

    def test_broken_tool_and_missing_resource_do_not_succeed(self):
        self.game.inventory['wooden_pickaxe'] = 1
        self.game.durability['wooden_pickaxe'] = 1
        self.game.blocks.update({(1,0,0):'cobblestone',(2,0,0):'cobblestone'})
        task = Task('stone',CollectResourceAction(self.game,'cobblestone',2),self.bus)
        self.run_task(task)
        self.assertEqual(task.status,TaskStatus.FAILED)
        self.assertEqual(self.game.inventory['cobblestone'],1)

    def test_cancellation_stops_movement(self):
        self.game.blocks[(8,0,0)] = 'log'
        task = Task('logs',CollectResourceAction(self.game,'log',1),self.bus)
        task.tick(self.game.advance(self.world))
        task.cancel()
        self.assertFalse(self.game.inputs)
