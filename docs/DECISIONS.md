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

## Pending decisions
- Exact processor target and SDK/toolchain.
- Native firmware framework or RTOS.
- Screen and input drivers.
- Recovery/debug hardware and safe bring-up protocol.

Record new decisions here and link each one in the knowledge graph.
