# ApexCraft

A foundation for autonomous Minecraft agents on single-player worlds, test
servers, and servers where automation is explicitly permitted.

**Implemented: Phase 1 only.** This package is a tested runtime, not yet a Minecraft
client. It cannot navigate, mine, craft, or accept natural-language objectives.

Requires Python 3.11+; no runtime dependencies. From the checkout:

```sh
python -m unittest discover -s tests -v
python -m pip wheel --no-deps --no-build-isolation --wheel-dir /tmp/apexcraft-wheels .
```

Building requires setuptools 68+ and wheel in the build environment. Standard
`python -m pip install .` can instead use PEP 517 build isolation.

The host creates an EventBus, WorldModel, TaskScheduler, and AgentController,
submits a Task tree backed by Action adapters, feeds observations into the model,
and calls controller.tick(). Each tick advances at most one action. Every action
must independently verify its result against the latest snapshot. Call
controller.stop() for emergency cancellation and unconditional input release.

See [architecture](docs/ARCHITECTURE.md), [task lifecycle](docs/TASK_SYSTEM.md),
[navigation design](docs/NAVIGATION.md), [testing](docs/TESTING.md), and
[roadmap](docs/ROADMAP.md).
