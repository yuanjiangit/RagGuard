#!/usr/bin/env python3
"""Builds a Markdown metric summary from ragguard-latest-run.json for PR comments.

Usage: summarize.py <report-dir>  — prints Markdown to stdout.
"""
import json
import sys
from pathlib import Path

LABELS = {
    "FAITHFULNESS": "Faithfulness",
    "ANSWER_RELEVANCE": "Answer relevance",
    "CONTEXT_RECALL": "Context recall",
    "CONTEXT_PRECISION": "Context precision",
}
ORDER = ["FAITHFULNESS", "ANSWER_RELEVANCE", "CONTEXT_RECALL", "CONTEXT_PRECISION"]


def fmt(value):
    return f"{value:.3f}" if isinstance(value, (int, float)) else "n/a"


def main(report_dir: str) -> None:
    path = Path(report_dir) / "ragguard-latest-run.json"
    if not path.is_file():
        print(f"⚠️ No RagGuard report found at `{path}` — did the evaluation run and "
              "set `ragguard.report-output-dir`?")
        return
    data = json.loads(path.read_text(encoding="utf-8"))
    aggregates = data.get("aggregateScores", {})
    cases = data.get("caseScores", {})

    lines = [
        "## 🛡️ RagGuard evaluation summary",
        "",
        f"**{data.get('caseCount', len(cases))} test cases**",
        "",
        "| Metric | Score |",
        "|---|---|",
    ]
    for key in ORDER:
        if key in aggregates:
            lines.append(f"| {LABELS.get(key, key)} | **{fmt(aggregates[key])}** |")
    lines += [
        "",
        "<details><summary>Per-case scores</summary>",
        "",
        "| Case | " + " | ".join(LABELS[k] for k in ORDER if k in aggregates) + " |",
        "|---" * (len([k for k in ORDER if k in aggregates]) + 1) + "|",
    ]
    for case_id, scores in cases.items():
        cells = [fmt(scores.get(k)) for k in ORDER if k in aggregates]
        lines.append(f"| `{case_id}` | " + " | ".join(cells) + " |")
    lines += [
        "",
        "</details>",
        "",
        "<sub>Scores in [0,1]; every score drills down to per-claim verdicts in the full HTML report.</sub>",
    ]
    print("\n".join(lines))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: summarize.py <report-dir>", file=sys.stderr)
        sys.exit(2)
    main(sys.argv[1])
