# Navigation design and validation boundary

Python now has a tested flat-grid implementation. Java core search/movement tests
also pass. The Fabric walking adapter compiles on GitHub Actions but has no in-game tests;
advanced navigation below remains design work.

The next vertical slice is deterministic grid movement in simulation: bounded
incremental A*, collision checks, waypoint following, displacement detection,
progress tracking, local rerouting, temporary node blacklists, and failure
escalation. Use one pathfinder with an injected cost model and explicit expansion
and time budgets. Cancellation must interrupt search and clear movement inputs.

Treat lava as forbidden. Configure walk, jump, water, placement, break, and safe
fall costs in one policy. Initially support walking and collision-safe routing;
introduce swimming, climbing, breaking and bridging only with tested action and
resource preconditions. Unknown terrain is not proof of safe traversability.

Track distance-to-waypoint and displacement over a bounded observation window.
After no progress: local adjustment → route recalculation → temporary blacklist
→ alternate route → blocked task. Each stage has a finite retry budget. Record
failed nodes with world version and expiry; invalidate on relevant observations.
Do not repeat an identical failed route indefinitely.

Phase 2 acceptance: obstacle detour, unreachable target, world changes, player
displacement, stuck escalation, bounded search, and cancellation tests. Benchmark
path search separately from future live-server objective completion. No performance
or superiority claims are supported by Phase 1.
