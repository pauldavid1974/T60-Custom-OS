# Development roadmap — safety-gated

No deadlines or hardware compatibility claims are implied.

| Stage | Deliverable | Exit gate |
|---|---|---|
| 0. Evidence baseline **(current)** | Recorded device read, vendor protocol sources, open questions, searchable graph | Findings are source-linked; uncertainties explicit |
| 1. Hardware identification | Exact SoC, board revision, display, input, power and memory map | Independent corroboration, not just matching Bluetooth UUIDs |
| 2. Existing OS evaluation (D-004) | Survey of existing watch OS/firmware options that could run on the identified hardware, with compatibility, licensing and access notes | At least one candidate documented as suitable for customization, or alternatives documented as inadequate — then commit to the build-own path |
| 3. Recovery feasibility | Genuine restorable factory image and validated recovery route, signature/lock analysis | Owner accepts documented risk; no flashing before this |
| 4. Portable firmware foundation | Host-tested UI state and basic service interfaces | Tests pass on host, no device access |
| 5. Board support | Correct target toolchain, HAL/BSP, linker and drivers | Target and peripherals verified; build artifacts checked |
| 6. Controlled bring-up | Hardware I/O tests on agreed safe equipment | Owner approves exact steps with rollback plan |
| 7. First usable watch OS | Time, input/navigation, display, power handling | Hardware tests and acceptance criteria met |
| 8. Additional capabilities | Bluetooth, notifications, settings, optional sensors | Each feature tested with documented power/safety behavior |

The stages are decision gates, not permission to progress automatically. Host-only source code may be developed for Stage 4 while Stages 1–3 are still open, but nothing should be flashed.

## Companion-app track (runs alongside Stages 1–3)

| Deliverable | Exit gate |
|---|---|
| Reverse engineer the installed GloryFit app and compare its protocol implementation with observed T60 GATT services and readable values | Protocol map is tied to exact APK/version and observed evidence; unsupported fields and behaviors remain marked unknown |
| Specify a replacement companion app that works with the selected customized watch software | Owner-approved requirements, supported phone platforms, and a test plan; no dependence on guessed or write-only behavior |

Replacing only the watch software or only the phone app is not the complete product goal. Implementations and any deployment remain subject to the recovery and device-write safety gates.

## Current priority

Resolve `BB2` / IC ID `0B` and compare Realtek protocol documentation to evidence from this exact T60. This is a prerequisite for both the existing-OS evaluation (Stage 2) and the build-own path. See `HANDOFF.md` for the single active assignment.
