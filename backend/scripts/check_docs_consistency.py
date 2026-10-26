#!/usr/bin/env python3
"""仓库级文档一致性检查（CR-146 / 问题清单 E-016 根治）。

为什么需要它：本项目反复出现"状态文档落后代码两轮而无机器察觉"——F-003 行把已交付的
`/u/:id` 写成占位页（导致一轮开发选错事项）、界面规范 §8.2 把 `/admin` 写成占位页、
`docs/README` §2 的登记版本落后文件头（多个会话各自校准过一次）。这些错都不是"文档不好看"，
而是**决策输入错**。本脚本把三类最贵的漂移变成一条命令可判：

  C1 版本登记一致：docs/README §2 登记的版本 == 各文档头部〈版本〉字段
  C2 页面清单一致：frontend/src/router/index.ts 的路由 == 界面规范 §8 表格里登记的路由
  C3 台账锚点不悬空：docs/ 各文档里的 `#cr-xxx` 引用都能在台账 §1 找到对应行

用法（仓库根目录执行）：
    python backend/scripts/check_docs_consistency.py           # 只报告
    python backend/scripts/check_docs_consistency.py --json    # 机器可读
退出码：0 = 全绿；1 = 有漂移（输出逐条列出）。

刻意不做的事：
- **不自动改文档**——它只指出"哪一处对不上"，改法是人的判断（附证据后回写）；
- 不比对接口契约与代码（那已由 `contract/OpenApiSnapshotOperationsTest` 与
  `contract/OpenApiSnapshotEqualityTest` 机器守，重复实现只会引入第二个事实源）；
- 不解析台账 §1 的"统计"段（该段自 v1.82 起已停更，登记数以 §1 行数为准）。
"""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
README = DOCS / "README.md"
ROUTER = ROOT / "frontend" / "src" / "router" / "index.ts"
UI_SPEC = DOCS / "设计" / "UI规范" / "界面规范.md"
LEDGER = DOCS / "变更日志" / "变更台账.md"


@dataclass
class Report:
    versions: list[str] = field(default_factory=list)
    routes: list[str] = field(default_factory=list)
    anchors: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    @property
    def drift(self) -> list[str]:
        return self.versions + self.routes + self.anchors


# ---------------------------------------------------------------- C1 版本登记
LINK_ROW = re.compile(r"^\|\s*\[[^\]]+\]\(([^)]+)\)\s*\|\s*(v\d+(?:\.\d+)*)\s*\|")
HEADER_VERSION = re.compile(r"^\|\s*(?:文档)?版本\s*\|\s*(v\d+(?:\.\d+)*)[^|]*\|", re.M)
# 部分文档的头信息用 YAML frontmatter（`文档版本: v1.0`）而不是表格行——**两种写法都要认**，
# 否则它们会被静默跳过，等于没检查（本脚本首版就漏过两份）
FRONTMATTER_VERSION = re.compile(r"^(?:文档)?版本\s*:\s*(v\d+(?:\.\d+)*)", re.M)


def check_versions(report: Report) -> None:
    registered: dict[Path, str] = {}
    for line in README.read_text(encoding="utf-8").splitlines():
        m = LINK_ROW.match(line.strip())
        if not m:
            continue
        href, version = m.group(1), m.group(2)
        if href.startswith("http"):
            continue
        target = (README.parent / href.split("#")[0]).resolve()
        if target.is_file():
            registered[target] = version

    report.notes.append(f"C1 读取 {len(registered)} 份 §2 登记文档")
    for target, declared in sorted(registered.items()):
        head = target.read_text(encoding="utf-8")[:4000]
        m = HEADER_VERSION.search(head) or FRONTMATTER_VERSION.search(head)
        if not m:
            report.notes.append(
                f"C1 跳过（头部既无〈版本〉表格行也无 frontmatter 版本字段）：{target.relative_to(ROOT)}"
            )
            continue
        actual = m.group(1)
        if actual != declared:
            report.versions.append(
                f"{target.relative_to(ROOT).as_posix()}：docs/README §2 登记 {declared}，文件头写 {actual}"
            )


# ---------------------------------------------------------------- C2 页面清单
ROUTE_DECL = re.compile(r"path:\s*'([^']+)'")


def normalize_route(path: str) -> str:
    path = path.strip().split("?")[0]
    path = re.sub(r":([A-Za-z_][\w]*)", ":id", path)  # 参数名不参与比对
    return "/" if path == "/" else path.rstrip("/")


def check_routes(report: Report) -> None:
    if not ROUTER.is_file() or not UI_SPEC.is_file():
        report.notes.append("C2 跳过：找不到 router 或界面规范")
        return
    routes = {normalize_route(m.group(1)) for m in ROUTE_DECL.finditer(ROUTER.read_text(encoding="utf-8"))}
    spec = UI_SPEC.read_text(encoding="utf-8")
    documented = {normalize_route(m.group(1)) for m in re.finditer(r"`(/[^`]*)`", spec)}
    # 规范里出现的非路由反引号路径（如 /api/v1/...）不参与比对
    documented = {p for p in documented if not p.startswith("/api")}

    report.notes.append(f"C2 路由 {len(routes)} 条，界面规范里出现的路径 {len(documented)} 条")
    for path in sorted(routes - documented):
        report.routes.append(f"路由 {path} 未在界面规范的页面清单中出现（新增页面忘了登记？）")


# ---------------------------------------------------------------- C3 台账锚点
# 只认「数字后不再接字母数字」的引用：文档里有 `[CR-0xx](#cr-0xx)` 这样的占位示例，不能被当成真锚点
CR_REF = re.compile(r"#cr-(\d+)(?![0-9A-Za-z])")
CR_ROW = re.compile(r"^\|\s*\[CR-(\d+)\]", re.M)
# §2 明细小节锚（早期格式），与 §1 行同为「可跳转的落点」
CR_SECTION_ANCHOR = re.compile(r'<a id="cr-(\d+)"', re.M)
# 增量 PRD 草案预留号：按约定不建 §1 行（见 CR-070 / 台账 §1 编号说明），
# 历史叙述里引用它们不是悬空——白名单写在这里而不是散落在文档里
DRAFT_NUMBERS = {36, 37, 38, 39, 42, 67}


def check_anchors(report: Report) -> None:
    if not LEDGER.is_file():
        report.notes.append("C3 跳过：找不到台账")
        return
    ledger_text = LEDGER.read_text(encoding="utf-8")
    rows = {int(m.group(1)) for m in CR_ROW.finditer(ledger_text)}
    sections = {int(m.group(1)) for m in CR_SECTION_ANCHOR.finditer(ledger_text)}
    known = rows | sections | DRAFT_NUMBERS
    report.notes.append(
        f"C3 台账 §1 共 {len(rows)} 行、§2 小节锚 {len(sections)} 处、草案预留号 {len(DRAFT_NUMBERS)} 个"
    )

    dangling: dict[int, set[str]] = {}
    for doc in DOCS.rglob("*.md"):
        text = doc.read_text(encoding="utf-8")
        for m in CR_REF.finditer(text):
            n = int(m.group(1))
            if n not in known:
                dangling.setdefault(n, set()).add(doc.relative_to(ROOT).as_posix())
    for n, files in sorted(dangling.items()):
        where = "、".join(sorted(files)[:3]) + ("…" if len(files) > 3 else "")
        report.anchors.append(f"#cr-{n} 被引用但台账既无 §1 行也无 §2 小节锚（引用处：{where}）")


# ---------------------------------------------------------------- main
def main() -> int:
    report = Report()
    check_versions(report)
    check_routes(report)
    check_anchors(report)

    if "--json" in sys.argv:
        print(json.dumps(
            {"drift": report.drift, "notes": report.notes},
            ensure_ascii=False, indent=2,
        ))
    else:
        for note in report.notes:
            print(f"[信息] {note}")
        if report.drift:
            print("\n发现漂移：")
            for item in report.drift:
                print(f"  ✗ {item}")
            print(f"\n共 {len(report.drift)} 处。改法：按证据回写文档（本脚本不改文件）。")
        else:
            print("\n✓ 三类一致性检查全部通过（版本登记 / 页面清单 / 台账锚点）")
    return 1 if report.drift else 0


if __name__ == "__main__":
    sys.exit(main())
