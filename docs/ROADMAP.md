# Roadmap

Each phase requires a small complete slice, build, unit and integration tests,
failure diagnosis, diff review, and a meaningful commit before advancing.

1. **Foundation — implemented:** event bus, world snapshots, action contract,
   hierarchical verified tasks, priority scheduler, structured logging,
   cooperative cancellation and controller emergency stop; simulation tests.
2. **Basic movement — next:** bounded pathfinding, movement controller, waypoints,
   collision awareness, stuck recovery and rerouting. Start with deterministic
   terrain tests. Select a permitted Minecraft adapter before live movement tests.
3. **World interaction:** observed blocks, mining, placement, collection and
   interaction with verified game outcomes and adapter rate limits.
4. **Inventory and crafting:** recipe graph, reusable inventory allocation,
   crafting, mining/tool requirements, fuel and equipment.
5. **Resource acquisition:** generic locate/collect/mine/craft requirement tasks.
6. **Autonomous planner:** objective parsing, dependency planning, incremental
   task generation, failure memory and alternative acquisition strategies.
7. **Survival:** threats, hunger, health, escape and verified safe interruption.
8. **Long-term memory:** persistent world-scoped resources, structures, hazards,
   failure strategies and checkpoint restoration with invalidation.
9. **Advanced objectives:** blueprints, farms, Nether progression and bases.
10. **Optimization:** measured CPU/memory/search/scan/task bottlenecks only.

Future commands must submit through the same scheduler/planner. Full iron armour
from a fresh spawn remains the eventual target, not a Phase 1 capability. Benchmark
A–E: stone pickaxe, iron pickaxe, full iron armour, diamond, starter shelter. Record
completion rate/time, path time, replans, stuck events, excess travel/resources,
and deaths with reproducible seeds and versions once these objectives exist.
