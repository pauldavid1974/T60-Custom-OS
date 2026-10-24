# Working effectively with Qwen3.8-27B (Engine Room / T60 project)

**Research date:** 2026-10-24  
**Target model ID:** `Qwen/Qwen3.8-27B`  
**Purpose:** Operating guide for ChatGPT when delegating work to Qwen through Engine Room (RunPod or Colab) and an authorized local coding harness. This is a **project execution guide**, not firmware code and not permission to modify the watch.

## 1. Identify the model precisely

**Vendor-confirmed:** The official `Qwen/Qwen3.8-27B` checkpoint is a **post-trained** 27B dense, multimodal model, despite informal references to it as the “base model.” This is not the same as an untrained/instructionless raw pretraining checkpoint. It can follow chat instructions and participate in tool-based agent workflows without additional fine-tuning. The official Hugging Face model page names the model and its format.

**Operational check:** Before tuning an agent, ask Engine Room / the local serving endpoint for its actual model ID, runtime, precision/quantization, context limit, and exposed OpenAI-compatible API capabilities. Do not assume the advertised alias actually maps to the official checkpoint. “Qwen3.8” is **not** “Qwen3.5”; do not copy configuration blindly from older Qwen variants.

Source: https://huggingface.co/Qwen/Qwen3.8-27B

## 2. Which mode and settings to use

From the Qwen3.8-27B model card (subject to runtime support):

| Assignment | Suggested reasoning | Serving/sampling guidance |
|---|---|---|
| Unknown-chip protocol analysis, firmware integrity/security, difficult debugging | `xhigh` initially | Thinking mode; validate every claim with code/primary evidence |
| Normal coding + test/debug loop | `medium` first; raise to `xhigh` when stuck | Thinking mode; bounded changes, tests and evidence |
| Straightforward file edits, indexing docs, summaries | `low` or non-thinking | Keep exact input/output contract; verify diff |
| Any live-watch interaction | **Require Paul's per-action approval regardless of model mode** | No device writes, OTA/DFU, erase, reset, unlock, or flash |

**Official defaults/features:** `reasoning_effort` supports `xhigh` (default), `medium` and `low`. Thinking is enabled by default. Official recommended sampling:
- **Thinking:** `temperature=1.0`, `top_p=0.95`, `top_k=20`, `min_p=0`, `presence_penalty=0`, `repetition_penalty=1.0`.
- **Non-thinking:** `temperature=0.7`, `top_p=0.80`, `top_k=20`, `min_p=0`, `presence_penalty=1.5`, `repetition_penalty=1.0`.

Only set knobs the *installed runtime* actually supports; measure rather than assuming a parameter took effect. To disable thinking with compatible self-hosted serving, Qwen documents `chat_template_kwargs: {"enable_thinking": false}` (not a magic `/nothink` suffix). Qwen3.8 also documents `preserve_thinking` to retain previous reasoning when supported. The vendor cautions that lowering reasoning effort can increase total time if it creates extra failed attempts. **Project policy:** experiment on ordinary host-only tasks before changing an ongoing coding session's reasoning behavior.

Sources:
- https://huggingface.co/Qwen/Qwen3.8-27B
- https://github.com/QwenLM/Qwen3.8

## 3. Tool calling and Engine Room

**Critical distinction:** Engine Room hosts and exposes the LLM; the **local harness** (e.g., OpenCode) is responsible for reading files, running commands, Git operations, and tool approvals. Qwen cannot act on Windows merely because a GPU endpoint exists.

For a self-hosted **vLLM** endpoint serving Qwen3.8, the documented parser flags are:
- `--reasoning-parser qwen3`
- `--enable-auto-tool-choice`
- `--tool-call-parser qwen3_coder`

Do **not** restart or reconfigure a working Engine Room runtime merely to apply these flags; inspect its current provider/adapter first. Other servers have different configuration requirements.

If using **OpenCode** against an OpenAI-compatible endpoint, explicitly configure the actual `baseURL`, model identifier, input/output limits, and tool support. In particular, its vLLM discovery may not infer tool support automatically. Use environment references for credentials; never commit tokens or Tailscale endpoints to Git. Test endpoint connectivity privately before assigning coding work.

**Smoke test the complete chain** before expensive assignments:
1. Model reports its exact ID and the harness can read `README.md` and `AGENTS.md`.
2. Model searches `knowledge/graph.json` or runs `python tools/kg.py search BB2`.
3. Model reads a source file and proposes a harmless **host-only** change.
4. Harness successfully invokes an authorized tool and returns its stdout/stderr to the model.
5. For a permitted change, model verifies tests, inspects Git diff, then commits/pushes **only after functioning safety hooks**; never bypass missing or failing hooks.

**This project already encountered an agent safety-hook failure**: a `PreToolUse` PowerShell hook path was missing/timed out during `git commit`. A broken hook is an environment problem, **not** permission to bypass validation or pretend a commit succeeded. Preserve staged changes, diagnose hook configuration, and stop if it cannot be repaired.

Sources:
- https://recipes.vllm.ai/Qwen/Qwen3.8-27B
- https://opencode.ai/v2/docs/models
- https://opencode.ai/v2/docs/providers

## 4. Context and cost controls

**Vendor fact:** Qwen3.8-27B natively supports **262,144 tokens** of context and documents extension toward 1 million with YaRN. Actual available context is limited by the **deployed server settings and GPU memory**, not merely model capability.

**Project policy (recommended, not a vendor guarantee):**
- Start sessions by reading **`README.md` → `AGENTS.md` → `HANDOFF.md` → matching knowledge-graph nodes/refs**. Avoid dumping the entire repo or very long logs into a prompt.
- Supply exact file paths, source spans, command results, and a bounded objective. Keep evidence in versioned project docs and commit state rather than relying on long chat context.
- Prefer modest context/output lengths that fit observed VRAM; increase only after measuring. Never force 262K/1M simply because the model advertises them.
- Preserve the current RunPod/Colab setup unless a **measured** alternative has better total cost. A quantized model can use less VRAM but may have different reliability; validate representative tasks.
- Use `medium` for routine work and `xhigh` for difficult investigations as an experiment, tracking quality, iterations and GPU runtime. Shut down paid GPU resources when no longer needed.
- In long-horizon work, use compact evidence summaries and fresh reads of exact relevant files; never reduce safety verification to save tokens.

## 5. Best delegation format for a smaller local agent

**Project recommendation:** One cohesive task with explicit inputs, allowed actions, outputs, and stop conditions. Rather than open-ended “build the watch OS,” give Qwen a narrow, testable unit.

Reusable assignment structure:

```text
ROLE: Firmware engineer working under AGENTS.md, not autonomous project owner.
GOAL: <one concrete deliverable>
READ FIRST: README.md, AGENTS.md, HANDOFF.md;
            docs/<specific>.md and knowledge graph search <terms>.
KNOWN EVIDENCE: <verified facts and exact citations/paths>.
UNCERTAINTY: <what must not be assumed>.
IN SCOPE: <named files/commands and expected changes>.
OUT OF SCOPE: Live T60 device reads/writes without separate approval;
              flashing, OTA, reset, unpair, hardware modification.
ACCEPTANCE: <objective tests, command outputs, file diffs>.
EVIDENCE: Cite SDK code path/version or official documentation for factual claims.
REPORT: Changed files, tests actually run/pass/fail/not run, commit SHA
        (only if committed), uncertainty, one recommended next action.
STOP: If hardware assumption is unverified, hook blocks writes, command requires
      approval, or safe recovery is missing, stop and report the blocker.
```

**T60 example (currently authorized, entirely offline):**

> Verify precisely what Realtek `BB2` / IC byte `0B` means by examining the installed GloryFit SDK and official documentation. Update `docs/HARDWARE.md`, `knowledge/graph.json`, and `HANDOFF.md` only with source-backed findings. Run graph validation and relevant tests; stop before any BLE access, flash, OTA, reset or other live-watch interaction. Report uncertainty where the full SoC identity is unavailable.

## 6. Evidence, verification and Git discipline

For each assignment, ask the agent to:
1. **Inspect** current repository state and current `HANDOFF.md` before edits (`git status`, branch/remote, `git pull --ff-only` when clean/safe).
2. **Research** from primary vendor docs or exact SDK sources. Distinguish observation, documentation, inference and unknown.
3. **Edit** the minimum coherent set of code/tests/docs/graph nodes. Do not invent tests or hardware facts.
4. **Verify** with actual command output. Never report “passed” if only expected to pass. Review `git diff`.
5. **Commit** with meaningful message, push and confirm remote SHA *only if hooks and permissions succeed*.
6. **Handoff** with a 3–5 bullet result, evidence paths, blockers and one next proposed assignment.

Only Paul's explicit approval authorizes interacting with the live T60 outside previously approved reads. **A high-confidence AI answer cannot replace a verified original-firmware backup and recovery path.**

## 7. Research sources and provenance

**Official/vendor or direct tool documentation reviewed 2026-10-24:**
- Qwen3.8-27B official model card: https://huggingface.co/Qwen/Qwen3.8-27B
- Qwen model family and serving guidance: https://github.com/QwenLM/Qwen3.8
- vLLM deployment reference: https://recipes.vllm.ai/Qwen/Qwen3.8-27B
- OpenCode model/provider settings: https://opencode.ai/v2/docs/models and https://opencode.ai/v2/docs/providers
- RunPod model reference (configuration is provider-specific; don't apply without checking): https://www.runpod.io/qwen3-8-27b

**Status:** The sources above support model properties and serving-interface recommendations. The delegation template, evidence workflow, task sizing and safety gates are **project-specific recommendations**, not official Qwen guarantees. No live agent benchmarking or changed Engine Room deployment was performed for this research.
