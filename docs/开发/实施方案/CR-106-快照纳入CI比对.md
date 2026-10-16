# CR-106 · 把契约快照纳入机器比对（处置问题清单 E-001）

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.0 |
| 状态 | **待发起人确认**（本轮零代码、零 CI 改动；CI 配置属红线，须点头才动） |
| 维护人 | AI 会话（Qoder @ 2026-10-16） |
| 最后更新 | 2026-10-16 |
| 关联 CR | [CR-106](../../变更日志/变更台账.md#cr-106) · 承接 [CR-090](../../变更日志/变更台账.md#cr-090) §5「CI 备料」与 [CR-103](../../变更日志/变更台账.md#cr-103) |

## 1. 目标

[E-001](../../项目现有问题清单.md) 现在的表述是「快照**无机器校验**，漂移只能靠约定拦」。本 CR 把它变成机器拦：**改 Controller 却忘了重抓快照，`mvn verify` 直接变红**（本机与 CI 同一条命令）。量化判据：注入两类探针（加一个映射方法不重抓 / 给 VO 加一字段不重抓）→ 新测试各自变红；`mvn -B verify` 在 CI 里跑出 `Tests run: 381 + N` 全绿；**不新增任何运行时依赖、不改 `openapi.json`**。

## 2. 范围

- **做（两层，成本差很大，勾选后再动）**：
  - **T1 · 注解级比对（不依赖数据库，永远跑）**——新增 `OpenApiSnapshotOperationsTest`（纯反射）：扫 `com.campuslink.module.*.web` 与 `common.markdown.web` 的映射注解，得出「METHOD + 路径模板」集合与每端的公开/受保护标记（`@PublicEndpoint` vs `@SecurityRequirement`），与归档 `openapi.json` 的 `paths` 逐项比对。**拦住的是本仓最容易复发的漂移**：新增端点忘了重抓（CR-077/078 就欠了两轮，由 CR-080 补做）。
  - **T2 · 真身比对（要服务，CI 里跑）**——新增 `OpenApiSnapshotEqualityIT`（`@SpringBootTest(webEnvironment=RANDOM_PORT)` + `@EnabledIfEnvironmentVariable(SPRING_IT=true)`，沿用 CR-090 那道门）：起真上下文抓 `/api/docs`，用 Jackson 解析后与归档**语义比对**（不比字节：缩进与 `servers` 随抓取方式变，比字节必假红；对象无序、数组有序）。**拦的是字段级漂移**（`UserProfileVo` 加两字段这一类，T1 看不见）。
  - **CI 侧**：`backend-ci.yml` 按 CR-090 §5 备料补 `services: mysql:8（health check）+ redis:7`，建库 `campuslink` / 账号 `campuslink/campuslink`，命令改 `SPRING_IT=true mvn -B verify` ⇒ **集成测试（含 CR-090 那 4 例 SPRING_IT）自此在 CI 真实跑**，顺带把 E-001 的另一半（"CI 从未起过真栈"）一起收掉。
  - **配套**：`AGENTS.md`「测试约定」加一条"改接口不重抓快照即测试红"、基线数单点更新；契约 README §2 与技术方案 §5 各注一行"快照自此有机器校验"；问题清单 **E-001 回置**（T1+T2 都落 = ✅，只落 T1 = 🟡「操作集有机器强制、字段级仍靠人」）。
- **不做（逐条给理由）**：
  - **不比字节 / 不比 sha256**——`servers` 回显请求 Host、缩进属格式，比字节会把正确行为报成缺陷（本仓 v1.14 那次"jar mtime 假信号"同类的错法）；
  - **不在 CI 里重抓并覆盖归档文件**——CI 只判定不改写，否则快照会被 CI 环境细节污染，且丢掉"由谁重抓"的责任点；
  - **不做 §6.3 已登记的表达极限项**（`1002/1003/1004` 三类响应、可重复 `@Pattern`）进比对断言，T1 只比操作集与鉴权标记，避免拿快照的已知短板当缺陷；
  - **不加 Checkstyle / PMD / 依赖漏洞扫描**（另议选型，与 E-003 剩余项一起判）；
  - **不动 `frontend-ci.yml`**（CR-103 那条 lint 步骤仍单独待批，两件事不捆）。

## 3. 改动点（文件级）

| 层 | 文件 | 改动 |
|---|---|---|
| test | 新增 `backend/src/test/java/com/campuslink/contract/OpenApiSnapshotOperationsTest.java` | 反射取映射注解集合 ↔ 归档 `paths`；快照路径经 `System.getProperty("openapi.snapshot.path", "../docs/设计/接口契约/openapi.json")` |
| test | 新增 `.../contract/OpenApiSnapshotEqualityIT.java` | `@SpringBootTest(RANDOM_PORT)` + `SPRING_IT` 门 + Jackson 语义比对（忽略 `openapi` / `servers` / `info.version`） |
| CI | `.github/workflows/backend-ci.yml` | `services: mysql:8 + redis:7`、`env` 五项、`SPRING_IT=true mvn -B verify`（**红线，待批**） |
| test 资源 | `backend/pom.xml` **不改** | 快照留在 `docs/`，用相对路径读（surefire 工作目录是 `backend/`）——**不把契约复制进 `src/test/resources`**，两处存一份必漂移 |
| docs | `AGENTS.md` · [契约 README](../设计/接口契约/README.md) §2 · [技术方案](../设计/技术方案.md) §5 · [问题清单](../项目现有问题清单.md) E-001 · 本台账 · [驾驶舱](../进度驾驶舱.html) | 同步 |

## 4. 步骤

① 勾选 T1 / T1+T2 → ② 先写 T1 并**当场验证它对现状是绿的**（若红，说明快照已漂移，先据实重抓再谈守护）→ ③ T2 与本地 `SPRING_IT=true mvn -B verify`（本机原生 MySQL/Redis 已具备）→ ④ 两支探针反证 → ⑤ CI YAML 落地（仅在批准后）→ ⑥ 推一次空跑验证 CI 真绿 → ⑦ 文档四处同步 + 台账回置 → ⑧ 展示摘要等提交。

## 5. 验证

- `mvn -B verify`：381 + 新增约 6~9 例全绿，SpotBugs `BugInstance 0`、jacoco 60% check 同批不破；
- **反证 ①**：给某 Controller 临时加一个 `@GetMapping("/probe")` 不重抓 → **T1 红**；**反证 ②**：给 `UserProfileVo` 临时加一个组件不重抓 → **T2 红**（只勾 T1 时这条如实标注"未覆盖"）；两支探针删除后归零、按 sha256 复原被改文件；
- **T1 首跑必须对现状为绿**——这是判"快照 == 代码"的独立第三方证据，与本轮手工 `cmp` 结论交叉印证；若首跑就红，先披露漂移再决定；
- CI：推送后看 `backend-ci` 实际跑绿（集成测试首次在 CI 真跑，含 CR-090 那 4 例），预计单次时长由 ~1 分钟升到 3~4 分钟；
- `openapi.json` **零改动**（本 CR 只加校验、不改契约），契约版本不进位。

## 6. 回滚

T1 / T2 是**纯新增测试类**、CI 是**独立几个 commit**：回滚即删两个测试文件（+ revert CI 提交），**零生产代码、零迁移、零依赖 ⇒ 无任何对外行为影响**。若 CI 因服务容器不稳而变红，可先 revert CI 那一个提交、保留 T1 作为无服务依赖的永久下限（这也是把 T1 设计成不依赖服务的原因）。

## 7. 待发起人勾选（三条，勾完才开工）

- **P-1 范围**：□ **T1 + T2（推荐）**——T1 永远跑、T2 连字段级漂移一起堵，并把集成测试首次搬进 CI □ 只 T1——零 CI 风险，但字段级仍靠人重抓
- **P-2 CI 改动**：□ 批准按 CR-090 §5 备料给 `backend-ci.yml` 加 `mysql:8 + redis:7` 服务与 `SPRING_IT=true`（代价：单次 CI 由 ~1 分钟升到 3~4 分钟，且自此多一层"服务容器起不来就红"的外部依赖）□ 不批准，T2 只在本机 `SPRING_IT=true` 跑
- **P-3 首跑发现漂移怎么办**：□ 若 T1 首跑变红，先据实重抓快照并在台账单记一条"发现 X 处漂移"（**推荐**）□ 停下来先报告、等我对每个差异逐条判定
