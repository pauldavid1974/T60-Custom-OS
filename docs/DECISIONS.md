# Decision record

## D-001: One-owner personal repository
- **Decision:** `pauldavid1974/T60-Custom-OS` is the private project source of truth, not merely an agent message board.
- **Reason:** All code, tests, documentation, evidence, and handoffs should remain searchable and versioned together.
- **Governance:** Paul is sole human project owner; no outside collaborators or required external reviewer.
- **Status:** Accepted (2026-10-24).

## D-002: Read-only research until proven recovery
- **Decision:** No watch-write operation, flashing, destructive debugging, or hardware modification without explicit approval and proven recovery path.
- **Status:** Accepted (2026-10-24).

## D-003: Evidence-backed knowledge graph
- **Decision:** Keep `knowledge/graph.json` alongside the code. Nodes must distinguish observed, documented, inferred and unknown information; references and relationships must remain searchable.
- **Status:** Accepted (2026-10-24).

## D-004: Evaluate existing watch operating systems before building our own
- **Decision:** Investigate existing compatible watch operating systems first; customize one if suitable; develop our own only if the alternatives are inadequate.
- **Reason:** Building a complete OS from scratch is the highest-cost path. A compatible existing base (vendor SDK application, open-source watch firmware for the same chip family) may reduce risk and effort.
- **Constraints:** Compatibility can only be assessed once the exact SoC is identified (`question-soc`), and any modification path still requires the proven backup/recovery gate (D-002). This decision changes *which* build path to prefer, not the safety gates.
- **Governance:** Paul (owner) decision, stated 2026-10-24.
- **Status:** Accepted (2026-10-24).

## D-005: Replace both watch software and companion app
- **Decision:** The long-term goal includes replacing the T60's factory watch operating software and the GloryFit companion app. Replacing only one is incomplete.
- **Reason:** The customized watch and its phone companion must work together without depending on the factory software stack.
- **Constraints:** Reverse engineer and document the existing watch/app protocols before choosing an implementation. D-002's recovery and device-write gates remain in force; this decision is not approval to install either replacement.
- **Governance:** Paul (owner) decision, stated 2026-10-24.
- **Status:** Accepted (2026-10-24).

## Pending decisions
- Whether an existing watch OS base is suitable for the T60 (D-004 evaluation output).
- Companion app requirements and supported platforms for the replacement.
- Exact processor target and SDK/toolchain.
- Native firmware framework or RTOS.
- Screen and input drivers.
- Recovery/debug hardware and safe bring-up protocol.

Record new decisions here and link each one in the knowledge graph.
