# CR-197 · module 包覆盖率独立门禁（R-5）

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.0 |
| 状态 | 已实施 |
| 维护人 | AI 会话（2026-11-05） |
| 最后更新 | 2026-11-05 |
| 关联 CR | [CR-197](../../变更日志/变更台账.md#cr-197) |

## 1. 目标

给 `com.campuslink.module.**` 单独设一条**行覆盖 ≥ 0.80** 的门禁，并保证「样本为空即失败」——避免全局 80% 被 `common` / `config` / `security`（当前 92%~96%）掩盖，也避免 JaCoCo rule 级 `includes` 空匹配静默通过。现状：module 行覆盖 **83.9%（3251/3873，399 类）**，全局 81.5%。

## 2. 范围

- **做**：新增独立检查脚本；把检查接入 `mvn verify`（本地与 CI 同一条命令）；更新 pom 注释与 AGENTS 常用命令。
- **不做**：不改全局 0.80 规则、不加按上下文（account / forum）的分项阈值、不引入分支覆盖、不改 `backend-ci.yml`、不改任何业务代码或测试。

## 3. 改动点（文件级）

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/scripts/check_module_coverage.py` | 新增 | 解析 `target/site/jacoco/jacoco.xml`，统计 `com/campuslink/module` 行覆盖；样本为 0 或报告缺失 → exit 2；低于阈值 → exit 1 |
| `backend/pom.xml` | 改 | 新增 `exec-maven-plugin`，绑 `verify`（声明在 jacoco 之后）；另加 `linux` profile 把 `python` 换成 `python3` |
| `AGENTS.md` | 改 | `mvn verify` 注释补「module 覆盖率门禁」，并注明本机需 `python` 在 PATH |
| `docs/开发/实施方案/CR-197-module覆盖率门禁.md` | 新增 | 本方案 |
| `docs/变更日志/变更台账.md` | 改 | §1 登记 CR-197（一行） |

## 4. 步骤

- [ ] 1. 写脚本并用实际 `jacoco.xml` 跑通：正常 → exit 0 并打印 ratio。
- [ ] 2. 反证：`--min 0.99` → exit 1；不存在的报告 / 空样本 XML → exit 2。
- [ ] 3. pom 接入（`verify` 阶段，`${coverage.python}` 默认 `python`；`linux` profile 覆盖为 `python3`）。
- [ ] 4. `mvn verify` 全绿，确认 gate 行出现在输出尾段。
- [ ] 5. 同步 AGENTS / 台账 / 方案实施结果。

## 5. 验证

- **单测**：不新增 Java 测试；脚本用上述 3 组输入做正反验证。
- **真机**：`mvn verify` 通过（864 例 / 17 跳过），且 gate 打印 `module line=0.839 ≥ 0.80 (classes=399)`。
- **契约**：不触碰 `openapi.json`，diff 应为空。
- **回归面**：确认 jacoco 全局 0.80 与 SpotBugs / Checkstyle 执行顺序、耗时无明显变化；Linux（CI）与 Windows 本机各跑一次。

## 6. 回滚

回滚单个提交即可（删脚本 + 去插件 + 恢复 AGENTS 注释）；无数据、无契约、无开关残留。

## 7. 实施结果（实施后追加，不回改上文）

- 实际改动与计划一致：新增 `backend/scripts/check_module_coverage.py`；`pom.xml` 加 `exec-maven-plugin`（`verify`，声明在 jacoco 后）与 `python3-on-unix` profile；AGENTS 两条注释同步；未改 CI、业务代码与测试。
- 正验证：`mvn verify` exit 0，门禁输出 `[PASS] module line coverage 0.839 (3251/3873) classes=399 threshold=0.80`。
- 反证三组：`--min 0.99` → exit 1；报告缺失 → exit 2；空样本 XML → exit 2。构建级反证：阈值临时改 0.99 后 `mvn verify` 在 `module-coverage-gate` 处失败（exit 1），已恢复 0.80。
- 偏差：无。遗留：本机 `mvn verify` 现要求 PATH 有 Python（Windows `python` / unix `python3`），已在 AGENTS「常用命令」注明；阈值 0.80 对应当前 83.9%，余量约 3.9pp。
