"""Conservative survival policy; unsafe unsupported conditions stop automation."""
from math import dist
from apexcraft.actions.interaction import InteractionAction
from apexcraft.navigation.movement import MoveAction


class SurvivalManager:
    def __init__(self, driver, *, critical_health=6, hunger_threshold=10, escape_radius=5):
        self.driver = driver
        self.critical_health, self.hunger_threshold = critical_health, hunger_threshold
        self.escape_radius = escape_radius

    def action(self, snapshot):
        if snapshot.health <= 0:
            raise RuntimeError('Player is dead; cannot continue objective')
        if self.driver.dangerous(snapshot.position):
            x,y,z = map(int,snapshot.position)
            candidates = [(x+dx,y,z+dz) for dx in range(-self.escape_radius,self.escape_radius+1)
                          for dz in range(-self.escape_radius,self.escape_radius+1)]
            safe = [p for p in candidates if self.driver.traversable(p) and not self.driver.dangerous(p)]
            if not safe:
                raise RuntimeError('No observed safe escape location')
            target = min(safe,key=lambda p:(dist(snapshot.position,p),p))
            return MoveAction(self.driver,target),1000
        if snapshot.hunger < self.hunger_threshold or snapshot.health < self.critical_health:
            if not snapshot.inventory.get('food',0):
                raise RuntimeError('Survival requires food; no safe food available in inventory')
            hunger,health = snapshot.hunger,snapshot.health
            return InteractionAction(self.driver.eat,
                lambda w: w.hunger > hunger or w.health > health,self.driver.release_inputs),100
        return None
