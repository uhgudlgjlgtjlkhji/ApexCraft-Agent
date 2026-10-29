# ApexCraft

A Fabric **client mod** targeting Minecraft **26.3**, Java **25**, Fabric Loader
**0.20.1**, and Fabric API **0.165.0+26.3**. Use only in single-player worlds and
on servers where automation is permitted. No separate bot account or external
Python process is required by the mod.

**Development status: not release-ready.** The standalone Java core passes tests;
the Fabric adapter has not compiled or run against Minecraft in this environment
because access to `maven.fabricmc.net` is denied. There is no validated mod JAR yet.
Do not treat the Python simulation's successful objectives as working mod features.

## Build and test

Install JDK 25 and run from the repository root:

```sh
scripts/test-java-core.sh
./gradlew --no-daemon build
```

Set `JAVA_HOME` if Java 25 is not the default. The Gradle wrapper verifies its
9.8.2 distribution checksum. A successful full build should produce
`build/libs/apexcraft-0.2.0.jar`. This filename is an expected build output,
not a claim that the JAR has been produced. The official Fabric example currently
uses Loom `1.19-SNAPSHOT`; the plugin is not locked to a resolved artifact yet.

After a verified build, install the JAR and the matching Fabric API in the mods
folder of a Fabric 26.3 client. No server mod is required by this client design.
Always validate in a disposable single-player world before using a server.

## Client adapter source

The adapter registers these local commands (not server chat messages):

- `/apex goto x y z`: conservative walking on known loaded terrain.
- `/apex collect minecraft:oak_log 8`: nearby block-item collection, verifying inventory.
- `/apex stop`, `/apex pause`, `/apex resume`.
- `/apex status`, `/apex tasks`, `/apex inventory`, `/apex debug`.

**F8** is the rebindable emergency stop. Disconnect stops automation. Opening a
screen, losing focus, low health/hunger, or entering water pauses and releases
inputs. Starting an objective is always explicit; installing the mod never starts
playing automatically. This adapter source is pending full build and in-game tests.

Natural-language objectives, live recursive crafting/smelting/equipping, advanced
movement, survival escape, building, farming, and Nether progression are **not yet
integrated into the Fabric mod**. Unsupported commands report that limitation.

## Planning reference and simulation

The dependency-free Python package in `apexcraft/` is a separate development
reference, not a runtime dependency of the mod:

```sh
python -m unittest discover -s tests -v
```

It exercises finite-world navigation, resource acquisition, recipe planning,
iron-armour equipment, survival interruption, checkpoint recovery, and shelter
construction. It uses fully known flat terrain and simplified game rules.
See [testing](docs/TESTING.md), [Fabric setup](docs/FABRIC.md),
[architecture](docs/ARCHITECTURE.md), and [roadmap](docs/ROADMAP.md).
