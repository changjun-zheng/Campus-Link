#!/usr/bin/env python3
"""依赖漏洞扫描（CR-148 / CR-149）：Maven 运行时依赖 + npm 全量包 → OSV 公共库。

为什么是 OSV 而不是 `npm audit` / OWASP Dependency-Check：
- **`npm audit` 在本机与国内镜像下不可用**——`registry.npmmirror.com` 没有实现
  `/-/npm/v1/security/*`，返回 `NOT_IMPLEMENTED`；要么临时换官方 registry，要么走第三方库；
- **OWASP Dependency-Check 要下载并定期更新 NVD 全量数据**（数百 MB、首次十几分钟、CI 里很容易超时），
  对一个单一校项目来说成本远大于收益；
- **OSV**（`api.osv.dev`）是 Google 维护的聚合库，覆盖 GHSA / CVE，提供**按包名+精确版本批量查询**的接口，
  无需 API Key、无需本地数据，一次查询几百个包只要几秒。

覆盖范围与口径：
- 后端用 `mvn dependency:list -DincludeScope=runtime` 拿**解析后**的版本（不是 pom 里写的版本），
  只统计 `compile` / `runtime`，跳过测试与 provided 作用域；
- 前端用 `frontend/package-lock.json` 的**全部**包，`dev: true` 的会标注出来（构建链漏洞也要看见，但可按需降级）；
- 退出码：默认 **只有出现 CRITICAL 才失败**（`--fail-on high` 可收紧、`--fail-on none` 只看报告）。
  这个默认值是刻意的——新公告随时可能出现，若一出现 HIGH 就让所有 CI 红，保护会变成阻塞。

用法（仓库根目录）：
    python backend/scripts/scan_deps_osv.py                 # 报告；有 CRITICAL 则 exit 1
    python backend/scripts/scan_deps_osv.py --fail-on high  # 收紧
    python backend/scripts/scan_deps_osv.py --skip-maven    # 只用已有 deps 清单（CI 里由前置步骤生成）
    python backend/scripts/scan_deps_osv.py --json out.json # 另存机器可读结果
"""

from __future__ import annotations

import argparse
import collections
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POM = ROOT / "backend" / "pom.xml"
LOCK = ROOT / "frontend" / "package-lock.json"
OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/"
SEVERITY_ORDER = {"CRITICAL": 0, "HIGH": 1, "MODERATE": 2, "MEDIUM": 2, "LOW": 3, "UNKNOWN": 4}
DEFAULT_DEPS_FILE = ROOT / "backend" / "target" / "deps-for-scan.txt"


def post_json(url: str, payload: dict) -> dict:
    req = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=90) as resp:
        return json.load(resp)


def get_json(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=90) as resp:
        return json.load(resp)


# ---------------------------------------------------------------- 依赖清单
MAVEN_LINE = re.compile(r"^\s+([\w.\-]+):([\w.\-]+):(\w+):([\w.\-]+):(\w+)")


def backend_dependencies(deps_file: Path) -> list[dict]:
    if not deps_file.is_file():
        raise SystemExit(f"找不到依赖清单 {deps_file}：先跑 "
                         f"`mvn -q dependency:list -DincludeScope=runtime -DoutputFile={deps_file}`")
    out = []
    for line in deps_file.read_text(encoding="utf-8", errors="replace").splitlines():
        m = MAVEN_LINE.match(line)
        if not m:
            continue
        group, artifact, typ, version, scope = m.groups()
        if typ != "jar" or scope not in ("compile", "runtime"):
            continue
        if version in ("", "[unknown]") or "SNAPSHOT" in version:
            continue
        out.append({"ecosystem": "Maven", "name": f"{group}:{artifact}", "version": version,
                    "dev": False, "scope": scope})
    return out


def resolve_maven_deps(deps_file: Path) -> None:
    """在仓库内跑一次 dependency:list（CI 里由前置步骤完成，可用 --skip-maven 跳过）。"""
    deps_file.parent.mkdir(parents=True, exist_ok=True)
    cmd = ["mvn", "-q", "dependency:list", "-DincludeScope=runtime",
           f"-DoutputFile={deps_file}", "-f", str(POM)]
    print(f"[信息] 解析后端依赖：{' '.join(cmd[:4])} …")
    subprocess.run(cmd, check=True, cwd=ROOT)


def frontend_dependencies() -> list[dict]:
    if not LOCK.is_file():
        raise SystemExit(f"找不到 {LOCK}")
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    out = []
    for path, meta in lock.get("packages", {}).items():
        if not path.startswith("node_modules/"):
            continue
        version = meta.get("version")
        if not version:
            continue
        out.append({
            "ecosystem": "npm",
            "name": meta.get("name") or path.split("node_modules/")[-1],
            "version": version,
            "dev": bool(meta.get("dev") or meta.get("devOptional")),
        })
    return out


# ---------------------------------------------------------------- OSV
def query_osv(deps: list[dict]) -> dict[tuple[str, str], list[str]]:
    """返回 {(ecosystem, name@version): [vuln_id...]}"""
    hits: dict[tuple[str, str], list[str]] = {}
    for start in range(0, len(deps), 400):
        chunk = deps[start:start + 400]
        payload = {"queries": [{"package": {"name": d["name"], "ecosystem": d["ecosystem"]},
                                "version": d["version"]} for d in chunk]}
        result = post_json(OSV_BATCH, payload)
        for dep, item in zip(chunk, result.get("results", [])):
            vulns = [v["id"] for v in (item.get("vulns") or [])]
            if vulns:
                hits[(dep["ecosystem"], f"{dep['name']}@{dep['version']}")] = vulns
    return hits


def severity_of(vuln: dict) -> str:
    sev = (vuln.get("database_specific") or {}).get("severity")
    if sev:
        return sev
    for s in vuln.get("severity") or []:
        if s.get("type") == "CVSS_V3":
            return "CVSS:" + str(s.get("score", ""))
    return "UNKNOWN"


def fixed_versions(vuln: dict, package_name: str) -> list[str]:
    fixed = set()
    for affected in vuln.get("affected") or []:
        if (affected.get("package") or {}).get("name") != package_name:
            continue
        for rng in affected.get("ranges") or []:
            for event in rng.get("events") or []:
                if "fixed" in event:
                    fixed.add(event["fixed"])
    return sorted(fixed)


def main() -> int:
    parser = argparse.ArgumentParser(description="依赖漏洞扫描（OSV）")
    parser.add_argument("--skip-maven", action="store_true", help="跳过 dependency:list（用已有清单）")
    parser.add_argument("--deps-file", type=Path, default=DEFAULT_DEPS_FILE)
    parser.add_argument("--fail-on", choices=["critical", "high", "none"], default="critical")
    parser.add_argument("--json", dest="json_out", type=Path)
    args = parser.parse_args()

    if not args.skip_maven:
        resolve_maven_deps(args.deps_file)

    deps = backend_dependencies(args.deps_file) + frontend_dependencies()
    backend = sum(1 for d in deps if d["ecosystem"] == "Maven")
    print(f"[信息] 后端运行时依赖 {backend} 个；前端包 {len(deps) - backend} 个"
          f"（dev {sum(1 for d in deps if d['dev'])} 个）")

    hits = query_osv(deps)
    if not hits:
        print("\n✓ 未发现已知漏洞（OSV 库按精确版本查询）")
        return 0

    cache: dict[str, dict] = {}
    rows = []
    for dep in deps:
        key = (dep["ecosystem"], f"{dep['name']}@{dep['version']}")
        for vuln_id in hits.get(key, []):
            if vuln_id not in cache:
                try:
                    cache[vuln_id] = get_json(OSV_VULN + vuln_id)
                except Exception as exc:  # noqa: BLE001 - 单条详情失败不应中断整轮
                    cache[vuln_id] = {"id": vuln_id, "error": str(exc)}
            vuln = cache[vuln_id]
            rows.append({
                "ecosystem": dep["ecosystem"], "package": dep["name"], "version": dep["version"],
                "dev": dep["dev"], "id": vuln_id,
                "severity": severity_of(vuln),
                "summary": (vuln.get("summary") or "")[:140],
                "fixed": fixed_versions(vuln, dep["name"]),
            })

    rows.sort(key=lambda r: (SEVERITY_ORDER.get(r["severity"].split(":")[0], 9),
                             r["ecosystem"], r["package"]))
    counts = collections.Counter(r["severity"].split(":")[0] for r in rows)
    affected = len({(r["ecosystem"], r["package"], r["version"]) for r in rows})
    print(f"[信息] 受影响依赖 {affected} 个 / 公告 {len({r['id'] for r in rows})} 条："
          + "  ".join(f"{k} {v}" for k, v in counts.most_common()))
    for r in rows:
        dev = " (dev)" if r["dev"] else ""
        print(f"  [{r['severity']}] {r['ecosystem']} {r['package']}@{r['version']}{dev}"
              f" → {r['id']} | 修复 {r['fixed'] or '无'}")
        if r["summary"]:
            print(f"      {r['summary']}")

    if args.json_out:
        args.json_out.write_text(json.dumps({"rows": rows, "counts": counts}, ensure_ascii=False, indent=2),
                                 encoding="utf-8")
        print(f"[信息] 详细结果：{args.json_out}")

    if args.fail_on == "none":
        return 0
    gate = ("CRITICAL",) if args.fail_on == "critical" else ("CRITICAL", "HIGH")
    failed = [r for r in rows if r["severity"].split(":")[0] in gate]
    if failed:
        print(f"\n✗ 有 {len(failed)} 条达到失败阈值（--fail-on {args.fail_on}）")
        return 1
    print(f"\n! 有漏洞但未达失败阈值（--fail-on {args.fail_on}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
