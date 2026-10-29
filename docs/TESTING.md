# Current validation boundary

Java 25 standalone core: `scripts/test-java-core.sh` (36 explicit checks, no game
classes or fake stubs). Full Fabric build: `./gradlew build` (**blocked at Fabric
Maven HTTP 403**). No Minecraft client test has run and no mod JAR is validated.

Python reference: `python -m unittest discover -s tests -v` (53 tests). These include
progression to stone tools, iron armour and diamonds on finite known terrain,
building/repair, tool breakage, disappearing resources, food/threat interruptions,
and checkpoint restoration. No farming, Nether or live-server test is claimed.

The simulation intentionally simplifies reach, tool durability (50), stack capacity,
station access (inventory-owned abstract station), smelting (one coal per ingot),
food healing and dimension/terrain physics. It must not be used as evidence that
actual Minecraft crafting or survival works. Python InventoryManager has independent
stack accounting; the simulation's capacity check uses a total-item bound.

The remainder documents the original foundation setup and test methodology.

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
