# T60 Custom OS

Research and development workspace for replacing the factory firmware on **Paul's T60 smartwatch** with a custom watch operating system.

**Status:** Research only. No custom firmware has been installed. The exact processor, a verified backup of the factory firmware, and a dependable recovery method remain unknown.

## Start here

1. Read [AGENTS.md](AGENTS.md) for working rules and safety boundaries.
2. Read [HANDOFF.md](HANDOFF.md) for established evidence, open questions, and the **single current assignment**.
3. Perform only that assignment, and update `HANDOFF.md` with findings, supporting evidence, and the next proposed action.

## Working arrangement

- **Human owner:** Paul (`pauldavid1974`); this is his personal, private repository.
- **ChatGPT:** research coordinator, technical planning, evidence review, and handoff authoring.
- **Qwen:** technical investigation/code generation, reached through Engine Room (RunPod or Colab) and a local agent harness.
- **Agent access:** through the owner's existing authorized GitHub setup; no invitation of outside reviewers or collaborators is needed.
- **Review workflow:** single-owner, no required external review, no mandated PR reviewer; the owner approves device-impacting operations. Documentation may be committed directly to `main`.

**Never experiment on the physical watch by flashing firmware, modifying BLE values, resetting, or accessing hardware debug pads without Paul's explicit approval of a specific, documented procedure.** Research, source analysis, and read-only diagnostics are the default.

The goal is **replacement firmware running on the watch**, not merely a different watch face or replacement Android companion app.
