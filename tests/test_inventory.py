import unittest
from apexcraft.inventory.recipes import Recipe, DependencyResolver, standard_catalog
from apexcraft.inventory.model import InventoryManager
from apexcraft.simulation.world import SimulationWorld


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.recipes, self.sources = standard_catalog()
        self.resolver = DependencyResolver(self.recipes, self.sources)

    def test_existing_materials_reused(self):
        steps = self.resolver.resolve({'furnace':1}, {'cobblestone':40, 'crafting_table':1})
        self.assertEqual([(s.kind,s.item) for s in steps], [('craft','furnace')])

    def test_recursion_and_shared_ingredients_not_double_spent(self):
        steps = self.resolver.resolve({'stone_pickaxe':1}, {})
        self.assertIn('wooden_pickaxe', [s.item for s in steps])
        self.assertEqual(steps[-1].item, 'stone_pickaxe')
        steps = self.resolver.resolve({'planks':4,'stick':4}, {'log':1})
        self.assertEqual(sum(s.count for s in steps if s.item == 'log'), 1)

    def test_cycle_and_missing_recipe(self):
        resolver = DependencyResolver({'a':Recipe('a',1,{'b':1}), 'b':Recipe('b',1,{'a':1})}, {})
        with self.assertRaisesRegex(ValueError, 'cycle'):
            resolver.resolve({'a':1},{})
        with self.assertRaisesRegex(ValueError, 'strategy'):
            self.resolver.resolve({'unobtainium':1},{})

    def test_stack_accounting_and_protection(self):
        inventory = InventoryManager({'log':64,'iron_pickaxe':1},slots=2)
        self.assertEqual(inventory.used_slots, 2)
        self.assertFalse(inventory.can_add('log',1))
        self.assertNotIn('log', inventory.discardable({'log':64,'iron_pickaxe':1}))
        with self.assertRaises(RuntimeError):
            inventory.select_tool({'iron_pickaxe':3},3,{'iron_pickaxe':0})

    def test_crafting_and_equipment_are_real_state_changes(self):
        game = SimulationWorld()
        game.inventory.update({'iron_ingot':5,'crafting_table':1})
        game.produce(self.recipes['iron_helmet'])
        self.assertEqual(game.inventory['iron_helmet'],1)
        self.assertEqual(game.inventory['iron_ingot'],0)
        game.tick_count += 1
        game.equip('iron_helmet','head')
        self.assertEqual(game.equipment['head'],'iron_helmet')
        self.assertEqual(game.inventory['iron_helmet'],0)

    def test_failed_craft_is_atomic(self):
        game = SimulationWorld()
        game.inventory['iron_ingot'] = 4
        before = game.inventory.copy()
        with self.assertRaises(RuntimeError):
            game.produce(self.recipes['iron_helmet'])
        self.assertEqual(game.inventory,before)
