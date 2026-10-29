import unittest
from apexcraft.agent.goals import GoalManager
from apexcraft.building.blueprints import starter_shelter
from apexcraft.runtime.events import EventBus
from apexcraft.world.model import WorldModel
from apexcraft.simulation.scenarios import progression_world


class BuildingTests(unittest.TestCase):
    def test_starter_shelter_from_empty_inventory_and_repair(self):
        bus = EventBus()
        model = WorldModel(bus)
        game = progression_world()
        # Dedicated build site must not contain fixture ore; retain finite resources elsewhere.
        for p in list(game.blocks):
            if 2 <= p[0] <= 6 and 0 <= p[2] <= 4:
                item = game.blocks.pop(p)
                game.blocks[(p[0],p[1],p[2]-16)] = item
        # Fixture needs enough logs for the shell and tool progression.
        for x in range(-20,20):
            game.blocks[(x,0,-10)] = 'log'
        manager = GoalManager(game,model,bus)
        manager.submit('build a starter shelter')
        for _ in range(10000):
            game.advance(model)
            manager.tick()
            if manager.status != 'RUNNING':
                break
        self.assertEqual(manager.status,'SUCCESS',manager.reason)
        self.assertTrue(all(game.blocks.get(p) == item for p,item in starter_shelter()))
        del game.blocks[(2,-1,0)]
        manager.submit('build a starter shelter')
        for _ in range(1000):
            game.advance(model)
            manager.tick()
            if manager.status != 'RUNNING':
                break
        self.assertEqual(manager.status,'SUCCESS',manager.reason)
