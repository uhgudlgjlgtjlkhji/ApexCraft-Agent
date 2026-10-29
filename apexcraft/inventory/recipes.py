from dataclasses import dataclass, field
from math import ceil
from collections import Counter


@dataclass(frozen=True)
class Recipe:
    item: str
    output: int
    ingredients: dict[str, int]
    requirements: dict[str, int] = field(default_factory=dict)
    kind: str = 'craft'

    def __post_init__(self):
        if self.output < 1 or self.kind not in {'craft', 'smelt'} or any(
            n < 1 for n in [*self.ingredients.values(), *self.requirements.values()]):
            raise ValueError('Invalid recipe')


@dataclass(frozen=True)
class Step:
    kind: str
    item: str
    count: int


class DependencyResolver:
    """Projected inventory ledger prevents double spending across branches."""
    def __init__(self, recipes, sources):
        self.recipes = recipes
        self.sources = sources

    def resolve(self, requirements, inventory):
        if any(n < 0 for n in requirements.values()):
            raise ValueError('Negative requirement')
        stock = Counter(inventory)
        steps = []
        visiting = set()

        def obtain(item, count):
            deficit = count - stock[item]
            if deficit <= 0:
                return
            if item in visiting:
                raise ValueError(f'Recipe dependency cycle: {item}')
            visiting.add(item)
            recipe = self.recipes.get(item)
            if recipe:
                batches = ceil(deficit / recipe.output)
                # Retained prerequisites (tools, workstations) are never consumed.
                for dependency, amount in recipe.requirements.items():
                    obtain(dependency, amount)
                for dependency, amount in recipe.ingredients.items():
                    obtain(dependency, amount * batches)
                    stock[dependency] -= amount * batches
                stock[item] += recipe.output * batches
                steps.append(Step(recipe.kind, item, batches))
            elif item in self.sources:
                for dependency, amount in self.sources[item].items():
                    obtain(dependency, amount)
                stock[item] += deficit
                steps.append(Step('collect', item, deficit))
            else:
                raise ValueError(f'No acquisition strategy for {item}')
            visiting.remove(item)

        for item, count in requirements.items():
            obtain(item, count)
            # Reserve the requested outputs against later branches.
            stock[item] -= count
        return steps


def standard_catalog():
    recipes = [
        Recipe('planks',4,{'log':1}), Recipe('stick',4,{'planks':2}),
        Recipe('crafting_table',1,{'planks':4}),
        Recipe('wooden_pickaxe',1,{'planks':3,'stick':2},{'crafting_table':1}),
        Recipe('stone_pickaxe',1,{'cobblestone':3,'stick':2},{'crafting_table':1}),
        Recipe('furnace',1,{'cobblestone':8},{'crafting_table':1}),
        Recipe('iron_ingot',1,{'raw_iron':1,'coal':1},{'furnace':1},'smelt'),
        Recipe('iron_pickaxe',1,{'iron_ingot':3,'stick':2},{'crafting_table':1}),
        Recipe('diamond_pickaxe',1,{'diamond':3,'stick':2},{'crafting_table':1}),
    ]
    for piece, count in [('helmet',5),('chestplate',8),('leggings',7),('boots',4)]:
        recipes.append(Recipe(f'iron_{piece}',1,{'iron_ingot':count},{'crafting_table':1}))
    sources = {'log':{}, 'cobblestone':{'wooden_pickaxe':1},
               'coal':{'wooden_pickaxe':1}, 'raw_iron':{'stone_pickaxe':1},
               'diamond':{'iron_pickaxe':1}, 'food':{}, 'seed':{}, 'dirt':{}}
    return {r.item:r for r in recipes}, sources
