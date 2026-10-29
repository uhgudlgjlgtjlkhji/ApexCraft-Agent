"""Incremental A* over an injected, observed movement graph."""
from dataclasses import dataclass
import heapq
import itertools
from math import inf


@dataclass(frozen=True)
class MovementCosts:
    walk: float = 1.0
    jump: float = 1.2
    water: float = 3.0
    fall: float = 1.5

    def __post_init__(self):
        if any(v <= 0 or v == inf for v in vars(self).values()):
            raise ValueError('Movement costs must be finite and positive')


class PathSearch:
    def __init__(self, start, goal, neighbors, *, heuristic=lambda a, b: 0,
                 max_nodes=10000):
        if max_nodes < 1:
            raise ValueError('max_nodes must be positive')
        self.goal, self.neighbors, self.heuristic = goal, neighbors, heuristic
        self.max_nodes, self.expanded = max_nodes, 0
        self.serial = itertools.count()
        self.frontier = [(heuristic(start, goal), next(self.serial), 0, start)]
        self.cost = {start: 0}
        self.parent = {}
        self.path = None
        self.failure = None

    @property
    def done(self):
        return self.path is not None or self.failure is not None

    def step(self, token, budget=64):
        if budget < 1:
            raise ValueError('Search budget must be positive')
        for _ in range(budget):
            token.check()
            if self.done:
                return
            if not self.frontier:
                self.failure = 'No traversable route'
                return
            _, _, g, node = heapq.heappop(self.frontier)
            if g != self.cost[node]:
                continue
            if node == self.goal:
                self.path = [node]
                while node in self.parent:
                    node = self.parent[node]
                    self.path.append(node)
                self.path.reverse()
                return
            self.expanded += 1
            if self.expanded > self.max_nodes:
                self.failure = 'Path search node budget exceeded'
                return
            for target, edge_cost in self.neighbors(node):
                if edge_cost == inf:
                    continue
                if not edge_cost > 0:
                    raise ValueError('Edges must have positive cost')
                candidate = g + edge_cost
                if candidate < self.cost.get(target, inf):
                    self.cost[target] = candidate
                    self.parent[target] = node
                    heapq.heappush(self.frontier, (
                        candidate + self.heuristic(target, self.goal),
                        next(self.serial), candidate, target))
