# Fabric 26.3 migration and build status

The user's implementation target is a Fabric client mod for the current 26.x
Minecraft series. Java is now the production target; the Python package remains
a standalone algorithm/simulation reference.

Version evidence: FabricMC/fabric-example-mod commit
`91f19010a4e764f193fe50acfe2c8ee68f1f15cd` specifies Minecraft 26.3, Loader 0.20.1,
Loom 1.19-SNAPSHOT, Fabric API 0.165.0+26.3, Java 25, and Gradle 9.8.2.
The contemporaneous Fabric API source also declares 26.3. Mojang's live version
manifest could not be read because the network proxy denied its host; therefore
26.3 is the verified current Fabric example target, not an independently verified
latest Mojang release across all channels.

## Layout

- `src/main/java/dev/apexcraft/core`: game-independent observation, cancellation,
  verified task tree, scheduler, incremental graph search, movement and collection
  actions, generic recipe dependency resolver.
- `src/client/java/dev/apexcraft/client`: Fabric entry point and normal Minecraft
  inputs/interactions. No packet manipulation, evasion, or separate account login.
- `src/coreTest/java`: dependency-free executable core tests. Gradle `check` invokes
  the `coreTest` JavaExec harness; `scripts/test-java-core.sh` runs it without Loom.
- `src/main/resources/fabric.mod.json`: client-only 26.3 metadata.

## Confirmed validation

Java 25.0.5+8 (Temurin) downloaded from its official GitHub release and verified
against the release SHA-256. Standalone core compile and executable checks pass.
The Gradle wrapper distribution checksum is pinned. CI source includes the core,
Python simulations, and a full Fabric build job. GitHub run 41000104336 passed compilation, all
36 core checks, and the Python simulation suite and uploaded the mod artifact.
Loom is now pinned to the resolved version 1.19.3.

Full Gradle build reaches plugin resolution but receives HTTP 403 for
`https://maven.fabricmc.net/.../1.19-SNAPSHOT/maven-metadata.xml` through the configured
proxy. This restriction affects the local cloud machine. The adapter was compiled on
GitHub Actions after fixing configuration-cache resource expansion and the 26.3
screen/chat APIs. A development JAR is uploaded by successful workflow runs;
no in-game behavior or release readiness is claimed.

## Cloud setup

Required HTTPS hosts: `maven.fabricmc.net`, `meta.fabricmc.net`,
`piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`,
`resources.download.minecraft.net`; package-manager presets supply Gradle/Maven
and GitHub hosts. These additions are saved in the environment draft; the running
policy still denies them. Review/save environment settings, then publish the
prepared environment. Saving a draft does not apply network changes.

Java does not automatically honor HTTP_PROXY/HTTPS_PROXY. Use explicit JVM proxy
properties with the environment's existing CA truststore. Never disable TLS or
checksum verification. The cloud build helper configures these only for its command.
Use an existing isolated checkout; do not create a worktree for routine tasks.

## Required next checks once downloads are permitted

1. Run `./gradlew --no-daemon build` locally when downloads are permitted, or use
   the successful GitHub Actions artifact.
2. Launch `./gradlew runClient` in a graphical environment and a disposable world.
3. Verify walking around obstacles, a missing source, required mining tools, a full
   inventory, normal item pickup, and F8 cancellation during movement and mining.
4. Verify opening chat/inventory, losing focus, death, disconnect, and dimension
   change release all held inputs before further actions.
5. Port recipe acquisition and planner orchestration into the client incrementally;
   use actual server-visible container/recipe state, not simulation shortcuts.

Current adapter limits: same-height walking within 48 blocks of the player,
no fluids/bridging/breaking paths, a bounded nearby scan, no nearest-source ranking,
manual tool selection, no inventory cleanup or food automation, and no persistent
live-world checkpoints. Fabric snapshots may change; pin a resolved stable Loom
release when the official metadata is reachable.
