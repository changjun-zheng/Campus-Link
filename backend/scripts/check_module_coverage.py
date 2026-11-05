#!/usr/bin/env python3
"""module 包行覆盖率门禁（CR-197 / 问题清单 R-5）。

为什么不用 JaCoCo 的 rule 级 <includes>：嵌套包前缀在 check 里实测空匹配，
阈值抬到 0.90 也静默 PASS——**空样本的门禁等于没门禁**（见 backend/pom.xml）。
本脚本直接解析 jacoco XML，并把「样本为空 / 报告缺失」判为失败，退出码：

  0 = 达标
  1 = 低于阈值
  2 = 报告缺失、无法解析或 module 样本为空（门禁不能空跑）

用法（在 backend/ 下）：
  python scripts/check_module_coverage.py
  python scripts/check_module_coverage.py --report target/site/jacoco/jacoco.xml --min 0.80
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MODULE_PREFIX = "com/campuslink/module"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="module 包行覆盖率门禁")
    parser.add_argument(
        "--report",
        default="target/site/jacoco/jacoco.xml",
        help="JaCoCo XML 报告路径（默认 target/site/jacoco/jacoco.xml）",
    )
    parser.add_argument(
        "--min",
        dest="minimum",
        type=float,
        default=0.80,
        help="module 行覆盖率下限（默认 0.80）",
    )
    return parser.parse_args()


def module_counters(root: ET.Element) -> tuple[int, int, int]:
    """返回 (covered, missed, classes)；只统计 com/campuslink/module 及其子包。"""
    covered = 0
    missed = 0
    classes = 0
    for package in root.findall("package"):
        name = package.get("name", "")
        if name != MODULE_PREFIX and not name.startswith(MODULE_PREFIX + "/"):
            continue
        for clazz in package.findall("class"):
            classes += 1
            for counter in clazz.findall("counter"):
                if counter.get("type") == "LINE":
                    covered += int(counter.get("covered"))
                    missed += int(counter.get("missed"))
                    break
    return covered, missed, classes


def main() -> int:
    args = parse_args()
    report = Path(args.report)
    if not report.is_file():
        print(f"[FAIL] JaCoCo 报告不存在：{report}（先运行 mvn verify 生成报告）", file=sys.stderr)
        return 2
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError as exc:
        print(f"[FAIL] 无法解析 JaCoCo 报告 {report}：{exc}", file=sys.stderr)
        return 2

    covered, missed, classes = module_counters(root)
    total = covered + missed
    if classes == 0 or total == 0:
        print(
            f"[FAIL] module 样本为空（classes={classes}，lines={total}）："
            "门禁不能空跑，检查报告路径与包名前缀。",
            file=sys.stderr,
        )
        return 2

    ratio = covered / total
    passed = ratio >= args.minimum
    label = "PASS" if passed else "FAIL"
    print(
        f"[{label}] module line coverage {ratio:.3f} ({covered}/{total}) "
        f"classes={classes} threshold={args.minimum:.2f}"
    )
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
