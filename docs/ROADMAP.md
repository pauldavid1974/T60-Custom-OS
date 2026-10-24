# Development roadmap — safety-gated

No deadlines or hardware compatibility claims are implied.

| Stage | Deliverable | Exit gate |
|---|---|---|
| 0. Evidence baseline **(current)** | Recorded device read, vendor protocol sources, open questions, searchable graph | Findings are source-linked; uncertainties explicit |
| 1. Hardware identification | Exact SoC, board revision, display, input, power and memory map | Independent corroboration, not just matching Bluetooth UUIDs |
| 2. Recovery feasibility | Genuine restorable factory image and validated recovery route, signature/lock analysis | Owner accepts documented risk; no flashing before this |
| 3. Portable firmware foundation | Host-tested UI state and basic service interfaces | Tests pass on host, no device access |
| 4. Board support | Correct target toolchain, HAL/BSP, linker and drivers | Target and peripherals verified; build artifacts checked |
| 5. Controlled bring-up | Hardware I/O tests on agreed safe equipment | Owner approves exact steps with rollback plan |
| 6. First usable watch OS | Time, input/navigation, display, power handling | Hardware tests and acceptance criteria met |
| 7. Additional capabilities | Bluetooth, notifications, settings, optional sensors | Each feature tested with documented power/safety behavior |

The stages are decision gates, not permission to progress automatically. Source code may be developed for Stage 3 while Stages 1–2 are still open, but nothing should be flashed.

## Current priority

Resolve `BB2` / IC ID `0B` and compare Realtek protocol documentation to evidence from this exact T60. See `HANDOFF.md` for the single active assignment.
