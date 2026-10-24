# Agent rules — T60 Custom OS

## Authority and scope

This is a **one-human-owner** personal project. Paul (`pauldavid1974`) makes final decisions. ChatGPT and Qwen are helpers, not independent owners, external reviewers, or separate approval authorities.

Read **README.md**, then **HANDOFF.md**, before beginning. Treat the `HANDOFF.md` **Next assignment** as the active task unless Paul explicitly directs a different project change. Do not silently expand scope. Evidence from actual device reads and original documents outranks hypotheses or generic claims about other T60 models.

## Safety boundaries

**Allowed without separate approval**
- Read repository files, inspect public manufacturer documents, analyze downloaded APKs and firmware copies offline, write research notes or code that is not run on the watch.
- Prepare read-only diagnostics *for Paul to approve*, including expected results and risk assessment.

**Requires Paul's explicit approval of the exact action**
- Any new operation against the live watch not already explicitly approved (even a BLE read, if an agent has not been given permission for that characteristic).
- Writing BLE characteristics, entering DFU/OTA mode, flashing/upgrading/downgrading firmware, factory reset/unpairing, unlocking, debug-port connections, disassembly, or any operation that might alter the device.

**Do not attempt installation until ALL are established**
1. Exact SoC/board revision and firmware compatibility.
2. Verified, restorable factory-firmware backup (not unrelated firmware bundled with the phone app).
3. Proven bootloader/update signature requirements and a testable recovery method.
4. Owner-approved flash plan, including rollback and stop conditions.

Never assume a GATT UUID or chipset family proves binary compatibility. If unsure, stop and report the uncertainty. Never claim a test passed unless its actual output is available. Keep secrets and device credentials out of commits; do not embed tokens, APKs of uncertain licensing, or private device dumps in GitHub.

## Full-project repository workflow

- This repository stores the **entire watch OS project**: code, tests, hardware evidence, decisions, documentation, tooling, and AI handoffs.
- Read `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, and `docs/HARDWARE.md` when investigating the firmware implementation.
- Use `knowledge/graph.json` as the **searchable index** linking evidence, components, blockers and source files; see `knowledge/README.md`. Search with `python tools/kg.py search <term>` and `python tools/kg.py related <node-id>`.
- Keep graph IDs stable. Update the graph, documentation and tests alongside relevant changes. Never convert an assumption into an observed fact without evidence.
- Source code belongs under `firmware/` rather than inside HANDOFF.md. Board support, target flashing and recovery code may not be added until the chip and recovery paths are substantiated and explicitly approved.
- Portable host-only C code can be built and tested with CMake, but must never be represented as deployable watch firmware.
- For changes to portable code, run host tests; for graph changes, run `python tools/kg.py validate` and `python -m unittest discover -s tests -v`. Record tests honestly: not run, passed, or failed.
- Do not commit proprietary APKs, original watch firmware images, personal device dumps, credentials or commercial SDK binaries; document secure local evidence locations where appropriate.

## One-owner Git workflow

- Repo: `pauldavid1974/T60-Custom-OS` (private; personal account; no outside collaborators/reviews requested).
- Routine docs and non-device changes can go directly to `main`, under Paul's authorization; do not require outside reviews or force PR creation.
- Commit clear, small changes. Do not merge, add collaborators, request reviewers, or configure branch protections that demand outside reviews.
- Avoid duplicate notes and stale handoffs. Put the live assignment and its findings in **HANDOFF.md**. Keep permanent technical knowledge in appropriate docs and the graph; place implementation in source files.
- Before leaving a session, update **HANDOFF.md**: result, source/link/log, confidence, unresolved questions, and one specific next assignment. Commit actual code, docs, and knowledge graph changes together when relevant.
- When exchanging prompts manually, give Paul a short copyable handoff pointing the next agent to the repository rather than repeating the whole history.

## Evidence labels

- **Observed:** direct screenshot/read/log or verified artifact, with date and source.
- **Documented:** manufacturer's original documentation or source code with link/path, and what exactly it establishes.
- **Inferred:** a plausible interpretation, explicitly uncertain.
- **Unknown:** unresolved and not a basis for flashing.
