# T60 Custom OS

**A complete, private source repository for developing a replacement operating system for Paul's T60 smartwatch.** This contains real firmware-core source code, tests, research, technical documentation, and a searchable evidence/dependency knowledge graph—not just AI messages.

> **Device status:** Exploration only. Nothing has been flashed. The exact SoC, target toolchain, genuine factory-firmware backup and safe recovery method are still unknown. No code in this repo is currently installable on the watch.

## Repository map

| Path | Purpose |
|---|---|
| [firmware/](firmware/) | Actual portable C firmware-core source and host-only unit tests; board-specific hardware support will follow once the target is proven |
| [docs/PRODUCT.md](docs/PRODUCT.md) | Product direction, design goals and acceptance gates |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Planned firmware layers and boundaries between core logic and hardware |
| [docs/ROADMAP.md](docs/ROADMAP.md) | From processor identification to safe bring-up and usable watch OS |
| [docs/HARDWARE.md](docs/HARDWARE.md) | Verified device evidence and unresolved hardware details |
| [docs/RECOVERY.md](docs/RECOVERY.md) | Mandatory backup, recovery, signing and safety requirements |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Technical/project decisions and pending choices |
| [knowledge/graph.json](knowledge/graph.json) | **Searchable project knowledge graph:** evidence, decisions, unknowns, dependencies, code components |
| [tools/kg.py](tools/kg.py) | Search, trace relationships, inspect nodes, validate graph integrity |
| [tests/](tests/) | Knowledge graph/search tests; more project-level tests go here |
| [docs/QWEN38_AGENT_GUIDE.md](docs/QWEN38_AGENT_GUIDE.md) | Research-backed Qwen3.8-27B prompting, reasoning/tool-calling settings, cost controls and handoff protocol |
| [AGENTS.md](AGENTS.md) | One-owner project rules for ChatGPT, Qwen and other authorized agents |
| [HANDOFF.md](HANDOFF.md) | Current assignment and recent verified findings—not the entire project's source of truth |

## Working with the project

From a local checkout, use Python 3 (standard library) for graph search:

```sh
python tools/kg.py search BB2
python tools/kg.py related fact-bb2
python tools/kg.py list --kind question
python tools/kg.py validate
python3 -m unittest discover -s tests -v
```

For the **portable host-only firmware component**, install CMake and a C compiler:

```sh
cmake -S firmware -B build/host
cmake --build build/host
ctest --test-dir build/host --output-on-failure
```

**These commands never connect to the watch.** The repository deliberately contains no flash/OTA command or usable device firmware image.

## Collaboration and safety

- **Owner:** Paul (`pauldavid1974`), personal account and private repository; no outside collaborators or mandatory outside reviews.
- **ChatGPT:** research coordination, architecture, evidence and code review, task planning.
- **Qwen through Engine Room (RunPod/Colab):** code generation and research using a user-approved local harness.
- **Single source of truth:** actual source in `firmware/`, documented decisions/evidence in `docs/` and `knowledge/`, next agent work in `HANDOFF.md`. Agents commit source code, tests and documentation to this repo.

**Owner approval is mandatory** before any new live watch operation. Read-only research is the default, and no firmware writes until the exact chip, original backup, recovery path and image verification are established. Full rules: [AGENTS.md](AGENTS.md).

## Current technical lead

Work read BLE `D0FF/FFF1`: `0B 01 1D 00 51 01 00 F7 00 00 00 00 00`. GloryFit's bundled Realtek SDK reportedly maps `0B` to `BB2`. This is a chipset-family clue, **not** an exact chip model. Research task and sources: [HANDOFF.md](HANDOFF.md).
