# Project knowledge graph

This is the machine-readable map of the **entire T60 operating-system project**. It makes hardware facts, firmware interfaces, source code, design decisions, open questions, and research evidence searchable without reading every document.

## Quick searches

From the repository root, using Python 3 with **no dependencies**:

```sh
python tools/kg.py search BB2
python tools/kg.py search firmware recovery
python tools/kg.py related fact-bb2
python tools/kg.py show question-soc
python tools/kg.py list --kind question
python tools/kg.py --json search bluetooth
python tools/kg.py validate
```

## Data model

- `knowledge/graph.json` includes **nodes** with a stable ID, type, certainty, title, summary, tags, and links to sources or repository paths.
- **Edges** identify named relationships between nodes, including `blocked-by`, `observed-in`, `requires`, `does-not-resolve`, and source links.
- Certainty is marked **observed**, **documented**, **inferred**, **unknown**, or **planned**. A finding's label is not permission to flash.
- Research notes and full procedures remain in `docs/` and `HANDOFF.md`. The graph is an index/traceability layer, not a duplicate knowledge dump.

## Maintaining the graph

Whenever a new feature, component, blocker, source, or technical decision is added, update its node and relevant relationships in `graph.json`. Add a source link to original evidence. Do **not** silently turn an inference into a confirmed hardware fact.

When adding a code file or document, consider an appropriate component/decision node and cite the actual file path. Keep IDs stable so links survive edits and renames.

Run `python tools/kg.py validate` and `python -m unittest discover -s tests -v` before committing. The repository's GitHub Actions workflow also runs these checks. The validator verifies structure, local file paths and node IDs; it does **not** verify factual truth or fetch remote URLs.

## Search workflow for ChatGPT / Qwen

1. Read README.md and AGENTS.md, then HANDOFF.md.
2. Search graph for the current subject, e.g. `python tools/kg.py search bootloader`.
3. Follow the returned `refs` to read source details and neighboring questions.
4. Update the graph with sourced findings and the handoff as needed; commit together with project changes.

Anyone without a checked-out copy can inspect `knowledge/graph.json` on GitHub; with local access, Qwen can run the search script.
