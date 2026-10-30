# Roadmap and evidence

The production target is a Fabric 26.3 client mod. Version 0.3.0 uses the official
Baritone 1.20.0 unoptimized Fabric dependency and a scoped normal-mouse mining
adapter. See [BARITONE.md](BARITONE.md) for installation, implementation and the
successful two-trial real-client validation run.

| Phase | Current production scope | Remaining work |
| --- | --- | --- |
| 1 Foundation | Java verified tasks, scheduler, cancellation, startup/shutdown tested | Broader lifecycle scenarios |
| 2 Navigation | Baritone routes/recovery; grass corridor tested in Minecraft | More terrain and hazard fixtures |
| 3 Interaction | Mouse mining, workstation placement, normal GUI clicks tested | Generic building/interaction catalog |
| 4 Inventory/crafting | Recursive wood/stone/iron tool chain and smelting tested | More recipes, equipment, storage, fuel choices |
| 5 Acquisition | Incremental native mining and tool prerequisites tested | More resource strategies |
| 6 Autonomous planning | Item/count goals; inventory-change replanning; empty-inventory diamond chain tested | Broader goal grammar and alternative-strategy memory |
| 7 Survival | Conservative health/hunger/water pausing | Automatic food, escape, combat |
| 8 Memory | Baritone spatial cache | Persistent objective checkpoints in the client |
| 9 Advanced goals | Python shelter reference only | Client building, farming, Nether progression |
| 10 Optimization | No live performance claims | Representative benchmarks and measured optimization |

The Python package remains a separate algorithm/reference simulation. Passing those
tests is not proof of Minecraft behavior. The Java client suite now exercises real
Minecraft and server inventory state. Its two trials passed in GitHub run
41014988485; this is controlled-fixture validation, not a random-world success rate.

Local cloud Fabric downloads remain denied by network policy. GitHub Actions builds
and tests the mod and uploads the package. New implementation slices must compile,
pass meaningful regressions and client tests, receive diff review, and be committed.

The broader project acceptance target still includes full iron armour from a real
fresh spawn, building/farming/Nether goals, survival and durable checkpoints. Those
features are not claimed by the current diamond-progression release. Benchmark A–E
objectives should report completion/time, path time, replans, stuck events, excess
travel/resources and deaths on pinned worlds once the relevant scenarios exist.
