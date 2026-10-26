# 数据变更脚本（PyMySQL）

> 本目录承担**纯数据**的增删改（补字典、修数据、清理脏数据），与**结构变更**（`src/main/resources/db/migration/` 下的 Flyway 迁移）严格分离（CR-014）。

## 为什么分两条轨

| 轨 | 工具 | 管什么 | 有版本记录吗 |
|----|------|--------|-------------|
| 结构轨 | **Flyway**（`db/migration/V*.sql`） | 建表 / 改列 / 加索引 / 参考数据 | ✅ `flyway_schema_history`，启动自动执行 |
| 数据轨 | **PyMySQL**（本目录 `D*.py`） | 业务数据的插入 / 修正 / 删除 | ❌ 无自动记录，**顺序与幂等由操作者负责** |

数据轨没有 `flyway_schema_history` 这样的自动校验，所以每条脚本必须自带**幂等判断**与**dry-run**，并由本文件记录执行情况（见下方"执行记录"）。

## 命名规范

```
D<序号>__<描述>.py        例：D0xx__backfill_user_grade.py（假想示例，勿真建）
```

- `D` 前缀 + 三位递增序号 + 双下划线 + 小写英文描述，与 Flyway 的 `V/R/U` 风格一致、一眼看出先后顺序。
- 序号**递增且不可复用**；已删除的编号不回收。**下一个可用序号看下方「执行记录」与 `data/` 目录实际文件**——`D001` 已由 [CR-054](../../docs/变更日志/变更台账.md#cr-054) 的 `D001__seed_dev_roster_45.py` 占用（本文件早期示例里的 `D001__backfill_user_grade.py` 是建档期的假想名，从未存在）。
- `_template.py` 是模板，不是迁移，新建脚本请复制它改名。

## 硬性约定

1. **默认 dry-run**：不加参数只**打印将要执行的 SQL 与影响行数**，不做任何写入；确认无误后加 `--apply` 才真正提交。
2. **凭据只从环境变量读**（`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`），**严禁硬编码**——仓库、示例、测试都不得出现可用凭据字面量（AGENTS.md 红线）。
3. **事务包裹**：全部语句在一个事务内；任一失败即整体回滚。**DDL 语句会隐式提交，不能放进数据脚本**——那是 Flyway 的职责。
4. **幂等**：用 `WHERE` 精确限定影响范围，或先查后写；重复执行的结果必须与执行一次相同。
5. **先备份**：涉及删除或大批量更新前，先确认有可回退的快照（开发期至少 `mysqldump` 一次）。

## 环境准备（一次性）

用**项目独立的虚拟环境**，不要装进系统或共享解释器：

```bash
cd backend/scripts
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt     # Windows
# .venv/bin/pip install -r requirements.txt       # macOS / Linux
```

`.venv/` 已被 `.gitignore` 排除。

> ⚠️ **`requirements.txt` 必须保持纯 ASCII**（只有包名与版本号，注释用英文）。
> pip 在 Windows 上按**系统区域编码**（中文环境为 GBK）解析 requirements 文件，
> 文件里出现 UTF-8 中文注释会直接抛 `UnicodeDecodeError: 'gbk' codec can't decode ...`
> 导致安装失败。中文说明写在本 README，不要写进 `requirements.txt`（已实测踩过）。

## 执行

```bash
# 1. 先干跑（默认），确认影响范围
.venv/Scripts/python data/D001__seed_dev_roster_45.py

# 2. 确认无误后真正执行
.venv/Scripts/python data/D001__seed_dev_roster_45.py --apply
```

连远程 / 非默认库时用环境变量覆盖，例如：

```bash
DB_PORT=33061 DB_NAME=campuslink_staging \
  .venv/Scripts/python data/D001__seed_dev_roster_45.py --apply
```

> ⚠️ **`D001` 与常规模板不同：它对数据库只读，写入靠编排既有 HTTP 接口完成**（名册走 `POST /api/v1/admin/roster/import`、账号走 `verify-student → captcha → register`），目的是复用生产代码的 HMAC / AES-GCM 加密与审计日志，不在 Python 里复刻密码学。因此执行它还需要：① 后端已启动且 `API_BASE` 指得到；② 该实例为 `APP_ROSTER_BYPASS=false`（否则学籍核验走内置名册、库里的名册行不可见）；③ `VERIFY_IP_HOURLY_LIMIT` 抬到 ≥ 45（默认 10 次/小时/IP 会在第 11 次核验返回 `2103`）。细节与账号清单见 [docs/开发/开发测试账号清单.md](../../docs/开发/开发测试账号清单.md)。
> **后续脚本若只写 SQL，仍照 `_template.py` 来，不要模仿这条的 HTTP 编排。**

## 执行记录（手工维护）

数据轨没有自动历史，**每次 `--apply` 后请在此登记**，避免重复执行或漏执行：

| 脚本 | 环境 | 执行时间 | 操作人 | 影响行数 | 备注 |
|------|------|---------|--------|---------|------|
| — | — | — | — | — | 尚无生产 / 共享环境的数据变更；开发期数据随 Flyway 重建 |
| `D001__seed_dev_roster_45.py` | **本机开发库** `127.0.0.1:3306/campuslink`（经 8089 临时实例，`APP_ROSTER_BYPASS=false` + `VERIFY_IP_HOURLY_LIMIT=60`） | 2026-09-15 22:24~22:26 | AI 会话（[CR-054](../../docs/变更日志/变更台账.md#cr-054)） | **SQL 直写 0 行**；经接口产生 `student_roster` +45 行（批次 `dev-sim-45`）、`users` +45 个、`audit_logs` +1 条 `ROSTER_IMPORT` | 首灌。导入 `inserted=45 skipped=0 failed=0`；注册 45/45；抽 3 个账号登录 + `/users/me` 昵称复核**当次失败**（验证码重发间隔 60s 内重发被 `2002` 拒、旧码已被注册消耗 ⇒ `2005`），已修脚本为重发被拒时等待重试 |
| `D001__seed_dev_roster_45.py` | 同上 | 2026-09-15 22:27 | AI 会话（同上） | **0 新增**（`inserted=0 skipped=45`、新注册 0） | 幂等复核 + 登录复核重跑：`3/3` 通过（`nickname=模拟学生01~03`）。复核后 `student_roster(dev-sim-45)=45` 行、`used_user_id` 全非空 45、`users(nickname LIKE '模拟学生%')=45` |

## 文档一致性检查（CR-146）

`check_docs_consistency.py` —— 纯 stdlib、零依赖，**只报告不改文件**：

```bash
python backend/scripts/check_docs_consistency.py        # 仓库根目录执行；有漂移 exit 1
python backend/scripts/check_docs_consistency.py --json # 机器可读
```

- **C1 版本登记**：`docs/README` §2 登记的版本 ↔ 各文档头部〈版本〉（表格行与 YAML frontmatter 两种写法都认）；
- **C2 页面清单**：`frontend/src/router/index.ts` 的路由 ↔ 界面规范里登记的路径；
- **C3 台账锚点**：docs 内 `#cr-xxx` 引用 ↔ 台账 §1 行或 §2 小节锚（草案预留号走白名单，`#cr-0xx` 占位示例不计）。

**为什么需要**：状态文档落后代码两轮过不止一次（F-003 把已交付的功能写成缺口、界面规范把 `/admin` 写成占位页、
README 的登记版本落后文件头），后果是**决策输入错**。**未挂 CI**（CI 属红线，待发起人批），改完文档请手动跑一次。

## 依赖漏洞扫描（CR-148）

`scan_deps_osv.py` —— 纯 stdlib；Maven 运行时依赖（`dependency:list` 解析后的版本）+ `frontend/package-lock.json` 全量包 → OSV 公共库。

```bash
python backend/scripts/scan_deps_osv.py                    # 报告；有 CRITICAL 时 exit 1
python backend/scripts/scan_deps_osv.py --fail-on high     # 收紧阈值
python backend/scripts/scan_deps_osv.py --skip-maven       # 用已有 backend/target/deps-for-scan.txt
python backend/scripts/scan_deps_osv.py --json out.json    # 机器可读
```

- **为什么不用 `npm audit`**：本机 registry 是 `registry.npmmirror.com`，该镜像未实现 `/-/npm/v1/security/*`；
- **为什么不用 OWASP Dependency-Check**：需下载并维护 NVD 全量数据，CI 成本高；
- **失败策略**：默认只在 **CRITICAL** 时红，HIGH/MODERATE 列出但不阻塞；
- **CI**：`.github/workflows/deps-ci.yml`（每周一 + pom / lock / 脚本变更触发）。
