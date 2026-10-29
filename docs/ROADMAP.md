# Roadmap and evidence

The production target changed to a Fabric 26.3 client mod. The Python phases below
are reference/simulation slices, not completed Minecraft features.

| Phase | Python reference | Java/Fabric target |
| --- | --- | --- |
| 1 Foundation | Tested | Core compiled/tested; client lifecycle unvalidated |
| 2 Navigation | Flat-grid detours/recovery tested | Core tested; walking adapter uncompiled |
| 3 Interaction | Finite-world mining/place/collect tested | Collect core tested; mining adapter uncompiled |
| 4 Inventory/crafting | Ledger/recipes/equipment tested | Dependency resolver tested; live containers pending |
| 5 Acquisition | Incremental finite sources tested | Nearby collect core tested; game integration pending |
| 6 Autonomous planning | Stone/iron/diamond/armour goals tested | Not integrated |
| 7 Survival | Food/escape/resume tested | Conservative pause source only, unvalidated |
| 8 Memory | Atomic world-scoped checkpoint resume tested | Not integrated |
| 9 Advanced goals | Declarative shelter and repair tested | Building, farming, Nether all pending |
| 10 Optimization | No measured optimization committed | Pending live profiling |

**Immediate blocker:** Fabric Maven/Mojang downloads denied by cloud network policy.
Necessary domain additions are saved in the environment draft. The next useful
step after settings are applied is full `./gradlew build`, then real client tests.
Do not call the Fabric mod complete before those checks pass.

Each further slice must compile, run meaningful tests, handle failure and
cancellation, receive diff review, and be committed independently. Existing
simulation tests remain regression references while porting to Java.

Final acceptance remains a real Minecraft fresh spawn → full iron armour equipped,
with verified acquisition, crafting, smelting, recovery and survival. Benchmark
A–E (stone pickaxe, iron pickaxe, full armour, diamond, safe shelter) must report
completion rate/time, path time, replans, stuck events, excess travel/resources,
and deaths on pinned worlds and versions. No live performance claims exist yet.
