Third-Party Notice

This repository contains original code and assets by Adonis. This notice records a completed provenance cleanup related to trap-bearing fishing gameplay.

Reference project under review:
- Project: Create: Integrated Farming
- Upstream author: DragonsPlus
- Observed upstream license: LGPL-3.0-or-later
- Local reference path used during review: `D:\Geography\Dev\111Reference\CreateIntegratedFarming-main`

Status of this notice:
- This file is a historical cleanup record.
- It does not assert that the files below are derived from upstream code.
- It records which files were reviewed or rewritten during the cleanup process.

Cleanup record

Clean-room rewritten during cleanup
- `src/main/java/com/adonis/createfisheryindustry/block/TrapBearing/TrapBearingBlockEntity.java`
- `src/main/java/com/adonis/createfisheryindustry/block/TrapBearing/TrapBearingFakePlayer.java`
- `src/main/java/com/adonis/createfisheryindustry/block/TrapBearing/TrapBearingBlock.java`

Reviewed and kept as-is
- `src/main/java/com/adonis/createfisheryindustry/block/FrameTrap/FrameTrapMovementBehaviour.java`
- `src/main/resources/assets/createfisheryindustry/models/block/frame_trap.json`
- `src/main/resources/assets/createfisheryindustry/models/block/mesh_trap.json`
- `src/main/resources/assets/createfisheryindustry/models/block/smart_mesh.json`
- `src/main/resources/assets/createfisheryindustry/models/block/trap_bearing.json`

Why these files are listed
- `TrapBearingBlockEntity.java`, `TrapBearingFakePlayer.java`, and `TrapBearingBlock.java` were independently rewritten during cleanup.
- The remaining files are listed conservatively because they belong to the same gameplay surface, but current review suggests they are less likely to be direct copies.

Outcome
- The trap-bearing Java implementation was replaced with independently authored code.
- The remaining listed files were reviewed and retained.
- Functional testing after rewrite was reported as passing.

Repository licensing note
- The repository-level MIT notice applies to project code except where a third-party license is explicitly preserved for a specific file or dependency.
- This notice is retained as a record of the cleanup, not as an indication of an unresolved license issue.
