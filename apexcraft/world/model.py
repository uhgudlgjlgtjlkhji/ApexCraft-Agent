"""Versioned observations; snapshots never expose mutable inventory state."""
from dataclasses import dataclass, field, replace
from types import MappingProxyType
from collections.abc import Mapping
import math


@dataclass(frozen=True)
class WorldSnapshot:
    version: int = 0
    position: tuple[float, float, float] = (0, 0, 0)
    health: float = 20
    hunger: int = 20
    inventory: Mapping[str, int] = field(default_factory=dict)

    def __post_init__(self):
        if len(self.position) != 3 or not all(math.isfinite(x) for x in self.position):
            raise ValueError('Position must have three finite coordinates')
        if not 0 <= self.health <= 20 or not 0 <= self.hunger <= 20:
            raise ValueError('Invalid health or hunger')
        if any(not isinstance(n, int) or n < 0 for n in self.inventory.values()):
            raise ValueError('Inventory counts must be nonnegative integers')
        object.__setattr__(self, 'position', tuple(self.position))
        object.__setattr__(self, 'inventory', MappingProxyType(dict(self.inventory)))


@dataclass(frozen=True)
class WorldUpdated:
    snapshot: WorldSnapshot


class WorldModel:
    def __init__(self, events):
        self.events = events
        self.snapshot = WorldSnapshot()

    def observe(self, **changes) -> WorldSnapshot:
        if 'version' in changes:
            raise ValueError('Observation versions are assigned by WorldModel')
        self.snapshot = replace(self.snapshot, **changes, version=self.snapshot.version + 1)
        self.events.publish(WorldUpdated(self.snapshot))
        return self.snapshot
