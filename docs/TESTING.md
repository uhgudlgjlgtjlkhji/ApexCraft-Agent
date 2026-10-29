# Testing

From repository root with Python 3.11+:

```sh
python -m unittest discover -s tests -v
python -m compileall -q apexcraft tests
python -m pip wheel --no-deps --no-build-isolation --wheel-dir /tmp/apexcraft-wheels .
```

No network, Minecraft server, credentials, or game installation is needed for
Phase 1 tests. Wheel builds require setuptools 68+ and wheel installed.

The deterministic ObservedAction simulation in tests exercises real controller,
scheduler, task, event, and observation systems together. It deliberately permits
an attempted action to finish without producing inventory, testing that completion
cannot bypass verification. Other cases cover missing prerequisites, invalid
observations, timeout, action errors, priority preemption, pause/resume, hierarchy
cancellation, cleanup failure, and explicit bounded recovery.

These tests validate runtime contracts, not Minecraft behavior. Tool breaking is
represented only as an action failure; mining mechanics, inventory capacity, mob
behavior, terrain, pathfinding, recipes, memory persistence and benchmark objectives
are unimplemented. Their scenario tests arrive with the corresponding phases.
A simulated high-priority task validates interruption, not threat recognition.

Add regressions for discovered defects. Keep skipped, failed, and unrun checks
separate from passing tests. A future live integration suite must pin game and
adapter versions and run only against worlds where automation is permitted.
