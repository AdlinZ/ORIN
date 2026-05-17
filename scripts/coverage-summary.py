#!/usr/bin/env python3
"""Print a small Markdown coverage summary for CI step summaries."""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def pct(covered: int, total: int) -> str:
    if total <= 0:
        return "n/a"
    return f"{covered / total * 100:.2f}%"


def print_table(title: str, rows: list[tuple[str, int | str, int | str, str]]) -> None:
    print(f"### {title}")
    print()
    print("| Metric | Covered | Total | Coverage |")
    print("|--------|---------|-------|----------|")
    for metric, covered, total, coverage in rows:
        print(f"| {metric} | {covered} | {total} | {coverage} |")
    print()


def summarize_jacoco(path: Path) -> None:
    root = ET.parse(path).getroot()
    counters = {counter.attrib["type"]: counter.attrib for counter in root.findall("./counter")}
    rows: list[tuple[str, int | str, int | str, str]] = []
    for label, key in [
        ("Instruction", "INSTRUCTION"),
        ("Line", "LINE"),
        ("Branch", "BRANCH"),
    ]:
        counter = counters.get(key)
        if not counter:
            rows.append((label, "n/a", "n/a", "n/a"))
            continue
        missed = int(counter["missed"])
        covered = int(counter["covered"])
        total = missed + covered
        rows.append((label, covered, total, pct(covered, total)))
    print_table("Backend Coverage", rows)


def summarize_vitest(path: Path) -> None:
    data = json.loads(path.read_text())
    total = data["total"]
    rows = []
    for label, key in [
        ("Statements", "statements"),
        ("Lines", "lines"),
        ("Branches", "branches"),
        ("Functions", "functions"),
    ]:
        metric = total[key]
        rows.append((label, metric["covered"], metric["total"], f'{metric["pct"]:.2f}%'))
    print_table("Frontend Coverage", rows)


def summarize_cobertura(path: Path) -> None:
    root = ET.parse(path).getroot()
    lines_total = int(root.attrib.get("lines-valid", "0"))
    lines_covered = int(root.attrib.get("lines-covered", "0"))
    branches_total = int(root.attrib.get("branches-valid", "0"))
    branches_covered = int(root.attrib.get("branches-covered", "0"))
    rows = [
        ("Lines", lines_covered, lines_total, pct(lines_covered, lines_total)),
        ("Branches", branches_covered, branches_total, pct(branches_covered, branches_total)),
    ]
    print_table("AI Engine Coverage", rows)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: coverage-summary.py <backend|frontend|ai-engine> <coverage-file>", file=sys.stderr)
        return 2

    kind = sys.argv[1]
    path = Path(sys.argv[2])
    if not path.exists():
        print(f"coverage file not found: {path}", file=sys.stderr)
        return 1

    if kind == "backend":
        summarize_jacoco(path)
    elif kind == "frontend":
        summarize_vitest(path)
    elif kind == "ai-engine":
        summarize_cobertura(path)
    else:
        print(f"unknown coverage kind: {kind}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
