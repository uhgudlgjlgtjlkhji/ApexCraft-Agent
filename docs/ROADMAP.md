# Roadmap and evidence

The production target changed to a Fabric 26.3 client mod. The Python phases below
are reference/simulation slices, not completed Minecraft features.

| Phase | Python reference | Java/Fabric target |
| --- | --- | --- |
| 1 Foundation | Tested | Core compiled/tested; client compiled; lifecycle unvalidated in-game |
| 2 Navigation | Flat-grid detours/recovery tested | Core tested; walking adapter compiled; in-game tests pending |
| 3 Interaction | Finite-world mining/place/collect tested | Collect core tested; mining adapter compiled; in-game tests pending |
| 4 Inventory/crafting | Ledger/recipes/equipment tested | Dependency resolver tested; live containers pending |
| 5 Acquisition | Incremental finite sources tested | Nearby collect core tested; game integration pending |
| 6 Autonomous planning | Stone/iron/diamond/armour goals tested | Not integrated |
| 7 Survival | Food/escape/resume tested | Conservative pause compiled; unvalidated in-game |
| 8 Memory | Atomic world-scoped checkpoint resume tested | Not integrated |
| 9 Advanced goals | Declarative shelter and repair tested | Building, farming, Nether all pending |
| 10 Optimization | No measured optimization committed | Pending live profiling |

**Build status:** GitHub Actions successfully compiles the Fabric mod and uploads
a development JAR. Local cloud dependency downloads are still denied by network
policy. Next: in-game walking/collection/cancellation tests, then live crafting and
planner integration. Compilation does not complete the broader autonomous-agent goal.

Each further slice must compile, run meaningful tests, handle failure and
cancellation, receive diff review, and be committed independently. Existing
simulation tests remain regression references while porting to Java.

Final acceptance remains a real Minecraft fresh spawn → full iron armour equipped,
with verified acquisition, crafting, smelting, recovery and survival. Benchmark
A–E (stone pickaxe, iron pickaxe, full armour, diamond, safe shelter) must report
completion rate/time, path time, replans, stuck events, excess travel/resources,
and deaths on pinned worlds and versions. No live performance claims exist yet.
