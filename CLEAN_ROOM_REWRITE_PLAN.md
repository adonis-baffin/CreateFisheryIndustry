Clean-Room Rewrite Plan

Goal

- Remove license ambiguity from the trap-bearing fishing implementation.
- Replace the highest-risk gameplay files with independently authored code and keep a written record of that process.

Scope

Completed clean-room rewrites
- `src/main/java/com/adonis/createfisheryindustry/block/TrapBearing/TrapBearingFakePlayer.java`
- `src/main/java/com/adonis/createfisheryindustry/block/TrapBearing/TrapBearingBlock.java`
- `src/main/java/com/adonis/createfisheryindustry/block/TrapBearing/TrapBearingBlockEntity.java`

Validation result
- Runtime testing after rewrite was reported as passing.
- Remaining formal build verification depends on local Java 21 toolchain availability.

Rewrite rules

- Do not look at upstream code while rewriting the target file.
- Base the rewrite only on project requirements, Minecraft or NeoForge APIs, and current in-project interfaces.
- Preserve external behavior only where the behavior is product-defined, not where the exact code structure came from a reference implementation.
- Keep commit messages and review notes explicit that the rewrite is intended as a clean-room replacement.

Functional spec used for the `TrapBearingBlockEntity.java` rewrite

- Acts as a Create bearing variant with internal item storage.
- Scans attached `FrameTrapBlock` blocks on the moving contraption.
- Counts valid trap blocks in water or lava and derives a fishing level from count thresholds.
- Uses a local cooldown-based loot cycle.
- Uses vanilla fishing loot for water and optional Nether Depths Upgrade loot for lava.
- Stores generated loot internally and exposes it for automation.
- Shows state in goggle tooltip.

Validation checklist

1. Build with Java 21 toolchain available.
2. Assemble a trap bearing with frame traps and confirm level progression from 1 to 8.
3. Confirm loot enters internal storage and can be extracted by automation.
4. Confirm water mode uses vanilla fishing loot and lava mode falls back safely when NDU tables are absent.
5. Validation completed at functional level; remaining work is ordinary project maintenance.
