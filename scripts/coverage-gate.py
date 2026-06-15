#!/usr/bin/env python3
"""Coverage gate（Phase 2 收尾：小刀 6）。

读 3 端 coverage report（jacoco xml / nyc json / pytest-cov xml），对照
`coverage-baseline.json` 门槛：
- 当前覆盖率 < 门槛 → fail
- 退出非零让 CI step 失败

用法（CI 步骤）：
    python scripts/coverage-gate.py <service> <report_path>
    # service: ai-engine | backend | frontend
    # report_path: 相对 repo root 的 coverage 文件路径

门槛定义在 `coverage-baseline.json` 根目录。每个 service 配
`threshold_pct`（绝对值）与 `last_observed_pct`（CI 失败时只报
"当前 X% < 门槛 Y%"，不读 last_observed；保留 last 字段给"基线随
时间漂移"的人类参考，不在 CI 自动 fail-on-decrease 上做激进
fail —— 历史 commit 经常有覆盖率自然波动）。

不依赖第三方包（不装 coverage / lxml），用 std-lib + coverage 自带
xml/parser。
"""
from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
BASELINE_PATH = REPO_ROOT / "coverage-baseline.json"


def read_ai_engine_pct(xml_path: Path) -> float:
    """pytest-cov coverage.xml：读 `class[@filename='app/']` / 全局 stat
    `line-rate` 属性作为语句覆盖率（pytest-cov 默认按行计）。"""
    root = ET.parse(xml_path).getroot()
    # pytest-cov coverage.xml: <coverage line-rate="0.6" ...><packages>
    line_rate = root.attrib.get("line-rate")
    if line_rate is None:
        raise ValueError(f"coverage.xml 缺 line-rate: {xml_path}")
    return float(line_rate) * 100


def read_backend_pct(xml_path: Path) -> float:
    """jacoco.xml：读 `counter[@type='INSTRUCTION']` 根节点。
    `covered` / `missed` 给出 instruction-level 覆盖率。"""
    root = ET.parse(xml_path).getroot()
    for counter in root.findall("./counter"):
        if counter.attrib.get("type") == "INSTRUCTION":
            covered = int(counter.attrib["covered"])
            missed = int(counter.attrib["missed"])
            total = covered + missed
            if total <= 0:
                return 0.0
            return covered / total * 100
    raise ValueError(f"jacoco.xml 缺 INSTRUCTION counter: {xml_path}")


def read_frontend_pct(json_path: Path) -> float:
    """nyc coverage-summary.json：读 `total.statements.pct`。"""
    with json_path.open() as f:
        data = json.load(f)
    total = data.get("total", {})
    stmts = total.get("statements", {})
    pct = stmts.get("pct")
    if pct is None:
        raise ValueError(f"coverage-summary.json 缺 total.statements.pct: {json_path}")
    return float(pct)


def check(service: str, report_path: Path) -> int:
    if not BASELINE_PATH.exists():
        print(f"FAIL: baseline 不存在: {BASELINE_PATH}", file=sys.stderr)
        return 2
    baseline = json.loads(BASELINE_PATH.read_text())
    if service not in baseline:
        print(f"FAIL: baseline 缺 service={service}", file=sys.stderr)
        return 2
    cfg = baseline[service]
    threshold = cfg["threshold_pct"]
    last_observed = cfg.get("last_observed_pct", "?")

    if not report_path.exists():
        print(f"FAIL: {service} coverage report 缺失: {report_path}", file=sys.stderr)
        return 2

    readers = {
        "ai-engine": read_ai_engine_pct,
        "backend": read_backend_pct,
        "frontend": read_frontend_pct,
    }
    if service not in readers:
        print(f"FAIL: 未知 service={service}", file=sys.stderr)
        return 2
    current = readers[service](report_path)

    print(f"[coverage-gate] {service}: {current:.2f}% (threshold {threshold}%, last_observed {last_observed}%, metric={cfg['metric']})")

    if current < threshold:
        print(
            f"FAIL: {service} 覆盖率 {current:.2f}% 低于门槛 {threshold}% "
            f"—— 补测试或调整 coverage-baseline.json 门槛"
        )
        return 1

    print(f"PASS: {service} 覆盖率 {current:.2f}% ≥ 门槛 {threshold}%")
    return 0


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("usage: coverage-gate.py <service> <report_path>", file=sys.stderr)
        return 2
    return check(argv[1], Path(argv[2]))


if __name__ == "__main__":
    sys.exit(main(sys.argv))
