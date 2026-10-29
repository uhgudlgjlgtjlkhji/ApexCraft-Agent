import unittest
from apexcraft.agent.goals import GoalManager
from apexcraft.runtime.events import EventBus
from apexcraft.world.model import WorldModel
from apexcraft.simulation.scenarios import progression_world
from apexcraft.planning.goals import ARMOUR, parse_goal


class GoalTests(unittest.TestCase):
    def setUp(self):
        self.bus = EventBus()
        self.world = WorldModel(self.bus)
        self.game = progression_world()
        self.manager = GoalManager(self.game,self.world,self.bus)

    def run_goal(self, text, limit=10000):
        self.manager.submit(text)
        for _ in range(limit):
            self.game.advance(self.world)
            self.manager.tick()
            if self.manager.status != 'RUNNING':
                break
        self.assertEqual(self.manager.status,'SUCCESS',self.manager.reason)

    def test_spawn_to_stone_pickaxe(self):
        self.run_goal('Get stone pickaxe.')
        self.assertEqual(self.game.inventory['stone_pickaxe'],1)

    def test_spawn_to_full_iron_armour_equipped(self):
        self.run_goal('Start from nothing and obtain full iron armour.')
        self.assertEqual(self.game.equipment,ARMOUR)
        self.assertFalse(self.game.inputs)

    def test_spawn_to_diamond(self):
        self.run_goal('collect 1 diamond')
        self.assertEqual(self.game.inventory['diamond'],1)

    def test_missing_resources_bounded_replanning(self):
        self.game.blocks.clear()
        self.manager.submit('collect 1 log')
        for _ in range(20):
            self.game.advance(self.world)
            self.manager.tick()
        self.assertEqual(self.manager.status,'BLOCKED')
        self.assertLessEqual(self.manager.replans,2)

    def test_tool_breaking_replans_and_crafts_replacement(self):
        self.game.inventory['wooden_pickaxe'] = 1
        self.game.durability['wooden_pickaxe'] = 1
        self.run_goal('collect 3 cobblestone')
        self.assertGreater(self.manager.replans,0)

    def test_pause_stop_and_no_work_after_stop(self):
        self.manager.submit('get full iron armour')
        self.manager.pause()
        self.manager.tick()
        self.assertEqual(self.game.mined,0)
        self.manager.resume()
        self.game.advance(self.world)
        self.manager.tick()
        self.manager.stop()
        before = self.game.position
        self.manager.tick()
        self.assertEqual(self.game.position,before)
        self.assertFalse(self.game.inputs)

    def test_parser_rejects_unimplemented_objective(self):
        with self.assertRaises(ValueError):
            parse_goal('conquer the end')
