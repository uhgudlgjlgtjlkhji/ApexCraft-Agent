"""Waypoint execution with bounded recovery and observation-based progress."""
from apexcraft.navigation.pathfinder import PathSearch


class MoveAction:
    def __init__(self, driver, target, *, stuck_ticks=5, max_replans=4, search_budget=64):
        if stuck_ticks < 1 or max_replans < 0 or search_budget < 1:
            raise ValueError('Invalid navigation budgets')
        self.driver, self.target = driver, tuple(target)
        self.stuck_ticks, self.max_replans = stuck_ticks, max_replans
        self.search_budget = search_budget
        self.replans = self.stuck_events = 0
        self.blacklist = set()
        self.path = []
        self.search = None
        self.wait = 0
        self.previous = None
        self.done = False

    def start(self, token):
        self.token = token
        token.check()
        self.path = []
        self.search = None
        self.wait = 0
        self.previous = None
        self.done = False

    def _replan(self, blocked=None):
        self.replans += 1
        self.driver.release_inputs()
        if self.replans > self.max_replans:
            raise RuntimeError('Navigation recovery exhausted')
        if blocked is not None:
            self.blacklist.add(blocked)
        self.search = None
        self.path = []
        self.wait = 0

    def tick(self, world):
        self.token.check()
        position = tuple(world.position)
        if position == self.target:
            self.done = True
            self.driver.release_inputs()
            return
        if self.path and position == self.path[0]:
            self.path.pop(0)
            self.wait = 0
        if self.previous is not None and position != self.previous:
            if self.path and not self.driver.adjacent(position, self.path[0]):
                self._replan()
        self.previous = position
        if not self.path:
            if self.search is None:
                self.search = PathSearch(position, self.target, lambda p: (
                    (n, cost) for n, cost in self.driver.neighbors(p)
                    if n not in self.blacklist))
            self.search.step(self.token, self.search_budget)
            if self.search.failure:
                raise RuntimeError(self.search.failure)
            if not self.search.done:
                return
            self.path = self.search.path[1:]
            self.search = None
        if not self.path:
            return
        waypoint = self.path[0]
        if not self.driver.traversable(waypoint):
            self._replan(waypoint)
            return
        if self.wait >= self.stuck_ticks:
            self.stuck_events += 1
            self._replan(waypoint)
            return
        self.driver.move_toward(waypoint)
        self.wait += 1

    def is_complete(self):
        return self.done

    def verify(self, world):
        return tuple(world.position) == self.target

    def cancel(self):
        self.driver.release_inputs()
        self.search = None
        self.path = []
