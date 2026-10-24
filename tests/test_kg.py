"""Offline CLI contract tests for the project knowledge graph."""
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "kg.py"


def run_tool(*arguments):
    return subprocess.run([sys.executable, str(TOOL), *arguments],
                          cwd=ROOT, text=True, capture_output=True, check=False)


class KnowledgeGraphTests(unittest.TestCase):
    def test_graph_is_valid(self):
        result = run_tool("validate")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("OK:", result.stdout)

    def test_search_returns_chip_family(self):
        result = run_tool("search", "BB2")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("fact-bb2", result.stdout)

    def test_related_follows_evidence(self):
        result = run_tool("related", "fact-bb2")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("fact-fff1", result.stdout)
        self.assertIn("question-soc", result.stdout)

    def test_machine_readable_results(self):
        result = run_tool("--json", "search", "recovery")
        self.assertEqual(result.returncode, 0, result.stderr)
        data = json.loads(result.stdout)
        self.assertTrue(any(item["id"] == "question-recovery" for item in data))

    def test_unknown_id_fails(self):
        result = run_tool("show", "not-an-id")
        self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
