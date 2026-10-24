#!/usr/bin/env python3
"""Search and validate T60 OS's version-controlled knowledge graph.

No networking, Bluetooth, firmware commands or third-party dependencies.
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH = ROOT / "knowledge" / "graph.json"
KINDS = {"device", "fact", "interface", "question", "component", "source", "decision", "milestone"}
CERTAINTY = {"observed", "documented", "inferred", "unknown", "planned"}


def load_graph():
    with GRAPH.open(encoding="utf-8") as handle:
        return json.load(handle)


def validate(graph):
    errors = []
    if graph.get("schema_version") != 1:
        errors.append("schema_version must be 1")
    nodes = graph.get("nodes")
    edges = graph.get("edges")
    if not isinstance(nodes, list) or not isinstance(edges, list):
        return ["nodes and edges must both be arrays"]

    index = {}
    for position, node in enumerate(nodes):
        if not isinstance(node, dict):
            errors.append(f"nodes[{position}] is not an object")
            continue
        identifier = node.get("id")
        if not isinstance(identifier, str) or not identifier:
            errors.append(f"nodes[{position}]: missing id")
            continue
        if identifier in index:
            errors.append(f"duplicate node id: {identifier}")
        index[identifier] = node
        if node.get("kind") not in KINDS:
            errors.append(f"{identifier}: invalid kind")
        if node.get("certainty") not in CERTAINTY:
            errors.append(f"{identifier}: invalid certainty")
        for field in ("title", "summary"):
            if not isinstance(node.get(field), str) or not node[field].strip():
                errors.append(f"{identifier}: missing {field}")
        if not isinstance(node.get("tags"), list) or not all(isinstance(x, str) for x in node["tags"]):
            errors.append(f"{identifier}: invalid tags")
        refs = node.get("refs")
        if not isinstance(refs, list) or not refs:
            errors.append(f"{identifier}: refs must contain evidence URLs or repository paths")
            continue
        for ref in refs:
            if not isinstance(ref, str):
                errors.append(f"{identifier}: non-string ref")
            elif ref.startswith(("https://", "http://")):
                continue  # Offline validation does not attest to remote source correctness.
            elif Path(ref).is_absolute() or ".." in Path(ref).parts or not (ROOT / ref).is_file():
                errors.append(f"{identifier}: missing or unsafe repo reference: {ref}")

    for position, edge in enumerate(edges):
        if not isinstance(edge, dict):
            errors.append(f"edges[{position}]: not an object")
            continue
        for side in ("from", "to"):
            if edge.get(side) not in index:
                errors.append(f"edges[{position}]: unresolved {side} {edge.get(side)}")
        if not isinstance(edge.get("relation"), str) or not edge["relation"].strip():
            errors.append(f"edges[{position}]: missing relation")
    return errors


def display_node(node):
    return f"[{node['certainty']}] {node['id']} — {node['title']}\n  {node['summary']}\n  refs: {', '.join(node['refs'])}"


def search(graph, terms):
    tokens = [term.lower() for term in terms]
    matches = []
    for node in graph["nodes"]:
        title = node["title"].lower()
        tags = " ".join(node["tags"]).lower()
        summary = node["summary"].lower()
        refs = " ".join(node["refs"]).lower()
        searchable = " ".join([node["id"].lower(), title, tags, summary, refs])
        if all(token in searchable for token in tokens):
            score = sum(4 * (t in title) + 3 * (t in tags) + 2 * (t in node["id"].lower()) + (t in summary) for t in tokens)
            matches.append((score, node))
    return [node for _, node in sorted(matches, key=lambda item: (-item[0], item[1]["id"]))]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="Print structured JSON output for agents")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("validate", help="Verify schema, references and graph edges")
    find = commands.add_parser("search", help="Find evidence, questions, components and decisions")
    find.add_argument("terms", nargs="+")
    show = commands.add_parser("show", help="Display one node")
    show.add_argument("id")
    related = commands.add_parser("related", help="Display connected nodes and relationships")
    related.add_argument("id")
    ls = commands.add_parser("list", help="List all nodes, optionally filtered by kind")
    ls.add_argument("--kind", choices=sorted(KINDS))
    args = parser.parse_args(argv)

    try:
        graph = load_graph()
    except (OSError, json.JSONDecodeError) as exc:
        print(f"Cannot open knowledge graph: {exc}", file=sys.stderr)
        return 2

    if args.command == "validate":
        errors = validate(graph)
        result = {"valid": not errors, "errors": errors, "nodes": len(graph.get("nodes", [])), "edges": len(graph.get("edges", []))}
        if args.json:
            print(json.dumps(result, indent=2))
        elif errors:
            for error in errors:
                print("ERROR:", error)
        else:
            print(f"OK: {result['nodes']} nodes, {result['edges']} edges; references and links valid (offline)")
        return 0 if not errors else 1

    problems = validate(graph)
    if problems:
        print("Knowledge graph is invalid; run: python tools/kg.py validate", file=sys.stderr)
        return 2

    by_id = {node["id"]: node for node in graph["nodes"]}
    if args.command == "search":
        result = search(graph, args.terms)
    elif args.command == "list":
        result = [node for node in graph["nodes"] if not args.kind or node["kind"] == args.kind]
    else:
        selected = by_id.get(args.id)
        if selected is None:
            print(f"Unknown node ID: {args.id}", file=sys.stderr)
            return 1
        if args.command == "show":
            result = [selected]
        else:
            result = []
            for edge in graph["edges"]:
                if edge["from"] == args.id:
                    result.append({"relation": edge["relation"], "direction": "out", "node": by_id[edge["to"]]})
                if edge["to"] == args.id:
                    result.append({"relation": edge["relation"], "direction": "in", "node": by_id[edge["from"]]})

    if args.json:
        print(json.dumps(result, indent=2))
    elif not result:
        print("No matches.")
    elif args.command == "related":
        for item in result:
            arrow = "->" if item["direction"] == "out" else "<-"
            print(f"{arrow} {item['relation']}: {item['node']['id']} — {item['node']['title']}")
    else:
        for node in result:
            print(display_node(node))
            print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
