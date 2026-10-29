import tempfile
from pathlib import Path
import unittest
from apexcraft.memory.store import WorldKnowledge
from apexcraft.agent.goals import GoalManager
from apexcraft.runtime.events import EventBus
from apexcraft.world.model import WorldModel
from apexcraft.simulation.scenarios import progression_world


class MemoryTests(unittest.TestCase):
    def test_observation_invalidation_and_failure_expiry(self):
        memory = WorldKnowledge('a')
        memory.observe((1,2,3),'coal',1)
        memory.observe((1,2,3),'lava',2)
        self.assertFalse(memory.resources)
        self.assertTrue(memory.hazards)
        memory.observe((1,2,3),None,3)
        self.assertFalse(memory.hazards)
        memory.reject('route','state-a',10,ttl=5)
        self.assertTrue(memory.rejected('route','state-a',11))
        self.assertFalse(memory.rejected('route','state-b',11))
        memory.reject('route','state-a',10,ttl=5)
        self.assertFalse(memory.rejected('route','state-a',15))

    def test_checkpoint_resume_reobserves_and_finishes_remaining_work(self):
        bus = EventBus()
        world = WorldModel(bus)
        game = progression_world()
        manager = GoalManager(game,world,bus)
        manager.submit('get full iron armour')
        for _ in range(120):
            game.advance(world)
            manager.tick()
        mined_before = game.mined
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'checkpoint.json'
            manager.checkpoint(path)
            manager.stop()
            restored = GoalManager(game,world,bus)
            restored.restore(path)
            for _ in range(10000):
                game.advance(world)
                restored.tick()
                if restored.status != 'RUNNING':
                    break
            self.assertEqual(restored.status,'SUCCESS',restored.reason)
            self.assertGreater(game.mined,mined_before)
            with self.assertRaisesRegex(ValueError,'identity'):
                WorldKnowledge('different-world').restore(path,restored)
