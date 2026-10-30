# Baritone-based 0.3.0

ApexCraft now uses the official Baritone 1.20.0 Fabric release for Minecraft 26.3
for navigation and mining. The previous flat-grid walker remains only in the
standalone reference tests; the client no longer uses it.

## Installation

Use the exact `baritone-unoptimized-fabric-1.20.0.jar` shipped in the Actions
artifact, plus `apexcraft-0.3.0.jar` and Fabric API for 26.3. Remove older ApexCraft
and other Baritone JARs first. The unoptimized upstream release has the stable
helper names required by the input adapter. The workflow verifies its SHA-256.
The distribution keeps Baritone separate and replaceable, with its LGPL license
and corresponding source archive. ApexCraft's original code remains MIT.

## Objectives

- `/apex goal get diamonds`
- `/apex goal get 16 diamonds`
- `/apex get iron pickaxe`
- `/apex collect diamond 16`
- `/apex goto x y z`
- `/apex stop`, `/apex pause`, `/apex resume`, `/apex status`

The objective action inspects actual inventory after each step. It reuses an iron,
diamond or netherite pickaxe when one is present. Otherwise it resolves wood,
planks, sticks, a table, a wooden pickaxe, cobblestone, a stone pickaxe, raw iron,
fuel and a furnace; it smelts iron and crafts the iron pickaxe before selecting
the diamond mining step. A broken required tool stops that step and replans.
Workstation placement and normal container clicks are incremental, and the result
must arrive in inventory before the step is complete. Nearby/remembered placed
workstations can be reused rather than recrafted.

Baritone controls resource exploration and terrain traversal, including the
collision semantics of short and tall grass. Mining uses Baritone's visible-ore
exploration mode rather than reading hidden ore locations. The diamond mining
exploration level is -54; iron exploration uses level 16. No success is reported
when resources, routes, inventory space, or workstation placement are unavailable.
Each acquisition step has a finite budget and failed operations have bounded
recovery. Survival remains conservative pausing, not automatic combat or food.

## Mouse mining and movement

During an ApexCraft-owned process, a scoped mixin replaces Baritone's block-break
helper with normal attack-button input: one initial click and then a hold, released
on stop. The replacement does not call a block-destroy controller or construct
attack packets. Minecraft itself still sends the normal mining traffic required
for any physical mouse click. Inputs are suppressed when the real crosshair is
not on a block, the screen/focus is unsuitable, or the held tool cannot harvest it.
The hook applies only to the primary player while ApexCraft owns Baritone.

Movement uses Baritone's ordinary walking and terrain recovery with visible camera
rotation, smooth look enabled, sprinting/parkour disabled, and no silent look.
Workstation interactions turn the visible camera with bounded angular increments.
The user's original Baritone settings are restored after the action. These changes
are not a promise to bypass anti-cheat systems; compatibility depends on the server.

F8 and `/apex stop` cancel the process, release held controls and discard queued
mining/placement presses. Unknown screens pause normal tasks; only screens opened
by the crafting action are allowed to continue automated menu operations.

## Validation

Core tests cover the prerequisite graph, existing/better tools, broken tool
replanning, explicit output requirements, quantities, mouse press/release edges,
cancellation and bounded failure. Python simulations are separate references.

The development-only Fabric client test creates an actual integrated-server world
and exercises a narrow short/tall-grass corridor, diamond collection with an iron
pickaxe, smelting/crafting an iron pickaxe through real menus, and input cleanup.
CI requires the test's completion marker, so an empty test run cannot pass. Runtime
results must be read from the relevant Actions run; test source alone is not proof
of a pass. A full fresh-world diamond journey is not yet a benchmarked claim.

The shutdown regression test also requires Minecraft/Gradle to exit normally.
The upstream cache workers are stopped after Minecraft closes its worlds; passing
gameplay assertions alone does not excuse a shutdown watchdog failure.
