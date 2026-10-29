import unittest
from types import SimpleNamespace
from apexcraft.navigation.pathfinder import PathSearch
from apexcraft.navigation.movement import MoveAction
from apexcraft.runtime.cancellation import CancellationToken, CancelledError


class Grid:
    def __init__(self):
        self.position = (0, 0, 0)
        self.blocked = set()
        self.stalled = False
        self.releases = 0

    def traversable(self, p):
        return p not in self.blocked and 0 <= p[0] < 5 and 0 <= p[2] < 3 and p[1] == 0

    def adjacent(self, a, b):
        return sum(abs(x-y) for x, y in zip(a, b)) == 1

    def neighbors(self, p):
        x, y, z = p
        return [(n, 1) for n in [(x+1,y,z),(x-1,y,z),(x,y,z+1),(x,y,z-1)] if self.traversable(n)]

    def move_toward(self, p):
        if not self.stalled and self.adjacent(self.position, p) and self.traversable(p):
            self.position = p

    def release_inputs(self):
        self.releases += 1


class NavigationTests(unittest.TestCase):
    def run_action(self, grid, action, limit=100):
        action.start(CancellationToken())
        for _ in range(limit):
            action.tick(SimpleNamespace(position=grid.position))
            if action.is_complete():
                return
        self.fail('Movement did not terminate')

    def test_obstacle_detour(self):
        grid = Grid()
        grid.blocked.add((1,0,0))
        action = MoveAction(grid, (4,0,0), search_budget=1)
        self.run_action(grid, action)
        self.assertTrue(action.verify(SimpleNamespace(position=grid.position)))

    def test_unreachable_and_search_budget(self):
        grid = Grid()
        grid.blocked.update((1,0,z) for z in range(3))
        search = PathSearch((0,0,0), (4,0,0), grid.neighbors)
        while not search.done:
            search.step(CancellationToken(), 1)
        self.assertEqual(search.failure, 'No traversable route')
        search = PathSearch((0,0,0), (4,0,0), Grid().neighbors, max_nodes=1)
        search.step(CancellationToken())
        self.assertIn('budget', search.failure)

    def test_stuck_is_bounded(self):
        grid = Grid()
        grid.stalled = True
        with self.assertRaises(RuntimeError):
            self.run_action(grid, MoveAction(grid, (4,0,0), stuck_ticks=1, max_replans=1))
        self.assertGreater(grid.releases, 0)

    def test_changed_terrain_and_displacement(self):
        grid = Grid()
        action = MoveAction(grid, (4,0,0))
        action.start(CancellationToken())
        action.tick(SimpleNamespace(position=grid.position))
        grid.blocked.add((2,0,0))
        grid.position = (0,0,2)
        for _ in range(50):
            action.tick(SimpleNamespace(position=grid.position))
            if action.done:
                break
        self.assertTrue(action.done)
        self.assertGreater(action.replans, 0)

    def test_search_cancellation(self):
        token = CancellationToken()
        search = PathSearch((0,0,0), (4,0,0), Grid().neighbors)
        token.cancel()
        with self.assertRaises(CancelledError):
            search.step(token)
