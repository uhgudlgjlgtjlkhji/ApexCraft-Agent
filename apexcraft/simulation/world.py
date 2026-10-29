"""Finite deterministic test world, explicitly not a Minecraft protocol adapter."""
from collections import Counter
from math import dist


class SimulationWorld:
    def __init__(self, *, size=32, capacity=36*64):
        self.size, self.capacity = size, capacity
        self.position = (0, 0, 0)
        self.blocks = {}
        self.drops = {}
        self.inventory = Counter()
        self.equipment = {}
        self.health, self.hunger = 20, 20
        self.stalled = False
        self.inputs = set()
        self.tick_count = 0
        self.last_interaction = -1
        self.interaction_interval = 1
        self.tool_levels = {'wooden_pickaxe': 1, 'stone_pickaxe': 2,
                            'iron_pickaxe': 3, 'diamond_pickaxe': 4}
        self.required_levels = {'cobblestone': 1, 'raw_iron': 2, 'diamond': 3}
        self.durability = {}
        self.stations = set()
        self.threats = set()
        self.distance_travelled = self.mined = 0

    def advance(self, model):
        self.tick_count += 1
        return model.observe(position=self.position, inventory=dict(self.inventory),
                             health=self.health, hunger=self.hunger)

    def traversable(self, p):
        return (len(p) == 3 and p[1] == 0 and -self.size <= p[0] <= self.size
                and -self.size <= p[2] <= self.size and p not in self.blocks)

    def adjacent(self, a, b):
        return sum(abs(x-y) for x, y in zip(a, b)) == 1

    def neighbors(self, p):
        x, y, z = p
        return [(n, 1) for n in ((x+1,y,z),(x-1,y,z),(x,y,z+1),(x,y,z-1))
                if self.traversable(n)]

    def move_toward(self, target):
        self.inputs.add('forward')
        if not self.stalled and self.adjacent(self.position, target) and self.traversable(target):
            self.position = target
            self.distance_travelled += 1

    def release_inputs(self):
        self.inputs.clear()

    def reachable(self, position):
        return dist(self.position, position) <= 1.5

    def _interaction(self):
        if self.tick_count - self.last_interaction < self.interaction_interval:
            raise RuntimeError('Interaction rate limit')
        self.last_interaction = self.tick_count

    def _space(self, count):
        if sum(self.inventory.values()) + count > self.capacity:
            raise RuntimeError('Inventory full')

    def mine(self, position):
        self._interaction()
        if not self.reachable(position):
            raise RuntimeError('Block out of reach')
        item = self.blocks.get(position)
        if item is None or item in {'lava', 'bedrock'}:
            raise RuntimeError('Missing or forbidden block')
        level = self.required_levels.get(item, 0)
        tools = [t for t in self.tool_levels if self.inventory[t] and self.durability.get(t, 50) > 0
                 and self.tool_levels[t] >= level]
        if level and not tools:
            raise RuntimeError('Required mining tool unavailable')
        if tools:
            tool = min(tools, key=lambda t: self.tool_levels[t])
            self.durability[tool] = self.durability.get(tool, 50) - 1
            if self.durability[tool] == 0:
                self.inventory[tool] -= 1
        del self.blocks[position]
        self.drops[position] = (item, 1)
        self.mined += 1

    def collect(self, position):
        self._interaction()
        if not self.reachable(position) or position not in self.drops:
            raise RuntimeError('Dropped item unavailable')
        item, amount = self.drops[position]
        self._space(amount)
        self.inventory[item] += amount
        del self.drops[position]

    def place(self, position, item):
        self._interaction()
        if not self.reachable(position) or position == self.position or position in self.blocks:
            raise RuntimeError('Placement obstructed or out of reach')
        if self.inventory[item] < 1:
            raise RuntimeError('Missing placement material')
        self.inventory[item] -= 1
        self.blocks[position] = item
        if item in {'crafting_table', 'furnace'}:
            self.stations.add(item)

    def produce(self, recipe, batches=1):
        self._interaction()
        if batches < 1:
            raise ValueError('Invalid batch count')
        for item, count in recipe.requirements.items():
            if self.inventory[item] < count:
                raise RuntimeError(f'Missing workstation or tool: {item}')
        for item, count in recipe.ingredients.items():
            if self.inventory[item] < count * batches:
                raise RuntimeError(f'Missing ingredient: {item}')
        after = self.inventory.copy()
        after.subtract({item: n*batches for item, n in recipe.ingredients.items()})
        after[recipe.item] += recipe.output * batches
        if sum(after.values()) > self.capacity:
            raise RuntimeError('Inventory full')
        self.inventory = after
        if recipe.item in self.tool_levels:
            self.durability[recipe.item] = 50

    def equip(self, item, slot):
        self._interaction()
        expected = {'head':'helmet', 'chest':'chestplate', 'legs':'leggings', 'feet':'boots'}
        if slot not in expected or not item.endswith(expected[slot]) or self.inventory[item] < 1:
            raise RuntimeError('Invalid or missing equipment')
        previous = self.equipment.get(slot)
        self.inventory[item] -= 1
        if previous:
            self.inventory[previous] += 1
        self.equipment[slot] = item

    def dangerous(self, position):
        return (self.blocks.get(tuple(position)) == 'lava'
                or any(dist(position, threat) <= 1.5 for threat in self.threats))

    def eat(self):
        self._interaction()
        if self.inventory['food'] < 1:
            raise RuntimeError('No food available')
        self.inventory['food'] -= 1
        self.hunger = min(20,self.hunger+6)
        self.health = min(20,self.health+2)
