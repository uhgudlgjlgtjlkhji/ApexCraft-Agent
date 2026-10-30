# Production target update

ApexCraft is now a Java 25 / Fabric 26.3 client mod. See [FABRIC.md](FABRIC.md)
for layout, authoritative version evidence, build blockers and validation boundaries.
The Java core mirrors the verified-action architecture described below. The Fabric
adapter compiles on GitHub Actions; in-game behavior is not yet validated. The following design text documents the original
Python reference runtime; it is not a claim that those capabilities exist in-game.

# Architecture

## Scope and choice

Phase 1 uses Python 3.11+ and a dependency-free, host-driven runtime. Python keeps
simulation tests fast and permits a future protocol adapter or external Minecraft
bridge without making the agent depend on a specific game version now. No wire
protocol, Minecraft client, GUI, or server connection is implemented. The adapter
choice must be made and validated before live-world claims.

## Data flow

Host observations → WorldModel → immutable versioned WorldSnapshot → scheduler
→ selected Task → injected Action → result verification → TaskChanged event.

- `runtime/events.py`: synchronous typed publication and explicit unsubscribe.
  Subscribers run over a stable copy; exceptions propagate rather than disappear.
- `runtime/cancellation.py`: downward-propagating cooperative tokens.
- `world/model.py`: validated atomic observations, inventory copies, and monotonic
  versions. An inventory observation replaces the previous inventory, removing
  stale entries. WorldUpdated publishes after the state commits.
- `actions/base.py`: structural adapter interface. start/tick/verify/cancel must
  be bounded and nonblocking. cancel is idempotent and releases held inputs.
- `agent/task.py`: action ownership, hierarchy, lifecycle, verification, bounded
  explicit recovery, and cancellation.
- `agent/scheduler.py`: stable integer priorities, prerequisite ordering,
  interruption, pause/resume, and stop. Larger priority wins; ties use submission
  order. A preempted action is stopped and restarted, keeping its task identity.
- `agent/controller.py`: host entry point and unconditional final input release.
- `debug/trace.py`: JSON decision events through an injected standard logger.

## Runtime contract

All mutation is confined to one host thread. Publish fresh observations before
advancing a tick; do not call ticks concurrently or reenter from an event handler.
Tasks are constructed fully before submission. IDs must be unique in a scheduler.
Action restart after pause must inspect current game state and be safe to repeat;
partial progress belongs in observations, not assumed command success. Finished
actions must quiesce on cancel without undoing verified work.

There are no hidden workers or sleeps. Hosts choose tick frequency and future
adapters enforce server-specific rate limits. Tick budgets bound attempts, not
wall-clock time; Python cannot forcibly preempt a blocking adapter. Emergency stop
is synchronous between bounded ticks; the host release_inputs callback must be
independent of action cleanup. Cleanup failures are surfaced as ExceptionGroup,
while stop still attempts remaining tasks and releases host inputs.

Future navigation, inventory, planning, survival, and persistence are separate
systems added in roadmap order. Empty modules are not supplied as implementations.
No evasion, exploit, hidden automation, or moderation-bypass behavior belongs here.
