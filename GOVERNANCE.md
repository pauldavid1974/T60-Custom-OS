# Project and Git governance

**Status:** Accepted by the repository owner, Paul (`pauldavid1974`), on 2026-10-25.

This document defines how decisions, changes, reviews, releases, and device-related approvals are managed in `pauldavid1974/T60-Custom-OS`. It applies to Paul and every authorized AI or human assistant. `AGENTS.md` supplies binding agent working rules; Paul's explicit instructions and the strictest applicable device-safety rule always take precedence.

## 1. Ownership and authority

- **Owner:** Paul (`pauldavid1974`) is the sole human project owner and final decision-maker.
- **Authorized agents:** ChatGPT, Qwen, and other assistants may research, implement, test, document, and commit within a bounded assignment. They are not independent owners or safety approvers.
- **Collaborators:** No outside collaborator, reviewer, or approval authority is required or implied.
- **Repository:** This public, one-owner repository is the source of truth for code, tests, evidence, decisions, documentation, tooling, and handoffs. Public visibility does not grant write access or permission to expose protected evidence.

An assignment, issue, pull request, CODEOWNERS entry, CI result, or agent recommendation does not expand authority or grant device-operation approval.

## 2. Sources of truth

| Concern | Authoritative location |
|---|---|
| Product purpose and current repository map | `README.md`, `docs/PRODUCT.md` |
| Active assignment and recent findings | `HANDOFF.md` |
| Agent authority and safety boundaries | `AGENTS.md` |
| Product and technical decisions | `docs/DECISIONS.md` |
| Hardware evidence and unresolved questions | `docs/HARDWARE.md` |
| Backup, recovery, and write prerequisites | `docs/RECOVERY.md` |
| Implementation | `firmware/` and other source directories |
| Searchable evidence and relationships | `knowledge/graph.json` |

Do not use an old handoff entry to override a newer decision or current owner instruction. Preserve history while clearly marking superseded guidance.

## 3. Decision classes

### A. Routine repository changes

Small, reversible documentation, research, test, tooling, and portable host-only code changes within an authorized assignment use a short-lived branch and pull request so the required `check` CI job passes before integration into protected `main`.

Requirements:

1. Synchronize safely and preserve unrelated work.
2. Keep commits focused and inspect the complete diff.
3. Run applicable checks and report actual results.
4. Update relevant documentation, graph entries, and `HANDOFF.md` together.

### B. Product, architecture, or governance decisions

A change to project goals, supported platforms, OS base, safety policy, ownership, release policy, or other durable direction requires Paul's decision. Record accepted decisions in `docs/DECISIONS.md` and link them from `knowledge/graph.json`.

A larger or risky repository change may use a feature branch and pull request for review. Pull requests are a coordination tool, not a second approval authority.

### C. Live-device operations

Every new operation against a live watch or phone requires Paul's explicit approval of the **exact action**, unless that precise operation is already covered by a recorded, still-current authorization. This includes BLE reads not previously approved.

Device writes, DFU/OTA, flashing, firmware downgrade, reset, unpairing, unlock, debug-port access, disassembly, and any potentially state-changing operation always require separate, action-specific approval. A general research assignment is not sufficient.

### D. Firmware installation or other high-risk changes

No installation or device-writing work may proceed until all of the following are established:

1. Exact SoC/board revision and firmware compatibility.
2. Verified, restorable factory-firmware backup, including relevant calibration or vendor data.
3. Proven bootloader/update signature requirements and a testable recovery method.
4. An owner-approved execution plan with commands, expected results, rollback, and stop conditions.

Host-only builds and tests never satisfy these gates.

## 4. Git workflow

### Default branch and branches

- `main` is the protected integration branch and shared agent work desk.
- Use a short-lived branch and pull request for routine and substantial changes so required CI can pass before integration.
- Protection requires the GitHub Actions `check` context and up-to-date branches, applies to administrators, and disables force-pushes and branch deletion. No pull-request reviews are required.
- Do not rewrite published history, force-push, delete branches, change visibility, or alter repository settings without Paul's explicit instruction.
- Do not add branch protection that demands outside reviews or changes these settings without Paul's approval.

### Commits

- Use a short, imperative subject that identifies the outcome.
- Commit related source, tests, documentation, graph changes, and handoff updates together.
- Reference an issue or decision ID when it adds traceability.
- Never bypass a failing hook. Preserve work, diagnose the blocker, and repair the hook through an authorized change.
- Never commit credentials, tokens, private device dumps, proprietary APKs, original firmware images, commercial SDK binaries, or unlicensed source.

### Pull requests

Pull requests are the normal integration path while `main` requires CI. No outside review is required.

- Complete `.github/PULL_REQUEST_TEMPLATE.md` with evidence, safety scope, tests, and rollback notes.
- CODEOWNERS identifies Paul as owner; it does not require an external review.
- Resolve relevant failing checks before merge. Do not represent an unrun or failed check as passing.
- Paul decides whether and how to merge; authorized agents may merge only when their assignment explicitly permits it.

## 5. Evidence and review standard

Use these labels consistently:

- **Observed:** Direct read, screenshot, log, test output, or verified artifact, with date and source.
- **Documented:** Original manufacturer documentation or source code, with an exact reference and its actual scope.
- **Inferred:** A plausible interpretation whose uncertainty is explicit.
- **Unknown:** Unresolved and not a basis for target compatibility or device writes.
- **Planned:** Intended future work, not an existing capability.

Review claims against primary evidence. Matching UUIDs, product names, SDK constants, or code-like byte patterns do not prove exact hardware or binary compatibility. Never claim a test passed unless its output was observed.

## 6. Verification

For knowledge-graph or project-file changes, run:

```sh
python tools/kg.py validate
python -m unittest discover -s tests -v
```

For portable C changes, also run where the host toolchain is available:

```sh
cmake -S firmware -B build/host
cmake --build build/host
ctest --test-dir build/host --output-on-failure
```

GitHub Actions runs these checks on pushes to `main` and pull requests. Record unavailable checks as **not run**, with the reason. Host success proves only host behavior, never watch compatibility.

## 7. Issues and approval records

Issue templates support three distinct records:

- **Bug report:** reproducible repository or host-only software defects.
- **Research or engineering task:** bounded objectives, files, tools, acceptance checks, and stop conditions.
- **Device-action request:** exact proposed action, evidence sought, risks, prerequisites, and approval status.

A device-action issue is a request or record only. Approval must be an explicit statement from Paul identifying the action; ticking a template box, creating an issue, or assigning an agent is not approval.

## 8. Security, privacy, and licensing

- The repository is public by Paul's explicit 2026-10-25 decision (D-007); do not change its visibility without new explicit owner approval.
- Report sensitive findings through Paul's existing private channel, not a public issue or pull request.
- Store proprietary or private evidence only in approved local locations and document non-sensitive provenance in the repository.
- Verify license compatibility before importing third-party source, firmware, SDKs, fonts, images, or other assets.
- Follow `SECURITY.md` for disclosure and safe investigation.

## 9. Releases and artifacts

The project currently has no installable watch release. Do not tag or describe host-only output as device firmware.

A future target release requires Paul's approval, an exact hardware target, reproducible build instructions, tested artifacts with hashes, license notices, verified backup/recovery instructions, and an explicit rollback/stop plan. Release approval does not itself authorize installation; installation remains a separate exact action.

## 10. Changing governance

Paul may amend these rules. Governance changes must update this file, relevant agent instructions, `docs/DECISIONS.md`, and applicable templates together. The change should identify any superseded policy rather than leaving conflicting instructions active.
