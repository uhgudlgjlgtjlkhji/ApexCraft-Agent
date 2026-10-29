"""Stack-aware accounting and protected item selection."""
from math import ceil


class InventoryManager:
    def __init__(self, items, *, slots=36, stack_sizes=None):
        if slots < 0 or any(n < 0 for n in items.values()):
            raise ValueError('Invalid inventory')
        self.items = dict(items)
        self.slots = slots
        self.stack_sizes = stack_sizes or {}

    def stack_size(self, item):
        return self.stack_sizes.get(item, 1 if any(
            item.endswith(s) for s in ('pickaxe', 'helmet', 'chestplate', 'leggings', 'boots')) else 64)

    @property
    def used_slots(self):
        return sum(ceil(n / self.stack_size(item)) for item, n in self.items.items() if n)

    def can_add(self, item, count):
        if count < 0:
            raise ValueError('Negative quantity')
        copy = dict(self.items)
        copy[item] = copy.get(item, 0) + count
        return InventoryManager(copy, slots=self.slots, stack_sizes=self.stack_sizes).used_slots <= self.slots

    def discardable(self, required, *, valuables=frozenset({'diamond', 'iron_ingot'})):
        return {item: count - required.get(item, 0) for item, count in self.items.items()
                if item not in valuables and count > required.get(item, 0)}

    def select_tool(self, levels, required_level, durability):
        candidates = [item for item, level in levels.items() if level >= required_level
                      and self.items.get(item, 0) and durability.get(item, 0) > 0]
        if not candidates:
            raise RuntimeError('No usable tool meets mining level')
        return min(candidates, key=lambda item: (levels[item], -durability[item]))
