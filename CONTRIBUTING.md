# Contributing to T60 Custom OS

This is Paul's private, one-owner project. Contributions are accepted only from Paul and assistants he authorizes; no outside review or collaboration is required.

Read `GOVERNANCE.md` for decision authority and `AGENTS.md` for binding agent and device-safety rules. This guide covers the normal repository workflow.

## Start a task

1. Read `README.md`, `AGENTS.md`, and the active assignment in `HANDOFF.md`.
2. Read the relevant product, architecture, hardware, recovery, and decision documents.
3. Search the knowledge graph before repeating research:

   ```sh
   python tools/kg.py search <term>
   python tools/kg.py related <node-id>
   ```

4. Synchronize `main` safely and inspect local changes. Preserve unrelated work.
5. Keep the task bounded. If it crosses an authority, safety, licensing, or tooling boundary, stop and ask Paul.

## Choose a workflow

- **Routine, reversible change:** commit directly to `main` under an existing authorization.
- **Large, experimental, or review-heavy change:** use a feature branch and pull request if useful or requested.
- **New product or technical direction:** obtain Paul's decision and record it in `docs/DECISIONS.md` and the knowledge graph.
- **Live-device operation:** follow the exact-action approval rules in `GOVERNANCE.md`; repository work never implies device permission.

Use the issue templates for bug reports, bounded research/engineering assignments, and device-action requests. Blank issues remain available for owner notes.

## Make a change

- Keep commits small and coherent.
- Put portable firmware source under `firmware/`; do not add board support, flashing, or recovery code before its prerequisites and approval exist.
- Label claims **Observed**, **Documented**, **Inferred**, **Unknown**, or **Planned** as appropriate.
- Update durable knowledge in `docs/` and `knowledge/graph.json`; keep the current assignment and recent findings in `HANDOFF.md`.
- Do not commit secrets, private device dumps, APKs, original firmware images, proprietary SDK binaries, or unlicensed material.
- Review the diff and never bypass a failing hook.

## Validate

Run applicable checks from the repository root:

```sh
python tools/kg.py validate
python -m unittest discover -s tests -v
```

For portable C changes, also run when CMake and a C compiler are available:

```sh
cmake -S firmware -B build/host
cmake --build build/host
ctest --test-dir build/host --output-on-failure
```

Report passed, failed, or not run accurately. Host-only artifacts are not installable watch firmware.

## Finish a task

1. Confirm documentation, graph entries, tests, and `HANDOFF.md` agree with the implementation and evidence.
2. Record unresolved questions and one specific next assignment in `HANDOFF.md` without overwriting another active assignment.
3. Commit related changes together with a clear imperative subject.
4. For a pull request, complete the provided template, including safety scope, actual validation, and rollback notes.
5. Report changed files, checks actually run, commit SHA, and any required owner decision or approval.

A Qwen or other agent commit is a report for Paul to review, not an independent approval or completed safety review.
