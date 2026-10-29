"""Incremental resource acquisition using injected navigation and world access."""
from math import dist
from apexcraft.navigation.movement import MoveAction


class CollectResourceAction:
    def __init__(self, driver, item, target_count, *, max_failures=8):
        self.driver, self.item, self.target_count = driver, item, target_count
        self.max_failures = max_failures
        self.rejected = set()
        self.failure_count = 0
        self.target = self.movement = None
        self.done = False
        self.last_failure = None

    def start(self, token):
        self.token = token
        token.check()
        self.target = self.movement = None
        self.done = False

    def _reject(self, reason):
        self.last_failure = reason
        self.rejected.add(self.target)
        self.failure_count += 1
        if self.movement:
            self.movement.cancel()
        self.target = self.movement = None
        if self.failure_count >= self.max_failures:
            raise RuntimeError(f'Resource strategies exhausted: {reason}')

    def tick(self, world):
        self.token.check()
        if world.inventory.get(self.item, 0) >= self.target_count:
            self.done = True
            return
        if self.target is None:
            candidates = [p for p, item in self.driver.blocks.items()
                          if item == self.item and p not in self.rejected]
            candidates.extend(p for p, (item, _) in self.driver.drops.items()
                              if item == self.item and p not in self.rejected)
            if not candidates:
                raise RuntimeError(f'No known reachable source: {self.item}')
            self.target = min(candidates, key=lambda p: (dist(world.position,p),p))
        if not self.driver.reachable(self.target):
            if self.movement is None:
                approaches = [p for p, _ in self.driver.neighbors(self.target)]
                if not approaches:
                    self._reject('No safe approach')
                    return
                approach = min(approaches,key=lambda p: dist(world.position,p))
                self.movement = MoveAction(self.driver, approach)
                self.movement.start(self.token)
            try:
                self.movement.tick(world)
            except RuntimeError as error:
                self._reject(str(error))
            return
        if self.target in self.driver.drops:
            self.driver.collect(self.target)
            self.target = self.movement = None
        elif self.driver.blocks.get(self.target) == self.item:
            self.driver.mine(self.target)
        else:
            self._reject('Resource disappeared')

    def is_complete(self):
        return self.done

    def verify(self, world):
        return world.inventory.get(self.item, 0) >= self.target_count

    def cancel(self):
        if self.movement:
            self.movement.cancel()
        self.driver.release_inputs()
