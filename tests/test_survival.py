import unittest
from apexcraft.agent.goals import GoalManager
from apexcraft.runtime.events import EventBus
from apexcraft.world.model import WorldModel
from apexcraft.simulation.scenarios import progression_world


class SurvivalTests(unittest.TestCase):
    def setUp(self):
        self.bus = EventBus()
        self.world = WorldModel(self.bus)
        self.game = progression_world()
        self.manager = GoalManager(self.game,self.world,self.bus)
        self.manager.submit('collect 2 log')

    def tick(self):
        self.game.advance(self.world)
        self.manager.tick()

    def test_threat_interrupts_and_resumes_original_goal(self):
        self.tick()
        original = self.manager.current
        self.game.threats.add(self.game.position)
        self.tick()
        self.assertIs(self.manager.current,original)
        self.assertIsNotNone(self.manager.safety_task)
        for _ in range(1000):
            self.tick()
            if self.manager.status != 'RUNNING':
                break
        self.assertEqual(self.manager.status,'SUCCESS',self.manager.reason)
        self.assertFalse(self.game.dangerous(self.game.position))

    def test_food_interrupt_is_verified(self):
        self.game.hunger = 5
        self.game.inventory['food'] = 2
        for _ in range(1000):
            self.tick()
            if self.manager.status != 'RUNNING':
                break
        self.assertEqual(self.manager.status,'SUCCESS',self.manager.reason)
        self.assertGreaterEqual(self.game.hunger,10)
        self.assertLess(self.game.inventory['food'],2)

    def test_missing_food_or_death_stops(self):
        self.game.health = 3
        self.tick()
        self.assertEqual(self.manager.status,'BLOCKED')
        self.assertFalse(self.game.inputs)

    def test_cancel_during_escape_releases_inputs(self):
        self.game.threats.add(self.game.position)
        self.tick()
        self.manager.stop()
        self.assertFalse(self.game.inputs)
