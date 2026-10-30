# ApexCraft

A Fabric **client mod** targeting Minecraft **26.3**, Java **25**, Fabric Loader
**0.20.1**, and Fabric API **0.165.0+26.3**. Use only in single-player worlds and
on servers where automation is permitted. No separate bot account or external
Python process is required by the mod.

**Development status: Baritone-based 0.3.0 is under client integration testing.**
ApexCraft now delegates mining and navigation to the official Baritone 1.20.0
Fabric release. Its scoped mining adapter uses normal mouse-button input instead
of Baritone's direct block-breaking helper. The objective planner obtains an iron
pickaxe when needed before collecting diamonds. See [the 0.3.0 design and validation
notes](docs/BARITONE.md) for the exact scope and test boundaries.

## Download the JAR

Open the latest successful [GitHub Actions build](https://github.com/uhgudlgjlgtjlkhji/ApexCraft-Agent/actions/workflows/validate.yml)
and download **ApexCraft-Fabric-26.3** under **Artifacts** (GitHub sign-in required).
For a successful 0.3.0 build, extract the ZIP and install **both**
`apexcraft-0.3.0.jar` and the supplied `baritone-unoptimized-fabric-1.20.0.jar`
alongside Fabric API for 26.3. Remove older ApexCraft/Baritone copies first.
The archive also includes Baritone's license and corresponding source.
Artifacts are retained for 30 days. This is a development build, not a release.

## Build and test

Install JDK 25 and run from the repository root:

```sh
scripts/test-java-core.sh
./gradlew --no-daemon build
```

Set `JAVA_HOME` if Java 25 is not the default. The Gradle wrapper verifies its
9.8.2 distribution checksum. A successful full build should produce
`build/libs/apexcraft-0.3.0.jar`. GitHub Actions produces this artifact. Loom is pinned to the successfully
resolved version `1.19.3`.

After a verified build, install the JAR and the matching Fabric API in the mods
folder of a Fabric 26.3 client. No server mod is required by this client design.
Always validate in a disposable single-player world before using a server.

## Client adapter source

The adapter registers these local commands (not server chat messages):

- `/apex goal get diamonds`, `/apex goal get 16 diamonds`: resolve tools and resources.
- `/apex get iron pickaxe`: obtain a specific tool, crafting it if necessary.

- `/apex goto x y z`: use Baritone navigation and terrain recovery.
- `/apex collect diamond 8`: resource acquisition through the same objective planner.
- `/apex stop`, `/apex pause`, `/apex resume`.
- `/apex status`, `/apex tasks`, `/apex inventory`, `/apex debug`.

**F8** is the rebindable emergency stop. Disconnect stops automation. Opening a
screen, losing focus, low health/hunger, or entering water pauses and releases
inputs. Starting an objective is always explicit; installing the mod never starts
playing automatically. Only successful client-test builds are uploaded by the current workflow.

The supported goal grammar is limited to item quantities and item names; it is not
a general language model. The current recipe catalog covers the wood → stone →
iron pickaxe → diamond chain. Equipment goals, automatic survival escape, building,
farming and Nether progression are not integrated into this client adapter.
No anti-cheat compatibility guarantee is made.

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
