# OpenAPI 快照说明（`./`）

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.15 |
| 状态 | 已归档（**快照**，接口契约变更后须再生成，见 §2） |
| 维护人 | 技术负责人（发起人兼任） |
| 最后更新 | 2026-09-24 |
| 对应行动项 | [设计门纪要](../../评审/设计门纪要.md) **A3-4**：补归档 OpenAPI 快照，或补 2~3 个主链路接口请求/响应结构（**两个备选项本文都做**） |
| 变更登记 | [CR-019](../../变更日志/变更台账.md#cr-019)（首次归档）· [CR-021](../../变更日志/变更台账.md#cr-021)（修复 N-1 / N-2 / N-3 后**再生成**：契约新增错误响应与 bearerAuth 声明）· [CR-022](../../变更日志/变更台账.md#cr-022)（Sprint 2 MVP 新增 6 个论坛端点后**再生成**：6 → **12** 端点）· [CR-028](../../变更日志/变更台账.md#cr-028)（**N-4 闭环后**再生成：`bearerAuth` 描述据实改写，**端点 / schema 数一字未变**）· [CR-031](../../变更日志/变更台账.md#cr-031)（**路径级鉴权落地后再生成**：`bearerAuth` 描述随框架拦截据实改写，**端点 / schema / `security` 声明仍未变**）· [CR-043](../../变更日志/变更台账.md)（**采纳最佳答案落地后再生成**：新增 `POST /posts/{id}/accept` 与 `AcceptReplyCommand` schema，12 → **13** 端点）· [CR-048](../../变更日志/变更台账.md)（**点赞收藏落地后再生成**：新增 4 端点与 `InteractionResult` schema，13 → **17** 端点）· [CR-049](../../变更日志/变更台账.md)（**站内搜索落地后再生成**：新增公开端点 `GET /posts/search`，17 → **18** 端点，**schema 数未变**）· [CR-050](../../变更日志/变更台账.md)（**通知中心落地后再生成**：新建 `module/notification` 上下文，新增 3 个受保护端点与 7 个 schema，18 → **21** 操作 / 33 → **40** schema；⚠️ 本次 diff **非纯新增**，另有 1 处 springdoc 的 `operationId` 重编号，见 §2）· [CR-051](../../变更日志/变更台账.md)（**时间戳时区口径修复——非再生成**：只改数据源时区参数、不动接口形状，重抓核对 `git diff` **为空**，快照文件零改动；本文新增 §1「时间字段口径」条与 §2 核对行，见 §7 v1.9.2）· [CR-058](../../变更日志/变更台账.md#cr-058)（**热榜定时任务（ADR-006）+ `sort=hot` 落地后再生成**：**契约规模零变化**，仍 8 tag / 21 操作（19 路径）/ 40 schema / 12 受保护操作；唯一变化是 `GET /api/v1/posts` 多一个 `sort` 查询参数（`default: "latest"`）与该操作 `summary` 的一处据实改写，diff **+10 / −1**，见 §7 v1.10）· [CR-060](../../变更日志/变更台账.md#cr-060)（**修复已下架回复仍可被点赞与采纳——非再生成**：只把楼层写用例的读侧换成带 `status` 过滤的`findVisibleById`，**不动 Controller / VO / `ResultCode` / 鉴权声明**，重抓核对逐字节一致、快照文件零改动，见 §7 v1.10.1）· [CR-065](../../变更日志/变更台账.md#cr-065)（**帖子删除 F-FORUM-006 后端落地后再生成**：新增**受保护**端点 `DELETE /api/v1/posts/{id}`，21 → **22** 操作、受保护 12 → **13**；**路径仍 19、schema 仍 40**（挂既有路径、`ApiResponseVoid` 已存在），diff **+85 / −0 纯新增**，见 §7 v1.11）· [CR-066](../../变更日志/变更台账.md#cr-066)（**内容处置「下架 / 恢复」F-SAFE-003 一支落地后再生成**：新增 2 个受保护端点 `PUT /api/v1/admin/{posts,replies}/{id}/status`，22 → **24** 操作、路径 19 → **21**、schema 40 → **41**（新 `ModerationCommand`）、tag 8 → **9**（新 `admin-moderation`）、受保护 13 → **15**；diff **+232 / −16**——删除行是 `ApiResponseVoid` 整块换位，经结构化比对判定仍为纯新增，见 §7 v1.12）· **v1.12.1（2026-09-19，非再生成）**（**CR-066 + CR-068 提交后的「提交后核对」一并完成**：两条 CR 已随 **`f58ef73`** 提交并推送 origin/main，自**已提交源码**重新构建的可执行 jar 起临时端口 **8097** 实例重抓 `/api/docs`，`servers` 照既有先例归一回 8088 后与仓库 `openapi.json` **逐字节一致**（**68470 字节 / sha256 `28327bc89910d9c1…19266cfb`**）——**同一轮把 v1.11 那笔从未复核的敞口也一起核掉了**（同一实例、同一份产物），快照自此**两版同时可追溯到提交**；`openapi.json` 文件零改动，见 §2 末行与 §7）· [CR-071](../../变更日志/变更台账.md)（**个人中心第一轮 F-ACC-007 落地后再生成**：新增 **3 个受保护 operation**（`PUT /api/v1/users/me` 资料编辑 + `GET /api/v1/posts/mine` + `GET /api/v1/replies/mine`），24 → **27** 操作、路径 21 → **23**、schema 41 → **48**、**tag 仍 9**（`MyReplyController` 复用 `reply` tag，未因新开映射类而增计数）、受保护 15 → **18**；⚠️ **本轮 diff 不是纯新增**——既有 `UserVo` 多一个 `status`、既有 `RegisterCommand.nickname` 多一个 `pattern`（注册侧同批收紧字符集禁则）、既有 `GET /users/me` 的 `summary` 一处据实改写，diff **+462 / −49**，见 §7 v1.13） |
| 关联记录 | [技术方案](../技术方案.md) §5 接口契约 · [PRD](../../需求/产品需求文档.md) F-ACC-004 · [Sprint 1 计划](../../开发/Sprint1计划.md) · [Sprint 2 计划](../../开发/Sprint2计划.md) · [Sprint 2 增量设计](../../开发/Sprint2增量设计.md) |

## 1. 这份文件是什么、不是什么

- **是**：`openapi.json` —— 由 springdoc 从**运行中的后端**导出的一次**冻结快照**，用于设计门归档与"文档 vs 实现"的差异比对；
- **不是**手写的接口规范：契约的权威来源永远是运行中的服务（`GET /api/docs`，Swagger UI 在 `/api/docs/swagger-ui.html`）。本文与快照一旦落后于代码，以代码为准并**立即再生成**；
- **覆盖范围是**已实现的 **35 个端点**（30 条路径，本文口径为"**一个 operation 计一个端点**"，故路径数与端点数本就不同：`/api/v1/posts` 与 `/api/v1/posts/{postId}/replies` 各含 GET + POST，`/api/v1/posts/{id}` 含 GET + **DELETE**（[CR-065](../../变更日志/变更台账.md#cr-065)），`/api/v1/admin/{posts,replies}/{id}/status` 各 1 个 **PUT**（[CR-066](../../变更日志/变更台账.md#cr-066)），`/api/v1/users/me` 含 GET + **PUT**（[CR-071](../../变更日志/变更台账.md)）），账号 **7**（+1 个 PUT）+ 论坛 **15**（+2 个"我的"列表）+ **通知 3** + **处置 2**，〔v1.11 及之前本行为 **22 个端点 / 19 条路径**、v1.12 ~ v1.12.1 为 **24 个 / 21 条**，+2 来自 CR-066 两个受保护 PUT、+3 来自 CR-071〕不得读成"全部接口契约已归档"——[技术方案](../技术方案.md) §5 接口表中仍有多行属后续 Sprint 计划项（逐条差异见 §5）。因此技术方案文末检查清单第 3 项"接口契约**完整**"**仍不勾选**；
- **自 [CR-021](../../变更日志/变更台账.md#cr-021) 起，快照也表达错误响应与鉴权**：每个操作都声明了 4xx / 5xx 及错误体 schema `ApiError`，`components.securitySchemes.bearerAuth` 已定义，**13 个**受保护操作带 `security`（`users/me`、`admin/roster/import`、`POST /posts`、`POST /posts/{postId}/replies`、`POST /posts/{id}/accept`、**`DELETE /posts/{id}`（[CR-065](../../变更日志/变更台账.md#cr-065)）**、点赞 ×2、收藏、`favorites/mine`、**通知 ×3**（列表 / 未读数 / 全部已读，[CR-050](../../变更日志/变更台账.md) —— 前三个不属于账号或论坛上下文的受保护端点）。〔**2026-09-19 加注（[CR-071](../../变更日志/变更台账.md)）：上句的"13 个"是 v1.11 的数**——[CR-066](../../变更日志/变更台账.md#cr-066) 加两个处置 PUT → 15，本轮再加 **`PUT /users/me`（资料编辑）+ `GET /posts/mine` + `GET /replies/mine`** 三个 ⇒ **现为 18 个受保护操作**。三条新线各自的"仅本人"都**不靠机器强制**：G7 只保证"声明了 `@SecurityRequirement` 的方法真的调 `CurrentUser`"，而 `requireId` 拿到的 id 是否恰为资源所有者**没有守护测试**——本轮的防线是**接口形状**（三个端点**一律不接受身份参数**，作者 id 唯一来源是令牌），加上 `MyReplyControllerAuthTest` / `PostControllerAuthTest` 的匿名 401 用例。同类缺口已登记在问题清单 E-011（角色侧由 G9 处置，归属侧仍无机器强制），读 §6.2 〕**N-4 的"漏写鉴权静默变公开"缺口已由 [CR-028](../../变更日志/变更台账.md#cr-028) 闭环**（统一入口 `CurrentUser` + `ArchitectureGuardTest` 机器校验）；**路径级拦截已由 [CR-031](../../变更日志/变更台账.md#cr-031) 落地**（`SecurityConfig` 去 `permitAll()`，按端点注解裁决，见 §6.2），另有 4 类事实快照表达不了（§6）——读快照前必须先看 §6；
- **时间字段口径（[CR-051](../../变更日志/变更台账.md) 起，2026-09-15）**：快照里所有 `format: date-time` 的字段（`createdAt` / `updatedAt` 等）**值即为真 UTC**，`Z` 后缀可信，前端可直接 `new Date(...)` 后按本地时区展示。**实现口径**：字段类型为 `java.time.Instant`，序列化恒 UTC（JSR-310，与 `spring.jackson.time-zone` 无关）；**存储侧**由数据源 URL 的 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` **成对**把 MySQL 会话时区强制为 `+00:00`（否则 V1 各表的 `DEFAULT CURRENT_TIMESTAMP` 落的是本机本地墙钟，见 [技术方案](../技术方案.md) §4.1），由 `config/DataSourceTimezoneTest` 守护防回退。⚠️ **不受本条影响的字段**：`expiresAtEpochSecond`（整数 epoch 秒，由 `Instant.now().getEpochSecond()` 产出、不经数据库，L-6 期间也一直正确）。⚠️ **本条之前的历史实测值带 +8 小时偏移**（Sprint 2 起至 2026-09-14，台账外问题 **L-6**）——读 §4 各主链路实测响应时注意这一点，**存量开发数据已一次性 −8h 校正**，但**采集于修复前的文字与取值不回填改写**（保留"当时实测"的证据价值）；
- **不留多份历史副本**：本目录只有一个当前快照，历史版本由 git 承担（[CR-008](../../变更日志/变更台账.md#cr-008) 的教训——不该用 zip / 副本替代 git 历史）。

## 2. 快照来源与再生成

| 项 | 值 |
|---|---|
| 首次归档 | 2026-09-11（[CR-019](../../变更日志/变更台账.md#cr-019)，源提交 `68d40ae`，6 端点 / 13 schema） |
| **本次生成时间** | ✅ **2026-09-24（[CR-080](../../变更日志/变更台账.md) 列表点赞轮，同时补做 [CR-077](../../变更日志/变更台账.md) / [CR-078](../../变更日志/变更台账.md) 欠下的再生成）**——自**运行中的 8088 实例**（`mvn -o verify` 现场重建的 jar、跑工作区源码，`Tests run: 346` 全绿）抓 `http://localhost:8088/api/docs`，`servers` 本就是仓库既有值、**无需归一**（临时端口那套流程本轮用不上，8088 未被并发会话占用）。〔上一版 **2026-09-20**（**CR-074 C 档体验升级的「快照再生成」补做轮**——CR-074 落地时把再生成挂账，本轮由独立会话补做：**+4 个受保护 operation**（`POST` / `DELETE /api/v1/users/{targetId}/follow`、`GET /api/v1/users/{targetId}/follow-state` 三条 tag 为新开的 `follow`；`GET /api/v1/posts/following` **复用既有 `post` tag**）、**tag 首次由 9 增至 10**、`PostSummaryVo` 加 `viewCount` / `coverUrl`、`PostDetailVo` 加 `viewCount`。〔上一版 **2026-09-19**（**[CR-071](../../变更日志/变更台账.md) 个人中心第一轮三个 operation 落地后再生成：新增 1 个受保护 PUT + 2 个受保护 GET，24 → 27 操作、21 → 23 路径、41 → 48 schema、tag 仍 9、受保护 15 → 18；⚠️ 与 v1.11 / v1.12 不同，本轮**不是纯新增**——既有 `UserVo` 加 `status`、既有 `RegisterCommand.nickname` 加 `pattern`、既有 `GET /users/me` 的 `summary` 据实改写，见「本次 diff 性质」行**）。上一轮（v1.12.1，同日）是**非再生成**的「提交后核对」；再上一次生成是 2026-09-18 的 [CR-066](../../变更日志/变更台账.md#cr-066)（内容处置两个 PUT，22 → 24 操作、19 → 21 路径、40 → 41 schema、8 → 9 tag、受保护 13 → 15）。〔原句保留如下：（[CR-065](../../变更日志/变更台账.md#cr-065) 帖子删除端点落地后**再生成**：新增 1 个受保护 operation，21 → **22** 操作，路径 / schema / tag 数均不变，受保护 12 → **13**）。上一次再生成为 2026-09-16 07:56（[CR-058](../../变更日志/变更台账.md#cr-058) 热榜 `sort=hot`：**契约规模零变化**）；[CR-060](../../变更日志/变更台账.md#cr-060) 与 CR-051 同属**非再生成**（重抓逐字节一致，见本节末的核对行）。再上一次规模变化是 2026-09-14 22:46（[CR-050](../../变更日志/变更台账.md) 通知中心：18 → 21 操作、33 → 40 schema、7 → 8 tag、受保护 9 → 12）〕 |
| **代码来源** | ⚠️ **v1.15 的来源＝未提交的工作区**（CR-080 的后端 account + forum 两侧改动、契约与文档都在工作区，发起人尚未授权提交）⇒ **按本节纪律，提交后须自「已提交源码」重抓一次并逐字节比对**（先例 v1.5.1 ~ v1.13；上一版 v1.14 是唯一一次"抓取时代码已提交"、无此义务）。⚠️ **本轮还捎带补了一笔来源不明的账**：v1.14 快照里**没有** CR-077 / CR-078 的 4 个 operation，而这两轮的代码**已随 `b4ecb5e` 提交推送**——即"已提交的代码"与"已归档的快照"在 2026-09-21 ~ 09-24 之间**不一致**，本轮重抓使其对齐。〔上一版（v1.14）原文保留：✅ **v1.14 的来源＝已提交且已推送的树**——抓取时 `git status --porcelain backend/` 实测为空，且 `281bf04`（快照文件记录的当前树）＝`origin/main`；`git diff --numstat 51e8c6b 281bf04 -- backend/` 为空 ⇒ **`backend/` 树与收录 CR-074 代码的 `51e8c6b` 逐字节相同**（那次提交只动前端字体）⇒ **本仓历轮「提交后核对」义务在本轮不存在**：这是第一次「抓取发生在代码已提交之后」（此前 v1.11 ~ v1.13 都是先抓未提交的工作区、事后再补一次核对）。⚠️ 该结论有一个明确前提：**本轮提交不得改动 `backend/`**（快照文件自身仍是工作区改动，但它不是"代码来源"）；一旦后续批次改了 Controller / VO / 校验注解，义务立即重新挂上。〔上一版（v1.13）原文保留：⚠️ **未提交的工作区（CR-066）**——本次快照由本地 `backend/src/main` （含新建的 `AdminModerationController` 两个映射方法）跑出，CR-066 的代码与文档**均未提交**，故按本节既有纪律，**提交后须重抓一次核对**（先例：v1.5.1 / v1.6.1 / v1.7.1 / v1.8.1 / v1.9.1 / v1.10.1）。〔**v1.11 同行原文保留**：其时来源记为"未提交的工作区（CR-065 + CR-064）"，上一份可追溯到提交的快照来源为 `286739b`（CR-058 实现代码）、并由 `8b37194`（CR-060）二次复核为逐字节一致（60251 字节）。〕⚠️ **v1.11 那一轮的「提交后核对」至今未做**——CR-064 / CR-065 其后已随 **`8216843`** 入库并推送 origin/main，但没人自己提交源码重抓比对过，"v1.11 快照 = 已提交代码"这一条**目前是断言、不是实测**。**两条敞口可并一次闭环**：CR-066 提交后自己提交源码重抓，与仓库快照逐字节比对即可同时确认 v1.11 与 v1.12。方法学备注（沿用）：⚠️ 抓取须用 **`localhost`** 而非 `127.0.0.1`——springdoc 会把 `servers` 回显成请求 Host，那是**抓取方式**差异、不是契约差异（v1.8.1 已记过一次）。〔**✅ 2026-09-19 闭环（v1.12.1）**：本格上面那句"两条敞口可并一次闭环"**已履行**——**`f58ef73`** 同时含 CR-066 与 CR-068，自该提交重建 jar 起临时端口 **8097** 实例重抓、`servers` 归一后与仓库快照**逐字节一致**（**68470 字节 / sha256 `28327bc89910d9c1…19266cfb`**）⇒ **v1.11 与 v1.12 两版的代码来源自此都是提交、不再是断言**，取证细节见本节末「CR-066 + CR-068 提交后核对」行〕〔**⚠️ 2026-09-19 本轮（v1.13）来源又回到"未提交的工作区"**：[CR-071](../../变更日志/变更台账.md) 的 account + forum 两侧代码与 `V3` 迁移都在工作区，发起人尚未授权提交 ⇒ **按本节纪律，CR-071 提交后须自**已提交源码**重抓一次并逐字节比对**（先例 v1.5.1 ~ v1.12.1）。本轮抓取用的实例是 `mvn -o package` 现场重建的 jar（**同一次命令顺带复验 `Tests run: 294, Failures: 0, Errors: 0`**）、临时端口 **8096**、`http://localhost:8096/api/docs`（一律 `localhost`）〕〕 |
| 后端地址 | `http://localhost:8088`（开发端口，[CR-012](../../变更日志/变更台账.md#cr-012)）——快照 `servers` 字段即此值，**部署形态（ADR-011）确定后须重生成** |
| 生成方 | springdoc-openapi 3.1.0（`springdoc-openapi-starter-webmvc-ui`） |
| 规范版本 | OpenAPI **3.1.0**；`info.title` = `Campus-Link API`，`info.version` = `0.1.0`（未随 Sprint 递增，见 §7 遗留） |
| 规模 | ✅ **v1.15（2026-09-24）现行值：10 个 tag / 35 个端点（30 条路径）/ 54 个 schema / 24 个受保护操作**，文件 **99031 字节**（`sha256 997a2802d9f3e2a9…eef053e6`），diff **+435 / −31**。⚠️ **本版是两笔合并**：+4 operation（`GET /posts/similar`、`POST /notifications/{id}/read`、`GET /users/{id}`、`GET /users/{id}/posts`）**全部来自 [CR-077](../../变更日志/变更台账.md) / [CR-078](../../变更日志/变更台账.md)——那两轮改了契约却没重生成快照、也没补本文 §3**（`git show HEAD` 的快照至今停在 31 端点），本轮一并补上；[CR-080](../../变更日志/变更台账.md) 自己**只多一个字段**：`PostSummaryVo` 加 `likedByMe`，外加 `GET /posts` 与 `GET /users/me` 两行 `summary` 文案据实改写。**tag 数不变（10）**。〔v1.14 原句保留：**10 个 tag** / **31 个端点**（26 条路径）/ **50 个 schema** / **22 个受保护操作**，文件 **88437 字节**（`sha256 3e7f377c4b20f006…c579da71`）—— 与上一版 v1.13（9 tag / 27 端点 / 23 路径 / 48 schema / 18 受保护、79158 字节）相比 **+4 operation / +3 路径 / +2 schema / +1 tag / +4 受保护、零删除**；新增三条路径：`/api/v1/users/{targetId}/follow`（POST 与 DELETE 同路径、两个 operation）、`/api/v1/users/{targetId}/follow-state`（GET）、`/api/v1/posts/following`（GET）；两个新 schema：`FollowStateVo` 与其包装 `ApiResponseFollowStateVo`；`follow` 是本仓**第一个由"关系类资源"开出的 tag**（此前 9 个 tag 全按上下文 / 资源分）。〔上一版（v1.13）原文保留：**9 个 tag** / **27 个端点**（23 条路径）/ **48 个 schema** / **18 个受保护操作**，文件 **79158 字节**（`sha256 b76663ae8878e348…f6eac6e0237db6b8`）—— 与 v1.12 / v1.12.1（9 tag / 24 端点 / 21 路径 / 41 schema / 15 受保护，文件 68470 字节）相比 **+3 operation / +2 路径 / +7 schema / +3 受保护、tag 数不变**，全部来自 [CR-071](../../变更日志/变更台账.md) 个人中心第一轮：新路径 `/api/v1/posts/mine`（GET）与 `/api/v1/replies/mine`（GET），`/api/v1/users/me` 这一既有路径多出 1 个 PUT；7 个新 schema = `UpdateProfileCommand` + `MyPostSummaryVo` / `PageVoMyPostSummaryVo` / `ApiResponsePageVoMyPostSummaryVo` + `MyReplyVo` / `PageVoMyReplyVo` / `ApiResponsePageVoMyReplyVo`。⚠️ **tag 仍 9 不是 10**：新建的 `MyReplyController` 显式复用 `@Tag(name = "reply")`——"我的回帖"与"楼层回复"是同一族端点，**不因新开映射类而增计数**（这一条在立项时是预测、本轮实测确认）。tag 清单：`admin-moderation` / `admin-roster` / `auth` / `board` / `favorite` / `notification` / `post` / `reply` / `user`。〔v1.12 原句保留：9 tag / 24 端点（21 路径）/ 41 schema / 15 受保护，与 v1.11 相比 +2 operation / +2 路径 / +1 schema / +1 tag / +2 受保护，全部来自 CR-066 内容处置，文件 62546 → 68470 字节〕〕 |
| 落盘处理 | 原始响应单行压缩 → 按 2 空格缩进格式化。**键序保持 springdoc 原序，未排序、未删改任何字段**；本次自**临时端口 8097** 抓取（上一版是 8096，同一台机同一口径，端口按并发会话占用情况顺延）（实例是 `mvn -o package` 现场重建的**可执行 jar**、跑工作区源码；8088 / 8089 由并发会话占用、按惯例不动），故 `servers` 照 v1.6 / v1.6.1 / v1.7.1 / v1.10 / v1.11 / v1.12 先例**归一回仓库既有值** `[{"url": "http://localhost:8088", "description": "Generated server url"}]`，除此之外未改任何字段。⚠️ **抓取一律用 `localhost`**（本次即用 `http://localhost:8097/api/docs`）——springdoc 把 `servers` 回显成请求 Host，用 `127.0.0.1` 抓会得到 `http://127.0.0.1:8097`，那是抓取方式差异、归一时须一并处理；⚠️ **jar 新鲜度的判据本轮换了**：`mvn -o package` 在源码未变时**不会重写 jar**（`maven-jar-plugin` 无变更即跳过，实测 jar mtime 仍是上一次构建的时刻），所以"jar 时间戳晚于源文件"不再足以自证——改用**两条硬核对**：① `find backend/src -name "*.java" -newer <jar>` 为空；② 抓出的 `/api/docs` **确实含本次要收的 4 个 operation**（31 条，与代码侧映射方法数一致）。 |
| 可复现性 | 格式化方法与历次一致（`json.dumps(ensure_ascii=False, indent=2)` + 尾部换行），`git diff` 仍可作为契约变更的可靠信号 |
| 本次 diff 性质 | ✅ **v1.15：+435 / −31，非纯新增，31 行删除已逐行归因**。结构化比对（HEAD 版与工作区版各 `json.load` 后逐项比）给出的完整结论：**新增 4 个 operation / 4 个 schema、零删除 operation**；**既有 schema 只有 1 处字段变化**＝`PostSummaryVo` 多 `likedByMe`（13 个属性）；**既有 operation 只有 2 处 `summary` 文案改写**＝`GET /posts`（补"登录请求每条附带 `likedByMe`"，CR-080）与 `GET /users/me`（"他人主页在后续 Sprint" → "他人主页见 `GET /users/{id}`"，**这条是 CR-078 的欠账**，他人主页本轮已交付）。**−31 行的全部来源**（逐条比对删除行得出，非推断）：**2 行是被改写的 `summary`**（上列两处）；**其余 29 行是 schema 块整体换位**——`MyReplyVo` / `PageVoMyReplyVo` / `ApiResponsePageVoMyReplyVo` 三个块及其内部字段行（`total` / `size` / `page` / `postId` / `postTitle` / `status` / `properties` 等）因 springdoc **按引用顺序排 schema**、新增块插入后位置后移，旧位置的行遂呈现为删除。**没有任何既有字段被静默删除**（结构化比对已独立证明：schema 键集合零减少、`PostSummaryVo` 之外零内容差异）。⚠️ **本轮一度把这条归因写错**（误写成"`GET /posts` 与 `GET /users/{id}` 因新增 `2010` 而换位"——`GET /users/{id}` 是**新** operation、无所谓换位，`GET /posts` 也没有新增 `2010`），删除行逐条列出后即改正；**教训与 v1.13 那条同源：归因必须看着删除行本身写，不能看着块名猜**。判据沿用 v1.12 立的"删除行逐行归因 + 结构化比对"，不用"−0 即纯新增"那条已被证伪的粗判。⚠️ **一条 springdoc 表达极限本轮再次出现**：`likedByMe` 是 `boolean` 原始类型、非空，故它进 `required` 数组；但**"匿名恒 false"这条口径在快照里完全看不见**——契约只说"一定有这个字段"，不说"它的值取决于你有没有带令牌"。这类"形状正确、语义缺席"的字段，验收只能靠单测与真机（本轮三条：登录批量 / 匿名零调用 / 空页不查）。〔上一版（v1.14）原文保留：✅ **本轮接近纯新增**：`git diff --numstat` **+357 / −1**——**唯一一条删除行是 `GET /api/v1/posts/{id}` 的 `summary` 文案改写**（CR-074 把"阅读数按每帖每账号每日一次去重累加"补进摘要，属据实更新、不改接口语义），其余 357 行全是新增块（三条 follow operation + 一条 following operation + `FollowStateVo` / `ApiResponseFollowStateVo` + 两个 VO 的新字段块 + `tags` 数组一项）。**结构化比对复核**（HEAD 版与新版各 `json.load` 后逐项比）：既有 operation **仅 1 处被改写**（即上述 `summary`）、既有 schema **仅 2 处字段增加**（`PostSummaryVo` +`viewCount` +`coverUrl`、`PostDetailVo` +`viewCount`）、`GET /api/v1/posts` 的 `sort` 参数**一字未变**——因为「关注」Feed 落成了独立路径而不是 `sort=following`。⚠️ **一条 tag 口径**：`GET /posts/following` 复用既有 `post` tag，三条 follow 端点才用新开的 `follow` ⇒ **tag 数 +1 而非 +2**。〔上一版（v1.13）原文保留：⚠️ **本轮不是纯新增**：`git diff --numstat` **+462 / −49**。结构化比对（HEAD 版与工作区版各 `json.load` 后逐项比）给出的完整结论是——**新增** 3 个 operation / 7 个 schema / 0 删除；**既有 schema 有 2 处真实字段变化**：`UserVo` 多 `status`（11 个属性）、`RegisterCommand.nickname` 多 `pattern`（注册侧同批收紧字符集禁则，**这是对外行为收紧**：含尖括号的昵称自本 CR 起 `400 / 1001`）；**既有 operation 有 1 处 `summary` 据实改写**：`GET /users/me` 去掉"与资料编辑在后续 Sprint"那半句（资料编辑本轮已交付，留着就是过期声明）。**49 行删除逐行归因**：`UserVo` / `ApiResponseUserVo` / `GET /users/me` 三个块因上述改动**整体换位**（springdoc 按引用顺序排 schema），加上被改写的 `summary` 行与 `RegisterCommand.nickname` 行——**没有任何既有字段被静默删除**。⚠️ **另记一次自己引入、当场抓回并修掉的契约瑕疵**：`PUT /users/me` 的 `@ErrorCodes` 里显式写了 `INVALID_PARAM`，而 `OpenApiErrorResponseCustomizer` 本就自动补通用 400，首次抓取实测到 400 的 description 是 **`参数错误（1001）；参数错误（1001）`**——**这正是 v1.8 那轮记过的教训，隔了 5 个 CR 又被重犯一次**；删去显式声明后重编译重抓，归一为单条 `参数错误（1001）`。**新增的三个 operation**：`PUT /users/me`（`requestBody: UpdateProfileCommand` required、响应 `200 ApiResponseUserVo` + `400`·`401`·`403(2006 账号已被封禁)`·`404(2007)`·`429(2008)`·`500`、`security: [bearerAuth]`）、`GET /posts/mine` 与 `GET /replies/mine`（各 2 个 query 参数 `page` / `size`、响应 `200 ApiResponsePageVo{MyPostSummaryVo,MyReplyVo}` + `400`·`401`·`500`、`security: [bearerAuth]`）。⚠️ **`405` / `415` / 路由级 `404`（`1004`）三类仍不在快照里**，与本文 §6.3 一致，不是本次新增的缺口。〔**v1.12 原句保留（当时的判定）**：+232 / −16，那 16 行删除不是任何既有字段被改写，而是 `ApiResponseVoid` 一个 schema 块整体换位；机器核对方式为 40 个既有 schema 逐一 `sort_keys` 序列化后 byte-identical、既有 operation 零变化、schema 键集合只多 `ModerationCommand` ⇒ 判为纯新增。**教训沿用**："−0 即纯新增"这条判据在 springdoc 重排 schema 时失效，一律改判为"**删除行逐行归因 + 结构化比对**"——本轮更进一层：**结构化比对不能只看 operation 增删，必须逐 schema 比字段**，否则 `UserVo` 加字段这种"既有 schema 变化"会被"纯新增"的粗判吞掉〕〕 |
| **CR-051 重抓核对（2026-09-15）** | [CR-051](../../变更日志/变更台账.md) 只改数据源 URL 的时区参数、不动 Controller / VO / `ResultCode` / 鉴权声明，故按本节命令起后端重抓后 **`git diff --numstat openapi.json` 为空**（59977 字节，逐字节一致）——**该方案的验收点之一即"快照 diff 必须为空"**，非空就说明意外改到了接口形状。⚠️ 注意快照的**值**里没有时间戳，diff 为空也**不代表时间口径未变**：时间口径的变更只体现在 §1 的口径条与响应体的实际取值上，`Z` 由"假 UTC"变成真 UTC 是**数据层**变化、不是**契约层**变化，这正是本行要写清的原因。**快照代码来源仍为提交 `70c6ca9`**（CR-051 尚未提交，但其生产代码改动不影响快照内容）——〔2026-09-18 注：该来源已随 `286739b` 入库并被 `8b37194` 复核，见下面两行〕 |
| **CR-058 提交后核对（2026-09-18）** | CR-058 已随 **`286739b`** 提交（六条 CR 合一，理由见该提交信息）。自**运行中的已提交代码**（临时端口 **8097**、`APP_ROSTER_BYPASS=false`，实例由 `target/` 下 13:12 构建的 jar 启动，该 jar 晚于全部 java 源文件且 `git status --porcelain -- backend/src` 为空 ⇒ **跑的就是 `8b37194` 的源码**）重抓 `/api/docs`，按 §2 命令格式化、`servers` 归一回 8088 后与仓库快照**逐字节一致**（60251 字节）——CR-058 遗留的"提交后核对"就此完成，快照自此可追溯到提交。 |
| **CR-060 提交后核对（2026-09-18）** | [CR-060](../../变更日志/变更台账.md#cr-060) 只把两条楼层写用例的读侧换成带 `status` 过滤的 `findVisibleById`，**不动 Controller / VO / `ResultCode` / 鉴权声明**，故预期与实测均为**零 diff**：与上一行同一次抓取（同一实例、同一份 jar）**sha256 相同**（`c3116d02bc76…`）、`git diff --numstat openapi.json` **为空**、规模仍 8 tag / **19 路径 / 21 操作 / 40 schema / 12 受保护**。**该方案的验收点之一即"快照 diff 必须为空"**，非空就说明意外改到了接口形状。⚠️ 同一次抓取还证实一件与快照无关的事实：CR-058 遗留的 **8095 临时实例（PID 28436 / maven 13524）此刻已不在监听**（8088 / 8089 / 8095 全部无监听），那条待清理遗留可视为已消解。 |
| **CR-065 再生成（2026-09-18）** | [CR-065](../../变更日志/变更台账.md#cr-065) 帖子删除是**新增一个映射方法**（`PostController#delete`），命中 §2「再生成触发点」第 ① 条（高频），故**必须再生成**、不是核对。**实例**：`target/` 下当日新构建的 jar，临时端口 **8099**、`APP_ROSTER_BYPASS=false`（8088 / 8089 由其他会话占用，未动）。**产物**：`git diff --numstat` **+85 / −0**、规模 8 tag / **19 路径 / 22 操作 / 40 schema / 13 受保护**、文件 62546 字节。⚠️ **敞口如实登记**：来源是**未提交的工作区**（CR-064 + CR-065 的代码都在其中），按本节纪律**提交后须自己提交代码重抓一次并核对逐字节一致**——该核对**本轮未做**（发起人尚未授权提交），先记为待办，比照 v1.5 / v1.6 / v1.7 / v1.8 / v1.9 / v1.10 各条的后续闭环。 |
| **CR-066 再生成（2026-09-18）** | [CR-066](../../变更日志/变更台账.md#cr-066) 内容处置「下架 / 恢复」是**新增两个映射方法**（`AdminModerationController#moderatePost` / `#moderateReply`），命中 §2「再生成触发点」第 ① 条（高频）与第 ② 条（受保护声明新增），故必须再生成。**产物**：8 tag → **9**（新 tag `admin-moderation`）、19 路径 → **21**、22 操作 → **24**、40 schema → **41**（新 `ModerationCommand`）、受保护 13 → **15**、62546 → **68470 字节**。**diff 判定**：`git diff --numstat` **+232 / −16**——⚠️ 删除行**不为零，但既有的"−0 即纯新增"判据本轮失效**：两个新 PUT 也引用 `ApiResponseVoid`，springdoc 的 schema 顺序随之重排，那 16 行是该 schema 块整体换位。改用**结构化比对**判定（`json.load` 两个版本后逐项比）：**40 个既有 schema 逐一序列化后 byte-identical、既有 operation 零变化、schema 键集合只多 `ModerationCommand`** ⇒ 仍判**纯新增**。**抓取方式**：8088 / 8089 由并发会话占用，本轮在**临时端口 8096** 用 `mvn spring-boot:run` 跑工作区源码（取证用 `HOT_REFRESH_INTERVAL_MS=20000`，只被 `@Scheduled` 消费、不进契约），`servers` 照 v1.6 / v1.7.1 / v1.10 / v1.11 先例逐字归一回 8088。⚠️ **快照表达不出处置语义**：出参是空的 `ApiResponseVoid`，schema 里没有 `is_deleted`、也没有"这是平台处置不是作者删除"的任何痕迹——两列两义、幂等零审计、恢复可逆三条只能在 §3.4 两行与[实施方案 §7](../../开发/实施方案/CR-066-内容处置下架与恢复.md)里读。⚠️ **来源仍是未提交的工作区**，且 **v1.11 那一轮的「提交后核对」也未做**——两条敞口并成一次：CR-066 提交后自己提交源码重抓、与本次产物比对即可一并闭环。**未做项**：本次未新增 §4 主链路实测小节（真机 45 项取证在实施方案 §7）。 |
| **CR-066 + CR-068 提交后核对（2026-09-19）** | ✅ **v1.11 + v1.12 两轮遗留一并闭环，`openapi.json` 文件零改动**。**前提成立**：发起人把 **CR-066（内容处置）+ CR-067（草案文档）+ CR-068（G9）合并提交为 `f58ef73` 并推送 origin/main**，`git status --porcelain` 为空 ⇒ **工作区即已提交源码**。**做法**：`mvn -o package` 从该树重建（**`Tests run: 249, Failures: 0, Errors: 0`** 同时复验），再起**可执行 jar**、临时端口 **8097**、`APP_ROSTER_BYPASS=false`（8088 / 8089 无监听、按惯例不动他人端口），用 `http://localhost:8097/api/docs` 抓取（**一律 `localhost`**，见上条方法学备注）。**结果**：抓取的 `servers` 回显 `http://localhost:8097` —— 归一回仓库既有值后与 `docs/设计/接口契约/openapi.json` **逐字节一致**：文件 **68470 字节**、`sha256 28327bc89910d9c13ce7b38f4656f4276c4ce09ebd79a272739f253e19266cfb`、规模 **21 路径 / 24 操作 / 41 schema / 9 tag**，`git diff` **为空**。**顺带核掉 v1.11**：`8216843`（CR-064 + CR-065）从未被复核，而它的代码**已完整包含在 `f58ef73` 的祖先链里**，同一实例、同一份产物的逐字节一致 ⇒ **v1.11 那版的"快照 = 已提交代码"自此也是实测、不再是断言**（口径如实写明：**这不是单独跑了一次 v1.11**，而是"当前树含 v1.11 全部改动且产物与快照一致"这条推论）。**外部行为冒烟（顺带，非本轮验收点）**：`/actuator/health` 200、匿名 `GET /posts` 200、匿名 `?sort=hot` 200、匿名 `POST /posts` `401 / 4001`、匿名 `PUT /api/v1/admin/posts/1/status` `401 / 4001`、匿名 `POST /api/v1/admin/roster/import` `401 / 4001`。**收尾**：8097 实例已 `taskkill` 停止（本机 Redis 按 AGENTS 惯例保留在跑）。**残余一条如实登记**：`openapi.json` 无机器校验（[AGENTS.md](../../../AGENTS.md)「测试约定」明写"漂移只能靠这条约定拦"），本轮**只能证明"抓取时点的工作区 == 快照"**，**不能保证下一次改 Controller 时会记得重抓**——把快照纳入机器校验（CI 起栈比对）仍属提测准入门欠账（W-05 无截止日期）。 |
| **CR-071 再生成（2026-09-19）** | [CR-071](../../变更日志/变更台账.md) 个人中心第一轮是**新增三个映射方法**（`UserController#updateProfile` / `PostController#myPosts` / `MyReplyController#myReplies`）+ **改两个既有 VO / Command 的字段**，命中 §2「再生成触发点」第 ① 条（高频）、第 ② 条（受保护声明新增）与第 ⑤ 条（新增 `ResultCode` 2008/429），故必须再生成。**实例**：`mvn -o package` 现场重建的 jar（同一次命令复验 **294 全绿**）、临时端口 **8096**、`APP_ROSTER_BYPASS` 保持开发默认（本轮取证需要 45 个开发账号）、`http://localhost:8096/api/docs`。**产物**：9 tag（**未增**）/ 21 → **23 路径** / 24 → **27 操作** / 41 → **48 schema** / 受保护 15 → **18**、68470 → **79158 字节**（+10688）、`sha256 b76663ae8878e348…`。**diff 判定**：**非纯新增**（详见「本次 diff 性质」行——`UserVo` 加 `status`、`RegisterCommand.nickname` 加 `pattern`、`GET /users/me` 的 `summary` 据实改写）。⚠️ **快照表达不出的三件事**（读形状不等于读语义）：① `PUT /users/me` 的"传 `null` 不改、传空串清空、全等重放零 UPDATE 零审计"在 schema 里**完全没有痕迹**（`UpdateProfileCommand` 三字段一律非 required，看不出 PUT 全量语义）；② `MyPostSummaryVo.status` 只是 `type: string`，**没有 `enum`**（后端是 `PostStatus` 枚举，springdoc 未导出取值域），"REMOVED 条目前端不可点"只能在 §3.2 该行与[实施方案 §8](../../开发/实施方案/CR-071-个人中心第一轮.md) 里读；③ **昵称的字符集禁则根本进不了快照**（可重复 `@Pattern` 被 springdoc 丢弃，见 §6.3 新增行）。**未做项**：本轮未新增 §4 主链路实测小节（真机取证逐条记在实施方案 §8），**来源是未提交的工作区 ⇒ 「提交后核对」待 CR-071 提交后补做**。 |

**再生成命令**（在仓库根目录执行；先按 `AGENTS.md`"常用命令"起栈：本机 MySQL / Redis + `mvn spring-boot:run`）：

```bash
curl -s http://localhost:8088/api/docs -o /tmp/openapi.json
python -c "import json;d=json.load(open('/tmp/openapi.json',encoding='utf-8'));open('openapi.json','w',encoding='utf-8',newline='\n').write(json.dumps(d,ensure_ascii=False,indent=2)+'\n')"
git diff --stat openapi.json   # 非空即契约有变更，须同步技术方案 §5
```

**再生成触发点**（已写入 `AGENTS.md`）：① 新增 / 修改端点或请求响应模型后；② 每个 Sprint 收尾；③ 部署形态确定后（`servers` 会变）；④ ~~N-1 / N-2 修复后~~ **已完成**（[CR-021](../../变更日志/变更台账.md#cr-021)）；⑤ 改了 `common/result/ResultCode` 的错误码、提示语或 `getHttpStatus()` 映射后——契约里的状态码与 description 全部由它派生，改它即改契约；⑥ ~~`SecurityConfig` 从 `permitAll()` 改为真正按路径授权后~~ **已完成**（[CR-031](../../变更日志/变更台账.md#cr-031)，2026-09-12 再生成：`security` 声明未变，`bearerAuth` 描述已写明"框架按端点注解裁决、未登录由过滤器链产出 401 / 4001"）；⑦ ~~Sprint 2 MVP 的 6 个论坛端点合入前~~ **已完成**（[CR-022](../../变更日志/变更台账.md#cr-022)，2026-09-12 再生成）；⑧ 增删端点的 `@PublicEndpoint` / `@SecurityRequirement` 声明、或改动 `common/web/CurrentUser` 鉴权入口语义后（[CR-028](../../变更日志/变更台账.md#cr-028) 起该纪律由 `ArchitectureGuardTest` 机器校验，但**快照的 `security` 声明不会自动跟随**，仍须重抓）；⑨ ~~N-4 闭环后复核 `bearerAuth` 描述~~ **已完成**（[CR-028](../../变更日志/变更台账.md#cr-028)，2026-09-12 再生成，diff 仅 1 行文案）。**新增**：⑩ 改动 `security/EndpointAuthorizationManager` 的裁决口径（如 fail-closed 范围、非 module 处理器放行规则）后——**运行时行为变了而契约描述可能落后**，须重抓并核对 `bearerAuth` 描述。**其中 ① 是高频触发点**：Sprint 2 起每次加端点都要重抓一次，`git diff` 非空即须同步本文 §3 / §4 与技术方案 §5。

## 3. 端点清单（快照实际覆盖的 31 个）

> 计数口径：**一个 operation 计一个端点**，同路径的不同方法分别计数。分项：§3.1 账号 7 + §3.2 论坛 16 + §3.3 通知 3 + §3.4 处置 2 + §3.5 关注 3 = **31**（上一版 27；本轮 +4 全部来自 [CR-074](../../变更日志/变更台账.md) 补做）。

### 3.1 账号上下文（`module/account`，8 个，Sprint 1 + [CR-071](../../变更日志/变更台账.md) 资料编辑 + [CR-078](../../变更日志/变更台账.md) 他人主页；关注的 3 个单列在 §3.5）

| 方法 | 路径 | tag | 摘要 | 请求体 | `data` 类型 | **快照声明的鉴权** | **运行时实际强制方式**（见 N-4） |
|------|------|-----|------|--------|------------|------------------|--------------------------------|
| POST | `/api/v1/auth/verify-student` | auth | 学籍核验（F-ACC-004），通过返回一次性票据 | `VerifyStudentCommand` | `VerifyStudentResult` | 无（公开） | 公开（另有 IP 限流） |
| POST | `/api/v1/auth/captcha` | auth | 发送邮箱验证码（60s 重发间隔，单账号日上限 10 条） | `CaptchaCommand` | 无（`data` 省略） | 无（公开） | 公开（另有 IP / 账号限流） |
| POST | `/api/v1/auth/register` | auth | 注册，需先通过学籍核验并携带票据 | `RegisterCommand` | `AuthResponse` | 无（公开） | 公开 |
| POST | `/api/v1/auth/login` | auth | 登录：邮箱 + 验证码 | `LoginCommand` | `AuthResponse` | 无（公开） | 公开 |
| GET | `/api/v1/users/me` | user | 我的主页（F-ACC-002 最小版）；〔v1.13 注：出参 `UserVo` 自 [CR-071](../../变更日志/变更台账.md) 起多一个 `status`，本行 `summary` 也随之去掉"资料编辑在后续 Sprint"那半句——**这是本轮唯一的既有 operation 文案变化**〕 | — | `UserVo` | **`security: bearerAuth`** | **框架层（[CR-031](../../变更日志/变更台账.md#cr-031)）按 `@SecurityRequirement` 前置拦截 → 匿名 `401 / 4001`（进入业务代码前）**；业务层 `CurrentUser.requireId(authentication)` 同码兜底 |
| PUT | `/api/v1/users/me` | user | **修改我的资料（F-ACC-007a）**：昵称 / 专业 / 签名；**PUT 全量语义**——字段传 `null` 即不改、传空串即清空（昵称除外）；成功回**全量 `UserVo`**（前端不必二次拉取） | `UpdateProfileCommand` | `UserVo` | **`security: bearerAuth`** | 框架层拦匿名 → `401 / 4001`；业务层 `CurrentUser.requireId`。**⚠️ 快照表达不出的四条语义**：① **定向单列 UPDATE**（只 SET 变化列，绝不整行回写 `role` / `status` / `email_*`）；② **全等重放幂等**（零 UPDATE、`updated_at` 不推进、**不记审计**，但**仍消耗配额**）；③ 状态门 `BANNED → 403 / 2006`；④ 限流 **10 次/小时 → `429 / 2008`**（判据 `hits > 10`，第 11 次才拒）。逐条见[实施方案](../../开发/实施方案/CR-071-个人中心第一轮.md) §8 与真机取证 |
| GET | `/api/v1/users/{id}` | user | **用户公开资料（[CR-078](../../变更日志/变更台账.md) 功能D，他人主页 `/u/:id` 的数据源）**：昵称 / 专业 / 签名 / 加入时间 + `postCount` / `followerCount` / `followingCount` | — | `UserProfileVo` | 无（公开） | 公开（**按 IP 限流 30 次/分钟 → `429 / 2010`**）；用户不存在或已注销 `404 / 2007`，**不区分两者**（否则可用 2007 与 404 的差异枚举哪些账号被注销）。⚠️ **`postCount` 是"可见帖数"不是"发帖数"**：口径与 `GET /users/{id}/posts` 同源（`status=PUBLISHED` 且非墓碑），所以作者被下架的帖不计数 |
| POST | `/api/v1/admin/roster/import?batch=` | admin-roster | 学籍名册 CSV 导入，body 为 CSV 文本 | `string`（CSV） | `RosterImportResult` | **`security: bearerAuth`** | 框架层前置拦截匿名 → `401 / 4001`；业务层 `CurrentUser.requireRole(authentication, ROLE_SUPERADMIN)` → 角色不足 `403 / 4002`（**403 支由单测 `AdminRosterControllerAuthTest` 覆盖，未在真机复现**，见 §6.2） |

### 3.2 论坛上下文（`module/forum`，18 个：Sprint 2 MVP 6 个 + [CR-043](../../变更日志/变更台账.md) 采纳 1 个 + [CR-048](../../变更日志/变更台账.md) 点赞收藏 4 个 + [CR-049](../../变更日志/变更台账.md) 站内搜索 1 个 + [CR-065](../../变更日志/变更台账.md#cr-065) 帖子删除 1 个 + [CR-071](../../变更日志/变更台账.md) 两个"我的"列表 2 个 + [CR-074](../../变更日志/变更台账.md) 「关注」Feed 1 个 + [CR-077](../../变更日志/变更台账.md) 相似帖 1 个 + [CR-078](../../变更日志/变更台账.md) 他人主页帖子流 1 个）

| 方法 | 路径 | tag | 摘要 | 请求体 | `data` 类型 | **快照声明的鉴权** | **运行时实际强制方式**（见 N-4） |
|------|------|-----|------|--------|------------|------------------|--------------------------------|
| GET | `/api/v1/boards` | board | 版块列表（6 个启用版块，按 `sort` 升序，**不分页**） | — | `List<BoardVo>` | 无（公开） | 公开 |
| GET | `/api/v1/posts?boardCode=&sort=&page=&size=` | post | 帖子列表：`sort=latest`（**缺省**，`created_at DESC`）或 `hot`（`hot_score DESC, created_at DESC, id DESC`，**热榜，[CR-058](../../变更日志/变更台账.md#cr-058)**）；带 `boardCode` 则版块内（可与 `sort` 组合 = 版块内热榜），不带则全站。**`sort` 非法值 → `400 / 1001`**（与同端点族 `days` 只受理 7/30/90 同口径，不静默归一）；出参**不含 `hotScore`**（无产品需求，前端只消费顺序）；〔**[CR-080](../../变更日志/变更台账.md) 注**：本行鉴权一栏的"公开"含义不变，但端点现在**接受可选身份**——带令牌的请求每条 `PostSummaryVo` 多出 `likedByMe`（当前账号是否已赞），匿名恒 `false`。⚠️ **这不是把公开端点改成受保护**：`@PublicEndpoint` 一字未动、匿名仍 `200`，`EndpointAuthorizationManager` 的裁决也不看令牌。同口径的既有先例是 `GET /posts/{id}` 的 `likedByMe` / `favoritedByMe` 与楼层列表的 `likedByMe`。"可选身份"这条判定原先在 `PostController` 与 `ReplyController` 各有一份私有 `viewerIdOf`，CR-080 起收敛为 `CurrentUser.optionalId` 单点〕 | — | `PageVo<PostSummaryVo>` | 无（公开） | 公开（**`@PublicEndpoint` 未变**：热榜是公开读，加参数不改鉴权口径；匿名 `?sort=hot` 真机实测 200） |
| GET | `/api/v1/posts/search?keyword=&boardCode=&days=&page=&size=` | post | **站内搜索（F-FORUM-008，[CR-049](../../变更日志/变更台账.md)）**：`keyword` **必填、2~50 字**，匹配标题（MySQL FULLTEXT + **ngram** 分词）与 `tags`（`LIKE` 兜底）；`boardCode` 可选筛版块（不存在 → `404 / 3001`）；`days` 可选且**只受理 7 / 30 / 90**；排序 **`MATCH` 相关度 DESC → `created_at DESC` → `id DESC`**；只搜 `PUBLISHED` 且未删除 | — | `PageVo<PostSummaryVo>` | 无（公开） | 公开（匿名可搜，[CR-049](../../变更日志/变更台账.md) 真机实测 200）。⚠️ **`keyword` 下限 2 字不是随意取值**：MySQL `ngram_token_size` 默认 **2**，单字关键词分不出词、全文索引恒不命中，故应用层直接拒为 `400 / 1001`（前端同口径先拦一次）；**改 `ngram_token_size` 须同步改这条下限** |
| POST | `/api/v1/posts` | post | 发帖（MVP 范围：`boardCode` + `title` + `contentMd`，不含标签） | `PublishPostCommand` | `PublishedPost` | **`security: bearerAuth`** | **框架层（[CR-031](../../变更日志/变更台账.md#cr-031)）前置拦截 → 匿名 `401 / 4001`（§4.5 ⑫ 实测）**；业务层 `CurrentUser.requireId(authentication)` 同码兜底 |
| GET | `/api/v1/posts/{id}` | post | 帖子详情，返回服务端渲染好的 `contentHtml` | — | `PostDetailVo` | 无（公开） | 公开 |
| DELETE | `/api/v1/posts/{id}` | post | **删除自己的帖子（F-FORUM-006，[CR-065](../../变更日志/变更台账.md#cr-065)）**：**软删**——写行级墓碑 `posts.is_deleted=1`（**零迁移**，V1 已有该列），不物理删行、不级联删楼层与 `likes`/`favorites` 行；同时记 `POST_DELETE` 审计（`detail` 只含 `boardId` 与截断标题，**不含正文**）；同一事务 | — | 无（`ApiResponseVoid`，`data` 省略） | **`security: bearerAuth`** | **框架层前置拦截 → 匿名 `401 / 4001`**（真机实测）；业务层 `CurrentUser.requireId` + **归属校验在业务层**（非作者 `403 / 4002`，[CR-065](../../变更日志/变更台账.md#cr-065) 真机实测）；**帖不存在 / 已删 / 已下架三类一律 `404 / 3001`**（防探测，且审计不重复记） |
| GET | `/api/v1/posts/{postId}/replies?page=&size=` | reply | 楼层列表，**最佳答案置顶**（`is_accepted DESC, floor_no ASC`，[CR-043](../../变更日志/变更台账.md) 调整） | — | `PageVo<ReplyVo>` | 无（公开） | 公开 |
| POST | `/api/v1/posts/{postId}/replies` | reply | 回帖（MVP 范围：平铺楼层，不含引用回复） | `PublishReplyCommand` | `PublishedReply` | **`security: bearerAuth`** | **框架层（[CR-031](../../变更日志/变更台账.md#cr-031)）前置拦截 → 匿名 `401 / 4001`（§4.5 ⑫ 实测）**；业务层 `CurrentUser.requireId(authentication)` 同码兜底 |
| POST | `/api/v1/posts/{id}/accept` | post | 采纳最佳答案（仅提问者 + 问答帖 + 非自答；可更换，后写覆盖前写） | `AcceptReplyCommand` | 无（`data` 省略） | **`security: bearerAuth`** | **框架层（[CR-031](../../变更日志/变更台账.md#cr-031)）前置拦截 → 匿名 `401 / 4001`**；业务层 `CurrentUser.requireId(authentication)`；**提问者 / 问答帖 / 非自答校验在业务层**（403 `4002` / 400 `3002` / 400 `3003` / 404 `3001` 防探测，[CR-043](../../变更日志/变更台账.md) 真机实测） |
| POST | `/api/v1/posts/{id}/like` | post | 点赞 / 取消点赞帖子（toggle，再次 POST 即取消） | — | `InteractionResult`（`{active, count}`） | **`security: bearerAuth`** | **框架层前置拦截 → 匿名 `401 / 4001`**（[CR-048](../../变更日志/变更台账.md) 真机实测）；业务层 `CurrentUser.requireId(authentication)`；目标不存在 `404 / 3001` |
| POST | `/api/v1/posts/{id}/favorite` | post | 收藏 / 取消收藏帖子（toggle，同上） | — | `InteractionResult` | **`security: bearerAuth`** | 同上（[CR-048](../../变更日志/变更台账.md)） |
| POST | `/api/v1/posts/{postId}/replies/{replyId}/like` | reply | 点赞 / 取消点赞楼层（toggle，同上） | — | `InteractionResult` | **`security: bearerAuth`** | 同上（[CR-048](../../变更日志/变更台账.md)；楼层不存在同 `404 / 3001` 防探测） |
| GET | `/api/v1/favorites/mine?page=&size=` | favorite | 我的收藏（按收藏时间倒序分页，出参同帖子摘要） | — | `PageVo<PostSummaryVo>` | **`security: bearerAuth`** | **框架层前置拦截 → 匿名 `401 / 4001`**；业务层 `CurrentUser.requireId(authentication)`（[CR-048](../../变更日志/变更台账.md)） |
| GET | `/api/v1/posts/mine?page=&size=` | post | **我的帖子（F-ACC-007b，[CR-071](../../变更日志/变更台账.md)）**：本人发帖时间倒序；**含被平台下架的条目（`status=REMOVED`）、不含自己删除的**（墓碑过滤）；**无身份参数**，作者 id 只从令牌取 | — | `PageVo<MyPostSummaryVo>`（**新建 VO，与 `PostSummaryVo` 不同形**：多 `status`、少 `replyCount` / `likeCount` / `accepted` / `authorNickname` / `boardName`） | **`security: bearerAuth`** | 框架层拦匿名 → `401 / 4001`；业务层 `CurrentUser.requireId`。⚠️ **`status` 在快照里只是 `type: string`、没有 `enum`**，"REMOVED 前端渲染已下架且不可点"是页侧约定（无障碍树实测该标题为 StaticText 而非 link，见实施方案 §8）；"仅本人"**无机器强制**，靠**无身份参数**这一形状保证 |
| GET | `/api/v1/posts/following?page=&size=` | **post**（复用既有 tag，未新开） | **「关注」Feed（[CR-074](../../变更日志/变更台账.md)：把 F-SOC-002 的关注流提前单独落地）**：登录者所关注作者的帖子时间序（`created_at DESC, id DESC`），出参与全站列表**同形**（`PageVo<PostSummaryVo>`，含本轮新增的 `viewCount` / `coverUrl`）；**未关注任何人 → `200` 空页**（前端给"去关注"空态，不报错）；⚠️ **落成独立路径而不是 `?sort=following`**——既有 `GET /posts` 的 `sort` 仍只受理 `latest` / `hot`，一字未改 | — | `PageVo<PostSummaryVo>` | **`security: bearerAuth`** | 框架层拦匿名 → `401 / 4001`；业务层 `CurrentUser.requireId`。**跨上下文方向**：关注 id 列表由 forum 的 application 调 **account 的 application**（`FollowApplicationService.followingIds`），与昵称解析同路，符合 ADR-012 与守护测试 G4。⚠️ **两条快照表达不出的口径**：① **作者数上限 500**（`FOLLOWING_FEED_AUTHOR_CAP`，超出静默截断、不告知前端）；② 可见性口径同全站列表（`status=PUBLISHED` 且非墓碑），与 §3.2 上方 `GET /posts/mine` 那行的"我的视角"**口径相反** |
| GET | `/api/v1/replies/mine?page=&size=` | **reply**（复用既有 tag，未新增） | **我的回帖（F-ACC-007c，[CR-071](../../变更日志/变更台账.md)）**：本人楼层时间倒序，条目带 `postId` + `postTitle` + `floorNo` 定位；**父帖不可见（作者自删或被下架）的楼层整条不出现**（SQL 侧 `JOIN posts` 与 `total` 同源）；**新建映射类** `MyReplyController`（`ReplyController` 的类级前缀挂不进跨帖资源） | — | `PageVo<MyReplyVo>`（**不下发 `contentMd` 原文**，只给 `summary`） | **`security: bearerAuth`** | 框架层拦匿名 → `401 / 4001`；业务层 `CurrentUser.requireId`。⚠️ **父帖可见性判定在 SQL 里、不在 Java 里** ⇒ Mockito 结构上捕不到，该条件由 `ReplyRepositoryImplMyRepliesTest` **读 `@Select` 注解文本**断言（反证实测：删掉两条 `p.` 条件即该用例红）；"列出来却点不开"这一缺陷模式因此在本链路结构上不可能发生 |

| GET | `/api/v1/posts/similar?title=` | post | **相似帖子推荐（[CR-077](../../变更日志/变更台账.md)，发帖页"也许已有答案"）**：按标题做 MySQL ngram 全文检索，最多 3 条，条目带 `[已采纳]` 与回复数 | — | `List<SimilarPostVo>` | **`security: bearerAuth`** | 框架层拦匿名 → `401 / 4001`；业务层 `CurrentUser.requireId`。⚠️ **纯检索、零 AI**：`campuslink.ai.similar.enabled` 关掉即整条链路退化为"不推荐"，不引入第三方服务商（评审纪要判 F-AI-001 只有纯检索版可排期的落点） |
| GET | `/api/v1/users/{id}/posts?page=&size=` | **post**（复用既有 tag，映射类是 forum 侧新建的 `UserPostController`） | **某人的公开帖子时间线（[CR-078](../../变更日志/变更台账.md) 功能D，他人主页下半屏）**：出参与全站列表**同形**（`PageVo<PostSummaryVo>`），前端因此复用同一个 `PostListItem` | — | `PageVo<PostSummaryVo>` | 无（公开） | 公开（按 IP 限流 30 次/分钟 → `429 / 2010`）；用户不存在 / 已注销 `404 / 2007`，判定在**应用层**（控制器不二次判）。⚠️ **与 `GET /posts/mine` 口径相反**：本条是路人视角（下架的一律不出现），那条是作者视角（下架的要出现并带 `status`）——两条走仓储的 `findPageByAuthors`（复数）与 `findPageByAuthor`（单数），**接错一个就是把平台已处置内容重新公开**。〔**CR-080 注**：本行现在也接受可选身份以回显 `likedByMe`，同 `GET /posts`〕 |

> ⚠️ **路径参数名不一致（真实存在）**：详情是 `/posts/{id}`（`PostController`），回复列表与回帖是 `/posts/{postId}/replies`（`ReplyController` 类级 `@RequestMapping`）。两者指同一个帖子 id，仅是**两个 Controller 各自的参数命名不同**，快照如实呈现。前端调用不受影响（URL 模板里只是占位符），但**读快照时不要以为是两个不同的资源**。属文档-代码细节差异，登记在 [增量设计](../../开发/Sprint2增量设计.md) §8（P-2）。
>
> ⚠️ **`accepted` 而非 `isAccepted`**：[增量设计](../../开发/Sprint2增量设计.md) §3.4 写的字段名是 `isAccepted`，**落码实际为 `accepted`**（Java record 组件名 `accepted` → Jackson 序列化即 `accepted`，无 `is` 前缀）。**以快照为准**，前端已按 `accepted` 对接；设计文档已同步修正（[增量设计](../../开发/Sprint2增量设计.md) §8 的 P-1）。

### 3.3 通知上下文（`module/notification`，4 个，[CR-050](../../变更日志/变更台账.md) / F-SOC-001 + [CR-078](../../变更日志/变更台账.md) 单条已读）

| 方法 | 路径 | tag | 摘要 | 请求体 | `data` 类型 | **快照声明的鉴权** | **运行时实际强制方式**（见 N-4） |
|------|------|-----|------|--------|------------|------------------|--------------------------------|
| GET | `/api/v1/notifications?unread=&page=&size=` | notification | 我的通知列表，按 `created_at DESC` 分页；`unread` **三态**：缺省=不限、`true`=只未读、`false`=只已读（`page` 默认 1、`size` 默认 20） | — | `PageVo<NotificationVo>` | **`security: bearerAuth`** | **框架层前置拦截 → 匿名 `401 / 4001`**（§4.7 实测）；业务层 `CurrentUser.requireId(authentication)` 同码兜底。⚠️ **`unread` 非数字（如 `maybe`）在匿名时仍是 `401`**（框架先于 Bean Validation），登录后才 `400 / 1001` |
| GET | `/api/v1/notifications/unread-count` | notification | 未读通知数（顶栏铃铛角标；前端 30s 轮询 + 路由切换补读，**无实时推送**） | — | `UnreadCountResult{unreadCount}` | **`security: bearerAuth`** | 同上（框架层 + `CurrentUser.requireId`） |
| PUT | `/api/v1/notifications/read-all` | notification | 全部标记已读；`updated` 为**本次新标记**的条数，无未读时为 `0`（**幂等**，§4.7 实测连打两次为 `1` → `0`） | — | `MarkAllReadResult{updated}` | **`security: bearerAuth`** | 同上。〔**原句"只有全部已读、没有单条已读"已被 [CR-078](../../变更日志/变更台账.md) 功能C 取代**，见下一行；原句保留为 CR-050 时期的取证〕 |
| POST | `/api/v1/notifications/{id}/read` | notification | **单条标记已读（[CR-078](../../变更日志/变更台账.md) 功能C）**：幂等，重复调用不报错、不重复计数 | — | `ApiResponseVoid`（`data` 省略） | **`security: bearerAuth`** | 框架层拦匿名 → `401 / 4001`；业务层 `CurrentUser.requireId`，**且只作用在本人通知上**（`userId` 只从令牌取、无身份参数）。⚠️ **前端是乐观更新**：先本地置已读再 fire-and-forget，失败静默不回滚（一条没标上的已读不值得打断阅读，下次刷新自然恢复真实态）——所以**这条端点失败在页面上看不出来**，不能靠"页面没报错"当验收证据 |

> ⚠️ **`NotificationVo` 的三个可空字段在前端是"可选属性"，不是"值为 null 的属性"**：`postId` / `postTitle` / `floorNo` 由服务端**读时组装**（跨上下文调 `module/account` 取昵称、调 `module/forum` 取标题与楼层号），目标帖已删时 `postId` / `postTitle` 为 `null`、`floorNo` 仅楼层类通知有值。而后端全局 `spring.jackson.default-property-inclusion: non_null`（`application.yml`）会把 `null` 字段**从 JSON 里整个删掉**，所以前端拿到的是 `undefined`。⚠️ **由此产生过一个真实缺陷**：`NotificationsView.vue` 原判空写作 `n.postId === null`、`n.floorNo !== null`，两条都恒不成立，导致帖级通知渲染出「#undefined 楼」、点进去跳 `/post/undefined`——CR-050 真机走查发现并改为真值判断（`if (!n.postId)` / `v-if="n.floorNo"`），类型声明同步改为可选（`postId?: number`）。**这条口径对所有接口通用**：判空一律用真值判断，`=== null` 在带 `non_null` 的响应上不可靠；快照里 `NotificationVo` 仍把这三个字段列在 `properties` 下且**不带 `required`**，与"可能不出现"一致。
>
> **`type` 取值**：`reply` / `like` / `favorite` / `accept` 四类**已实现并会真的产出**；V1 迁移还留有一个 `quote`（引用回复）取值，但**引用回复从未建模，通知侧也不产出**（与 [技术方案](../技术方案.md) §5 的 `quote` 未产出说明一致）。`targetType` 为 `POST` / `REPLY`，`targetId` 指向楼层或帖子本身——**跳转用 `postId`，定位用 `?floor=<targetId>`（仅 `targetType=REPLY`）**。

### 3.4 内容处置（`module/forum` 的 admin 侧，2 个，[CR-066](../../变更日志/变更台账.md#cr-066) / F-SAFE-003 一支）

| 方法 | 路径 | tag | 摘要 | 请求体 | `data` 类型 | **快照声明的鉴权** | **运行时实际强制方式**（见 N-4） |
|------|------|-----|------|--------|------------|------------------|--------------------------------|
| PUT | `/api/v1/admin/posts/{id}/status` | admin-moderation | **处置帖子（下架 / 恢复）**：body `{status, reason?}`，`status` 只受理 `PUBLISHED` / `REMOVED`；**同状态重复调用幂等返回 200 且不记审计**；写 `posts.status`（**不碰 `is_deleted` 作者墓碑**），下架后前台十二处不可见（真机取证见方案 §7.3） | `ModerationCommand` | 无（`data` 省略） | **`security: bearerAuth`** | **框架层拦匿名（401 / 4001）+ 业务层 `CurrentUser.requireRole(auth, "ROLE_SUPERADMIN")` 判角色（403 / 4002）**——⚠️ 角色这一层**框架不做、G7 守护也测不出**，只由控制器 403 用例守着（缺口登记为问题清单 **E-011**） |
| PUT | `/api/v1/admin/replies/{id}/status` | admin-moderation | **处置楼层（下架 / 恢复）**：body 同帖子侧；楼层下架后从楼层列表消失且不可点赞 / 采纳（CR-059 / CR-060 的可见性守卫自此才有真实触发方），**已删（墓碑）楼层不可被处置 → 404 / 3001**，恢复走同一端点、同一权限 | `ModerationCommand` | 无（`data` 省略） | **`security: bearerAuth`** | 同上 |

> ⚠️ **两个端点是"同一个动作的两个方向"**：下架与恢复共用 `PUT …/status`，靠 body 的 `status` 区分，不另设 `/remove`、`/restore` 两个动词——幂等语义因此有明确落点（"当前态即目标态"→ 200 且零审计），也让审计 detail 能记 `from=… to=…` 的迁移方向。`reason` **可空**（PRD 只要求"处置留审计"，未要求必填理由），空值在审计 detail 里落 `-`，上限 200 字由 `@Size` 挡。

### 3.5 关注关系（`module/account` 的 follow 侧，3 个，[CR-074](../../变更日志/变更台账.md) / F-SOC-002 的关注流提前）

| 方法 | 路径 | tag | 摘要 | 请求体 | `data` 类型 | **快照声明的鉴权** | **运行时实际强制方式**（见 N-4） |
|------|------|-----|------|--------|------------|------------------|--------------------------------|
| POST | `/api/v1/users/{targetId}/follow` | **follow**（本轮新开） | 关注用户：⚠️ **重复关注幂等 `200` 且不加行**（靠 `follows` 的联合主键 `(follower_id, followee_id)` + 适配器吞 `DuplicateKeyException`，**不是应用层先查后插**）；**不能关注自己 → `400 / 2009`（`CANNOT_FOLLOW_SELF`，本轮新增错误码）**；目标账号不存在 → `404 / 2007`；**关注动作不产生站内通知** | — | 无（`ApiResponseVoid`，`data` 省略） | **`security: bearerAuth`** | 框架层拦匿名 → `401 / 4001`；业务层 `CurrentUser.requireId`，**自关注与存在性校验在 application**（`FollowApplicationService.follow`）。⚠️ `2009` 在快照里**只出现在 400 的 description 文本**（`参数错误（1001）；不能关注自己（2009）`），**没有结构化 `code` 字段**（见 §6.3） |
| DELETE | `/api/v1/users/{targetId}/follow` | follow | 取消关注：**未关注时同样幂等 `200`**（按两条件 DELETE，删 0 行不报错——DELETE 语义，不报"关系不存在"）；目标账号不存在 → `404 / 2007`；**不校验"是否自己"**（自关注关系结构上不可能存在，无需再判一次） | — | 无（`ApiResponseVoid`） | **`security: bearerAuth`** | 同上。⚠️ **计数口径提醒**：POST 与 DELETE 同路径、两个 operation ⇒ **端点 +2 而路径 +1**（本文"一个 operation 计一个端点"） |
| GET | `/api/v1/users/{targetId}/follow-state` | follow | 关注状态：`{following, fanCount, followCount}`——⚠️ **同一响应里两种视角**：`following` 是"**登录者**是否关注了 target"，而 `fanCount` / `followCount` 是 **target 的**粉丝数与关注数（不是登录者自己的），前端勿混用 | — | `FollowStateVo`（新 schema，另有包装 `ApiResponseFollowStateVo`） | **`security: bearerAuth`** | 同上。**为什么一个"读"端点要登录**：`following` 是 viewer 私有关系，匿名无从判定，故与"我的"列表同口径、**不与帖子详情同口径**（详情公开） |

> ⚠️ **三条端点住在 `module/account`，消费方却在 forum**：§3.2 的「关注」Feed 跨上下文调本上下文的 application 取关注 id 列表（`followingIds`），方向符合 ADR-012 与守护测试 G4。**代价是一条快照读不出的口径**：该方法 `ORDER BY created_at ASC LIMIT 500` ⇒ 关注数超 500 时 **Feed 只覆盖"最早关注的 500 人"、且静默不告知前端**（上限常量 `FOLLOWING_FEED_AUTHOR_CAP`）。
>
> ⚠️ **本轮的结构变更是 `V4__post_views_and_follows.sql`**（[CR-074](../../变更日志/变更台账.md)）：新建 `follows` 表 + 给 `posts` 加 `view_count`（去重阅读数，**计数状态在 Redis、库里没有 `post_views` 表**）与 `cover_url`（发帖时从正文提取首张 https 图片，**旧帖不回填**）。⚠️ **`follows` 不是 V1 存量列**（与 [CR-066](../../变更日志/变更台账.md#cr-066) 那种"V1 就有列、只是没有写入口"的缺口性质不同，本轮是**新开表**）。真实启动校验：`flyway_schema_history` 第 4 行 `post views and follows` **`success=1`**、`installed_on = 2026-09-19 15:44:21`（会话时区已归 `+00:00`，读法见 `AGENTS.md`「常用命令」的时区告警条）⇒ 迁移可执行性**已由实测覆盖**，不依赖 `mvn verify`。
>
> ⚠️ **没有"我的粉丝 / 我关注的人"列表端点，也没有关注按钮的页面入口**（本轮只做关系本身 + 一条以关注为源的 Feed）——**"F-SOC-002 提前"指的是数据与关系链路，不等于该条 PRD 的验收可签**。

**快照中各端点声明的非 200 响应**（description 为 `提示语（错误码）`，由 `ResultCode` 派生；下表为本次抓取的实际内容）：

| 端点 | 声明的状态码 | description（错误码） |
|------|------------|---------------------|
| `POST /auth/verify-student` | 400 / 429 / 500 | 400：`1001` + `2101`；429：`2103` |
| `POST /auth/captcha` | 400 / 429 / 500 | 400：`1001`；429：`2002` + `2003` |
| `POST /auth/register` | 400 / 409 / 500 | 400：`1001` + `2001` + `2102` + `2101`；409：`2004` |
| `POST /auth/login` | 400 / 403 / 500 | 400：`1001` + `2005`；403：`2006` |
| `GET /users/me` | 401 / 404 / 500 | 401：`4001`；404：`2007`（业务码，非路由级 `1004`） |
| `PUT /users/me` | 400 / 401 / 403 / 404 / 429 / 500 | 400：`1001`（长度越界、字符集禁则命中、昵称全空白）；401：`4001`；403：**`2006`（账号已被封禁——本轮第一次有写端点用状态门拒已登录用户）**；404：`2007`；**429：`2008`（资料修改过于频繁，10 次/小时）**；500：`9999`。⚠️ `@ErrorCodes` 声明 `NOT_LOGGED_IN` + `USER_NOT_FOUND` + `USER_BANNED` + `PROFILE_UPDATE_TOO_FREQUENT`，**`INVALID_PARAM` 不显式写**（写了会让 400 的 description 变成"参数错误（1001）；参数错误（1001）"——v1.8 教训本轮重犯一次并当场删掉，见 §2「本次 diff 性质」） |
| `POST /admin/roster/import` | 400 / 401 / 403 / 500 | 400：`1001`；401：`4001`；403：`4002` |
| `GET /boards` | 500 | —（无业务错误码：无入参、无鉴权，只可能落 `9999`） |
| `GET /posts` | 400 / 404 / 500 | 400：`1001`（**含 [CR-058](../../变更日志/变更台账.md#cr-058) 新增的 `sort` 非法值**——只受理 `latest` / `hot`，大小写不敏感、去首尾空白，非法即拒不静默归一；以及 `page`·`size` 非数字）；404：`3001`（`boardCode` 不存在） |
| `GET /posts/search` | 400 / 404 / 500 | 400：`1001`（`keyword` 缺失 / 不足 2 字 / 超 50 字、`days` 不在 {7,30,90}、`page`·`size` 非数字）；404：`3001`（`boardCode` 不存在）。⚠️ **`@ErrorCodes` 只声明了 `NOT_FOUND`**：`1001` / `9999` 由 `OpenApiErrorResponseCustomizer` 自动补，**再显式写一遍 `INVALID_PARAM` 会让 description 变成"参数错误（1001）；参数错误（1001）"**（本次实施中真实踩到并已修正，见 §7 v1.8） |
| `POST /posts` | 400 / 401 / 404 / 500 | 400：`1001`；401：`4001`；404：`3001`（`boardCode` 不存在） |
| `GET /posts/{id}` | 400 / 404 / 500 | 400：`1001`；404：`3001` |
| `DELETE /posts/{id}` | 400 / 401 / 403 / 404 / 500 | 400：`1001`（`id` 非数字）；401：`4001`；403：`4002`（非作者）；404：`3001`（**不存在 / 已删 / 已下架三类合并**，防探测）。`@ErrorCodes` 声明 `NOT_LOGGED_IN` + `FORBIDDEN` + `NOT_FOUND`，`1001` / `9999` 自动补（[CR-065](../../变更日志/变更台账.md#cr-065)） |
| `GET /posts/{postId}/replies` | 400 / 404 / 500 | 400：`1001`；404：`3001`（帖子不存在） |
| `POST /posts/{postId}/replies` | 400 / 401 / 404 / 500 | 400：`1001`；401：`4001`；404：`3001`（帖子不存在） |
| `POST /posts/{id}/accept` | 400 / 401 / 403 / 404 / 500 | 400：`1001` + `3002`（非问答帖）+ `3003`（采纳自己的回复）；401：`4001`；403：`4002`（非提问者）；404：`3001`（帖子或回复不存在，防探测统一提示） |
| `POST /posts/{id}/like` | 400 / 401 / 404 / 500 | 400：`1001`；401：`4001`；404：`3001`（帖子不存在） |
| `POST /posts/{id}/favorite` | 400 / 401 / 404 / 500 | 400：`1001`；401：`4001`；404：`3001` |
| `POST /posts/{postId}/replies/{replyId}/like` | 400 / 401 / 404 / 500 | 400：`1001`；401：`4001`；404：`3001`（帖子或楼层不存在，防探测统一提示） |
| `GET /favorites/mine` | 400 / 401 / 500 | 400：`1001`；401：`4001` |
| `GET /posts/mine` | 400 / 401 / 500 | 400：`1001`（`page`·`size` 非数字）；401：`4001`。**没有 `404` 支**：无路径参数、无资源可"不存在"，空列表是 `200 / total=0`（[CR-071](../../变更日志/变更台账.md)） |
| `GET /replies/mine` | 400 / 401 / 500 | 同上（`@ErrorCodes` 只声明 `NOT_LOGGED_IN`，`1001` / `9999` 自动补）。⚠️ **分页越界与 `size` 上限沿用既有归一口径**：`page=99` → 200 空列表、`size=999` → 归一 100、`size<1` → 回落默认 20，**都不报错**（与 `GET /notifications` 一致，见上一行）|
| `PUT /admin/posts/{id}/status` | 400 / 401 / 403 / 404 / 500 | 400：`1001`（`status` 缺失 / 非 `PUBLISHED`·`REMOVED`、`reason` 超 200 字、`id` 非数字）；401：`4001`；403：`4002`（非 SUPERADMIN）；404：`3001`（**不存在 / 已被作者删除两类合并，防按 id 探测**）；500：`9999` |
| `PUT /admin/replies/{id}/status` | 400 / 401 / 403 / 404 / 500 | 同上（404 的 `3001` 另含"楼层已是墓碑"——`findStatusById` 读不到即按不存在处理，不区分原因） |
| `GET /notifications` | 400 / 401 / 500 | 400：`1001`（`unread` 非布尔、`page`·`size` 非数字）；401：`4001`。⚠️ **越界不报错、口径与 `GET /posts/search` 一致**（§4.7 实测）：`page=99` → 200 且 `list` 空、`total` 照常、`page` 原样回显；`page=0` 归一为 1；`size=999` 归一为 100 |
| `GET /notifications/unread-count` | 401 / 500 | 401：`4001`（无入参，`1001` 无用武之地；`9999` 自动补） |
| `PUT /notifications/read-all` | 401 / 500 | 401：`4001`（同上）。**没有"资源不存在"支**：全部已读对零条通知也是 200 / `updated: 0` |
| `POST /users/{targetId}/follow` | 400 / 401 / 404 / 500 | 400：`1001`（`targetId` 非数字）+ **`2009`（不能关注自己）**；401：`4001`；404：`2007`（目标账号不存在，业务码而非路由级 `1004`）；500：`9999`。`@ErrorCodes` 声明 `NOT_LOGGED_IN` + `USER_NOT_FOUND` + `CANNOT_FOLLOW_SELF`，`1001` / `9999` 自动补 |
| `DELETE /users/{targetId}/follow` | 400 / 401 / 404 / 500 | 400：`1001`；401：`4001`；404：`2007`；500：`9999`。**没有 `2009` 支**：取消关注不判自关注（`@ErrorCodes` 只声明 `NOT_LOGGED_IN` + `USER_NOT_FOUND`） |
| `GET /users/{targetId}/follow-state` | 400 / 401 / 404 / 500 | 同 `DELETE` 那一行（400：`1001`；401：`4001`；404：`2007`；500：`9999`）。⚠️ **快照里"用户不存在"用的是账号侧业务码 `2007` 而非 `3001`**——与 `GET /users/me` 同族 |
| `GET /posts/following` | 400 / 401 / 500 | 400：`1001`（`page`·`size` 非数字）；401：`4001`；500：`9999`。**没有 `404` 支**：无路径参数、无资源可"不存在"，未关注任何人时是 `200 / total=0` |
| `GET /posts/similar` | 400 / 401 / 500 | 400：`1001`（`title` 空或超 100 字）；401：`4001`；500：`9999`。〔**CR-080 注**：本行原抓出来是 `400` 的 description 重复两次 `1001`，系 CR-077 在 `@ErrorCodes` 里显式写了 `INVALID_PARAM` 而通用 400 本就自动补，本轮删去后归一为单条，见 §7 v1.15 第③笔〕 |
| `GET /users/{id}` | 400 / 404 / 429 / 500 | 400：`1001`（`id` 非数字）；404：`2007`（**不存在与已注销同码**，不给枚举位）；429：`2010`（IP 限流 30 次/分钟）；500：`9999` |
| `GET /users/{id}/posts` | 400 / 404 / 500 | 400：`1001`；404：`2007`；500：`9999`。⚠️ **本路径没有 `429` 支**，与上一行同族却不同限流口径，读快照时不要以为主页两个请求共享同一个 429 |
| `POST /notifications/{id}/read` | 400 / 401 / 404 / 500 | 400：`1001`；401：`4001`；404：`3001`（**“不是你的通知”与“通知不存在”同码**，实测 `markRead` 两条分支都抛 `NOT_FOUND`，不外泄他人通知是否存在）；500：`9999` |

> ✅ **N-4 的"漏写鉴权静默变公开"缺口已由 [CR-028](../../变更日志/变更台账.md#cr-028) 闭环，路径级拦截已由 [CR-031](../../变更日志/变更台账.md#cr-031) 补齐**。机制是**三层**：① **统一入口** `common/web/CurrentUser`（`requireId` / `requireRole`，未登录 `401 / 4001`、权限不足 `403 / 4002`）——上表第 8 列的强制逻辑全部收敛到它；② **显式声明锚点** `common/web/PublicEndpoint`（公开）与 `@SecurityRequirement`（受保护）二选一，由 `ArchitectureGuardTest` **机器校验**两条纪律：**每个** HTTP 映射方法必须且只能声明其一（漏写即测试失败）· **凡**声明 `@SecurityRequirement` 的方法必须真的调用 `CurrentUser`（只声明不调用同样失败）——因此"新增端点忘了加鉴权"不再是静默的；③ **框架层（[CR-031](../../变更日志/变更台账.md#cr-031) 起）**：`config/SecurityConfig` 已去掉 `anyRequest().permitAll()`，改由 `security/EndpointAuthorizationManager` **按同一批注解裁决**（`@PublicEndpoint` 放行、`@SecurityRequirement` 要求已登录、`com.campuslink.module.*` 端点两者都没标即 fail-closed），匿名请求在**进入业务代码前**即被过滤器链内的 `security/ApiErrorSecurityHandler` 拒为 `401 / 4001`。
>
> ⚠️ **两层边界须知情**：**其一**，框架**只区分"登录 / 未登录"**——角色与资源级授权（如"仅作者可删"、管理端的 `role in (ops, superadmin)`）仍归业务代码的 `CurrentUser.requireRole`；`403 / 4002` 出口（`AccessDeniedHandler`）为防御性接线，当前裁决口径下实际走入口点分支。**其二**，[CR-031](../../变更日志/变更台账.md#cr-031) 带来一处**行为变化**：受保护端点的"匿名 + 非法请求体"由 `400 / 1001`（[CR-028](../../变更日志/变更台账.md#cr-028) 前 `@Valid` 先于方法体执行）**前置为 `401 / 4001`**（框架先于 Bean Validation）——公开端点不受影响，仍走 `400 / 1001`。`bearerAuth` 的 description 已就地写明这些事实（§4.5 ⑦⑧ 与 ⑫ 实测）。
>
> **历史沿革（Sprint 2 的人工防线，已被机器校验 + 框架拦截取代）**：Sprint 2 交付时两个论坛受保护端点是**手工**写判空（`PostController` / `ReplyController`）+ 各 3 例「匿名 → 401」单测 + `ForumPublicReadTest` 3 例确认匿名可读——当时这三道防线是"人工加的、非流程强制"，A3-9 落地前下一个上下文的受保护端点仍会静默敞开。这一状态在 [CR-028](../../变更日志/变更台账.md#cr-028) 后被上述机器校验取代，路径级强制在 [CR-031](../../变更日志/变更台账.md#cr-031) 后到位。
>
> 另需注意：**路径级错误无法按端点声明**。`405`（`1002` 方法不允许）、`404`（`1004` 无此路由）、`415`（`1003` 媒体类型不支持）由 `GlobalExceptionHandler` 统一处理，springdoc 的 `OperationCustomizer` 只能改已注册的操作，看不到"不存在的路径"，故这三类响应在快照里**完全不出现**（详见 §6）。

## 4. 主链路请求 / 响应结构（A3-4 备选项②）

以下响应体的来源分五档，逐条标注：**【实测】**= 2026-09-11 对 `68d40ae` 运行实例抓取（**均为成功响应**）；**【CR-016 实测】**= 取自 [CR-016](../../变更日志/变更台账.md#cr-016) 当时记录的实测结果，本次未重跑；**【CR-021 实测】**= 2026-09-12 对**修复后**的运行实例抓取，集中在 §4.4 的错误响应；**【Sprint 2 实测】**= 2026-09-12 对含 `module/forum` 的工作区运行实例抓取，集中在 §4.5；**【推演】**= 按快照 schema 与 `common/result/ResultCode` 的 httpStatus 映射写出，未实测。一次性凭据（票据 / JWT）已脱敏。dev 固定验证码与测试名册见 `AGENTS.md`，本文不重复登记凭据。

### 4.1 学籍核验 → 验证码 → 注册（F-ACC-004 主链路）

**① `POST /api/v1/auth/verify-student`**

```jsonc
// 请求
{ "studentId": "249971346", "name": "张三" }

// 成功 HTTP 200 【实测】（dev bypass 名册）
{ "code": 0, "message": "成功", "data": { "ticket": "<一次性核验票据，32 位十六进制，已脱敏>" }, "traceId": "b208e820c94e" }

// 核验不通过 HTTP 400 —— 学号不存在 / 姓名不匹配 / 学号已注册**三者同码同提示**
// 本行为"学号不存在"（240000001）【实测】；"学号已注册"见 ③【CR-016 实测】；"姓名不匹配"未实测【推演】
{ "code": 2101, "message": "学籍信息校验未通过", "traceId": "1a2399953d26" }

// 格式违规 HTTP 400 【实测】—— 与上面区别开：格式错误不泄露名册信息，故明确指出字段
{ "code": 1001, "message": "studentId 学号必须为 9 位数字", "traceId": "451c16d92118" }
```

**② `POST /api/v1/auth/captcha`**

```jsonc
// 请求
{ "target": "someone@example.com" }

// 成功 HTTP 200 【实测】—— 注意 data 字段被省略（jackson default-property-inclusion: non_null），
// 而快照的 ApiResponseVoid 仍声明了 "data": {}，这是 schema 与实际响应的一处不一致
{ "code": 0, "message": "成功", "traceId": "d7dd5bf899fa" }
```

**③ `POST /api/v1/auth/register`**（票据一次性，用后即失效）

```jsonc
// 请求
{ "ticket": "<①返回的票据>", "email": "someone@example.com", "code": "<6 位验证码>", "nickname": "小明" }

// 成功 HTTP 200 【推演】（按 ApiResponseAuthResponse schema；该链路已在 Sprint 1 联调冒烟中用浏览器跑通，本次未抓取响应体）
{ "code": 0, "message": "成功", "data": { "token": "<JWT，已脱敏>", "expiresAtEpochSecond": 1799999999, "user": { "...": "UserVo，见 ④" } }, "traceId": "..." }

// 邮箱已注册 HTTP 409 【推演】（按 ResultCode.EMAIL_EXISTS = 2004 / httpStatus 409，唯一键冲突翻译见 CR-016）
{ "code": 2004, "message": "该邮箱已注册", "traceId": "..." }

// 学号已被占用 HTTP 400 【CR-016 实测】—— 与"学号不存在""姓名不匹配"同码同提示，防名册枚举
{ "code": 2101, "message": "学籍信息校验未通过", "traceId": "..." }
```

### 4.2 登录 → 我的主页

**④ `POST /api/v1/auth/login`**

```jsonc
// 请求
{ "email": "someone@example.com", "code": "<6 位验证码>" }

// 成功 HTTP 200 【推演】（按 ApiResponseAuthResponse / UserVo schema；登录链路已在 Sprint 1 联调冒烟中跑通，本次未抓取响应体）
{ "code": 0, "message": "成功", "data": { "token": "<JWT，7 天滑动续期，已脱敏>", "expiresAtEpochSecond": 1799999999,
  "user": { "id": 1, "nickname": "小明", "avatarUrl": null, "school": "重庆工程学院", "major": "计算机科学与技术",
            "grade": "2024", "bio": null, "role": "USER", "verified": true, "createdAt": "2026-09-11T12:00:00Z" } }, "traceId": "..." }

// 未登录访问受保护端点 GET /api/v1/users/me（无 Authorization 头）HTTP 401 【实测】
{ "code": 4001, "message": "未登录", "traceId": "870d69df657b" }
```

### 4.3 快照中含有的字段级约束（可直接用于前端校验与测试用例）

| 字段 | 快照中的约束 | 来源 |
|------|-------------|------|
| `studentId` | `pattern: \d{9}`、`minLength: 1`、必填 | Jakarta Validation（[CR-013](../../变更日志/变更台账.md#cr-013)） |
| `name` | `pattern: ^(?:[\p{IsHan}]{2,16}(?:[·・][\p{IsHan}]{1,16})?\|[A-Za-z][A-Za-z .'\-]{1,63})$`、必填 | 同上（中文名 2~16 汉字可含 `·`，或外文名字母起头） |
| `email` / `target` | `format: email`、`minLength: 1`、必填 | 同上 |
| `code` | `pattern: \d{6}`、必填 | 同上 |
| `nickname`（**注册侧** `RegisterCommand`） | `minLength: 2`、`maxLength: 32`、`pattern: ^[^<>\p{Cntrl}…\uFEFF]*$`、必填 | 同上（`pattern` 由 [CR-071](../../变更日志/变更台账.md) 同批加上——编辑侧拒尖括号而注册侧不拒，等于"唯一防线漏一半"。⚠️ **这是对外行为收紧**：含 `<` `>` 或控制字符的昵称自本 CR 起 `400 / 1001`） |
| `nickname`（**编辑侧** `UpdateProfileCommand`） | `minLength: 2`、`maxLength: 32`、**非 required**（`null` = 不改）；⚠️ **快照里没有 `pattern`** | 同一组常量（`common/validation/FieldRules`），但该属性叠了**两个可重复 `@Pattern`**（字符集禁则 + 非空白），springdoc **一个都没导出**——判据只能读 `FieldRules` 与 `Account#updateProfile`，见 §6.3 新增行 |
| `major`（专业） | `maxLength: 64`、`pattern` 同注册侧、**非 required** | [CR-071](../../变更日志/变更台账.md) 新增；**单** `@Pattern` ⇒ 正常导出（与上一行对照即是那条表达极限的直接证据） |
| `bio`（签名） | `maxLength: 200`、`pattern` 同上、**非 required** | 同上；200 对齐 [CR-066](../../变更日志/变更台账.md#cr-066) 处置 `reason` 的档位 |
| `ticket` | `minLength: 1`、必填 | 同上 |

### 4.4 错误响应实例（CR-021 修复后实测）

错误体统一为 `common/result/ApiError`，形状固定为 **`{code, message, traceId}`**——**没有 `data` 字段**（与成功响应的 `ApiResponse` 外壳不同，这是有意的：错误不携带业务数据）。以下 7 条均为 **2026-09-12【CR-021 实测】**，`traceId` 为真实抓取值（非凭据，无脱敏必要）。

```jsonc
// ① 请求体不是合法 JSON：POST /auth/verify-student，body = {"studentId":}
//    HTTP 400 —— 改前为 HTTP 500 / 9999「系统繁忙」并打全栈 error 日志（N-3）
{ "code": 1001, "message": "请求体缺失或不是合法 JSON", "traceId": "047d204b6b28" }

// ② 完全不带请求体：同一端点，无 body
//    HTTP 400 —— 与 ① 同码同提示（改前也是 500 / 9999）
{ "code": 1001, "message": "请求体缺失或不是合法 JSON", "traceId": "e6bd4152ac7e" }

// ③ Content-Type: text/plain（body 内容合法）
//    HTTP 415 —— 改前为 500 / 9999
{ "code": 1003, "message": "不支持的请求内容类型", "traceId": "207fd3bd371f" }

// ④ 用 GET 打 POST-only 端点
//    HTTP 405，且响应头含 Allow: POST（RFC 9110 要求；改前为 500 / 9999）
{ "code": 1002, "message": "请求方法不支持", "traceId": "a2b802ee2d31" }

// ⑤ 请求不存在的路径 /api/v1/nonexistent
//    HTTP 404 —— 改前为 500 / 9999
{ "code": 1004, "message": "接口不存在", "traceId": "3ec34cc27532" }

// ⑥ 匿名访问受保护端点 GET /users/me（无 Authorization 头）
//    HTTP 401 —— 改前改后一致
{ "code": 4001, "message": "未登录", "traceId": "522f54766a72" }

// ⑦ 匿名调 POST /admin/roster/import
//    HTTP 401 —— 改前为 403 / 4002，本次拆开「未登录」与「无权限」两态（对齐技术方案 §5）
{ "code": 4001, "message": "未登录", "traceId": "134701226102" }
```

**与快照的对应关系**：只有 **①②⑥⑦** 对应的状态码在快照里有声明（①② → `400`，⑥⑦ → `401`）；**③④⑤ 在快照里一律查不到**——`415` 是因为 springdoc 按 Controller 的 `consumes` 生成请求体媒体类型、不生成 415 分支；`405` / `404` 是因为 OpenAPI 无法为"不允许的方法"或"不存在的路径"挂响应（见 §6 末两行）。这三类前端若要处理，须读本文与 `GlobalExceptionHandler`，**不能只读快照**。

**日志档位**（`GlobalExceptionHandler` 的设计，非快照内容）：①~⑤ 各记 **1 行 WARN**，不打栈；⑥⑦ 这类业务鉴权失败**不记日志**；只有落到兜底 `Exception` 分支的未知异常才 `log.error` 打全栈。改前 ①~⑤ 全部走兜底分支，每个请求一条带全栈的 ERROR——**这就是 N-3 污染 5xx 监控的机制**。

### 4.5 论坛主链路（Sprint 2 MVP 的 6 个新端点，2026-09-12【Sprint 2 实测】）

链路：**版块列表 → 帖子列表 → 发帖 → 详情 → 楼层列表 → 回帖**。以下 `traceId` 均为真实抓取值（非凭据）。发帖 / 回帖的成功响应由 `admin@campuslink.local` 的 JWT 调用取得（[CR-013](../../变更日志/变更台账.md#cr-013) 的 dev-only 管理员账号）。

**① `GET /api/v1/boards`（公开）**

```jsonc
// 成功 HTTP 200 —— 6 个版块已由 V2__seed_boards.sql 种入，按 sort 升序；此处略去中间 4 个
{ "code": 0, "message": "成功", "data": [
  { "code": "qna", "name": "技术问答", "description": "提问、报错排查、环境配置、技术选型", "type": "QUESTION", "sort": 1 },
  { "code": "resources", "name": "学习资源", "description": "教程、笔记、工具、资源分享", "type": "DISCUSSION", "sort": 2 }
  /* …interview / contest / course / chat，sort 3~6… */ ],
  "traceId": "3ed853d4185d" }
```

**② `GET /api/v1/posts?page=1&size=2`（公开，全站最新）**

```jsonc
// 成功 HTTP 200 —— 分页外壳为 PageVo{list,total,page,size}，注意不是 pageNum/pageSize
{ "code": 0, "message": "成功", "data": { "list": [
    { "id": 3, "boardCode": "chat", "boardName": "闲聊灌水", "title": "M6 契约取证：发帖成功响应体",
      "authorNickname": "管理员", "replyCount": 0, "likeCount": 0, "summary": "取证用正文",
      "createdAt": "2026-09-12T15:10:21Z" },
    { "id": 2, "boardCode": "resources", "boardName": "学习资源", "title": "M5 前端联调：浏览器实测发帖",
      "authorNickname": "管理员", "replyCount": 2, "likeCount": 0, "summary": "…（content_md 去 Markdown 记号后前 120 字）",
      "createdAt": "2026-09-12T15:03:23Z" } ],
  "total": 3, "page": 1, "size": 2 }, "traceId": "…" }
```

**③ `POST /api/v1/posts`（需登录）**

```jsonc
// 请求（body 含中文，故用 --data-binary 从 UTF-8 文件发送）
{ "boardCode": "chat", "title": "M6 契约取证：发帖成功响应体", "contentMd": "取证用正文" }

// 成功 HTTP 200 —— 只回 id，前端据 ②跳 /post/{id}
{ "code": 0, "message": "成功", "data": { "id": 3 }, "traceId": "…" }
```

**④ `GET /api/v1/posts/2`（公开）→ ⑤ `GET /api/v1/posts/2/replies?page=1&size=2`（公开）→ ⑥ `POST /api/v1/posts/2/replies`（需登录）**

```jsonc
// ④ 详情：contentHtml 是发布时服务端渲染好的 HTML（ADR-005），此处只截前 60 字
{ "code": 0, "message": "成功", "data": { "id": 2, "boardCode": "resources", "boardName": "学习资源",
    "title": "M5 前端联调：浏览器实测发帖", "contentHtml": "<h2>二级标题</h2>\n<p>这是<strong>浏览器实测</strong>发帖…",
    "authorNickname": "管理员", "replyCount": 2, "likeCount": 0, "accepted": false,
    "createdAt": "2026-09-12T15:03:23Z" }, "traceId": "a73815618bef" }

// ⑤ 楼层：floor_no 升序；内容同样已渲染为 HTML
{ "code": 0, "message": "成功", "data": { "list": [
    { "id": 3, "floorNo": 1, "contentHtml": "<p>浏览器实测回帖：<strong>楼层</strong>应为 1。</p>",
      "authorNickname": "管理员", "createdAt": "2026-09-12T15:03:54Z" },
    { "id": 4, "floorNo": 2, "contentHtml": "<p>M6 契约取证回帖</p>",
      "authorNickname": "管理员", "createdAt": "2026-09-12T15:10:21Z" } ],
  "total": 2, "page": 1, "size": 2 }, "traceId": "9376e254630d" }

// ⑥ 回帖请求 → 成功响应：id + floorNo（floor_no 由 posts.reply_count 行锁派生，见增量设计 §4.1）
{ "contentMd": "M6 契约取证回帖" }
{ "code": 0, "message": "成功", "data": { "id": 4, "floorNo": 2 }, "traceId": "…" }
```

**⑦⑧ 两个受保护端点的匿名调用（**body 合法**、无 Authorization 头；此组为 Sprint 2 实测，[CR-031](../../变更日志/变更台账.md#cr-031) 后响应不变但拦截点上移——由框架层在进入业务代码前产出，见 ⑫）**

```jsonc
// ⑦ 匿名 POST /posts（**body 合法**、无 Authorization 头）→ HTTP 401
{ "code": 4001, "message": "未登录", "traceId": "a4d1c6ea395e" }

// ⑧ 匿名 POST /posts/2/replies（**body 合法**、无 Authorization 头）→ HTTP 401
{ "code": 4001, "message": "未登录", "traceId": "3b437a230c05" }
```

**⑨⑩⑪ 论坛侧的参数与资源错误**

```jsonc
// ⑨ 发帖 title 101 字（DDL 上限 100）→ HTTP 400，字段级提示（格式错误不属名册信息，可明确指出字段）
{ "code": 1001, "message": "title 标题最长 100 字", "traceId": "757a20cb9caa" }

// ⑩ boardCode 不存在 → HTTP 404 / 3001
{ "code": 3001, "message": "资源不存在", "traceId": "4b05ac33521e" }

// ⑪ 帖子不存在（或已删、非 PUBLISHED）→ HTTP 404 / 3001 —— 与 ⑩ 同码同提示，不区分
{ "code": 3001, "message": "资源不存在", "traceId": "429f282dc14f" }
```

**⑫ [CR-031](../../变更日志/变更台账.md#cr-031) 路径级鉴权落地后的复测（2026-09-12，框架拦截生效）**

```jsonc
// 匿名 POST /posts（**body 非法**，`{}`）→ HTTP 401 / 4001 —— 框架先于 Bean Validation
// 对照：CR-031 前该组合返回 400 / 1001「boardCode 版块不能为空」（即下方 🔍 记录的行为顺序已改变）
{ "code": 4001, "message": "未登录", "traceId": "4d9c7822b3bc" }

// 匿名 POST /posts（**body 合法**）→ HTTP 401 / 4001（与上同码同体，60 字节）
{ "code": 4001, "message": "未登录", "traceId": "a80d69fe861b" }

// 伪造 token（Authorization: Bearer garbage）访问 GET /users/me → HTTP 401 / 4001
{ "code": 4001, "message": "未登录", "traceId": "67b4a8f88f2b" }

// 公开端点（GET /boards、GET /posts、GET /posts/{id}、GET /posts/{postId}/replies）→ 200 不受影响
// 匿名 DELETE /api/v1/posts → HTTP 405 / 1002（带 Allow 头；方法不支持先于鉴权，交回 MVC）
// 请求不存在的路径 → HTTP 404 / 1004；actuator/health 与 /api/docs → 200
```

> 🔍 **一个已经改变的行为顺序（[CR-031](../../变更日志/变更台账.md#cr-031) 前后对比）**：**CR-031 前，受保护端点是"参数校验先于登录态校验"**——鉴权写在方法体内部，`@Valid` 在校验阶段先执行，匿名 + 非法 body 返回 `400 / 1001`、匿名 + 合法 body 才返回 `401 / 4001`（Sprint 2 实测，即原 ⑦⑧ 与本节旧注记）。**CR-031 起，框架层在进入业务代码前按端点注解裁决**，受保护端点的**任意**匿名请求（无论 body 是否合法）一律 `401 / 4001`（⑫ 实测）；**公开端点不受影响**（仍走 `400 / 1001`）。前端判断登录态仍须看 `401` 或本地 token，不得把"400"当作已登录的证据。**注**：`GET /users/me` 无入参，两阶段行为一致。

### 4.6 站内搜索（[CR-049](../../变更日志/变更台账.md) / F-FORUM-008，2026-09-13 真机实测）

```jsonc
// ① PRD 验收主用例：**匿名** GET /posts/search?keyword=Redis → HTTP 200
{ "code": 0, "message": "成功", "data": { "list": [
    { "id": 13, "boardCode": "qna", "boardName": "技术问答",
      "title": "Redis 缓存穿透怎么防？CR-049 冒烟帖", "authorNickname": "管理员",
      "replyCount": 0, "likeCount": 0, "summary": "布隆过滤器 + 空值缓存，SETNX 限流。",
      "createdAt": "2026-09-13T22:40:53Z", "accepted": false } ],
  "total": 1, "page": 1, "size": 20 }, "traceId": "8c0fe8ec0d17" }

// ② 中文 2 字关键词（ngram 分词生效）keyword=冒烟 → 200，total=6，ids=[14,13,11,10,9,1]
// ③ keyword=R（单字）/ 全空白 / 完全缺失 / 51 字 → 一律 HTTP 400 / 1001「参数错误」
// ④ keyword=50 字（上限）→ 200（total=0，受理不报错）
// ⑤ days=15、days=0（不在 {7,30,90}）→ 400 / 1001；days=7|30|90 → 200
// ⑥ boardCode=nope（不存在）→ HTTP 404 / 3001「资源不存在」；qna → total=4；chat → total=2
// ⑦ page=abc（类型不匹配）→ 400 / 1001；page=0 → 归一为 1；size=999 → 归一为 100
// ⑧ page=99（越界）→ 200 且 list 为空、total 仍为 6（静默归一，与 ③⑤ 的"校验失败即拒"刻意不同口径）
// ⑨ 无命中关键词 → 200 / total=0（**不是** 404：无结果是正常业务态）
```

> 🔍 **相关度优先于时间的取证**（PRD 要求"按相关度 + 时间排序"，只看 API 输出无法证明两级排序真的分级）：关键词 `CR-043` 返回 `ids=[11,10,9,14,13,8]`，而 DB 侧 `MATCH(title) AGAINST('CR-043')` 的得分为 **11 / 10 / 9 = 0.6905**、**14 / 13 = 0.2850**、**8 = 0.1128**。其中 **14 / 13 的 `created_at`（09-13 22:40）明显晚于 11 / 10 / 9（09-13 13:30）却排在其后**——这就证明 `relevance DESC` 确实在 `created_at DESC` 之前生效，而非退化为纯时间序。同关键词下得分相同的 6 条（如 `冒烟` 全部 0.1128）则由 `created_at DESC, id DESC` 决定次序。
>
> ⚠️ **两处取证用了临时置数，且都已还原**（本节如实记录，避免读者误以为库里本来就有这些数据）：
>
> | 取证目的 | 临时置数 | 实测结果 | 还原确认 |
> |---------|---------|---------|---------|
> | **时间窗口过滤真的会收窄**（库内全部帖子都落在 7 天内，`days` 三个取值结果恒同，无法证明过滤生效） | `UPDATE posts SET created_at = DATE_SUB(NOW(), INTERVAL 40 DAY) WHERE id = 1` | `keyword=冒烟`：无 `days` → total=6；`days=7` → **total=5（排除 1）**；`days=30` → **total=5**；`days=90` → **total=6（含 1）** | 已还原为 `2026-09-12 14:53:29`（原值逐字回写），复核 `MIN/MAX(created_at)` = `2026-09-12 14:53:29` / `2026-09-13 22:40:53`，且 `days=7` 重新返回 total=6 |
> | **`tags LIKE` 兜底通道可用** | `UPDATE posts SET tags = '面试,内推' WHERE id = 12`（标题为「你好」，不含该词） | `keyword=面试` → 200 / total=1 / 命中 12，且该行 `MATCH` 得分为 **0**（纯 LIKE 通道命中，排在有得分者之后）；叠加 `boardCode=qna` 仍命中、`boardCode=chat` → total=0 | 已还原为 `NULL`，复核 `SELECT COUNT(*) FROM posts WHERE tags IS NOT NULL` = **0** |
>
> ⚠️ **`tags` 兜底通道当前没有真实数据来源**（这是本次实施如实点明的取舍，不是遗漏）：发帖链路**从未建模标签**——`PublishPostCommand` 无 `tags` 字段、全库 `posts.tags` 恒为 `NULL`、`post_tags` 关联表未启用。因此"按标签搜索"目前**只有人工置数才能验**，用户从 UI 无法产生任何标签数据。方案已把它列入"不做清单"，标签建模（发帖标签输入 + 关联表）留待后续 CR。
>
> ✅ **回归面**（同批实测，均无变化）：`GET /posts` → total=14 / `created_at DESC`；`GET /boards` → 6 项；`GET /posts/13` → 详情 14 字段齐（含 [CR-048](../../变更日志/变更台账.md) 的 `likedByMe` / `favoritedByMe`）；`GET /posts/13/replies` → 200；`GET /favorites/mine` 匿名 → `401 / 4001`。前端 `/search` 已浏览器实测（宽屏顶栏搜索框 `readOnly` 已解除、`maxlength=50`、回车跳转 `/search?keyword=`；窄屏图标跳 `/search`；版块 / 时间下拉变更即重搜并把条件写回 URL；无结果态出「去提问」按钮；1 字关键词在前端先拦为「关键词至少 2 个字」不发请求）。⚠️ **目视走查未完成**：本轮内嵌浏览器视口为 `0×0`，截图与指针事件通道均不可用，故**宽屏 / 窄屏的视觉呈现只做了 DOM 结构校验、未做像素级目视**，该残留归入 [W-03](../../流程偏离记录.md) 补偿③ 的逐页走查。

### 4.7 通知中心（[CR-050](../../变更日志/变更台账.md) / F-SOC-001，2026-09-14 真机实测）

链路：**A 触发 → B 收通知 → B 查未读数 → B 列表分页与 `unread` 筛选 → B 全部已读**。取证环境为 `APP_ROSTER_BYPASS=false` 的真实名册链路：以 SUPERADMIN 导入名册批次 `cr050-smoke` 后注册 **小A**（`userId=25`，触发者）与 **小B**（`userId=24`，接收者），小B 是目标帖 `id=15` 的作者。以下响应体与 `traceId` 均为真实抓取值。

```jsonc
// ① 触发：A 在 B 的帖子下回帖 → HTTP 200（论坛侧顺带把通知写进 notifications，同事务）
{ "code": 0, "message": "成功", "data": { "id": 19, "floorNo": 4 }, "traceId": "ce43a647d18c" }

// ② 触发：A 收藏 B 的帖子 —— 但 A 此前已收藏，本次 POST 是 **toggle 取消**（active:false）
//    因此**不产生** favorite 通知：这正是"取消交互不发通知"的复测证据
{ "code": 0, "message": "成功", "data": { "active": false, "count": 0 }, "traceId": "a8a5256db45d" }

// ③ B GET /notifications/unread-count → 只有 ① 这一条未读
{ "code": 0, "message": "成功", "data": { "unreadCount": 1 }, "traceId": "174724328b74" }

// ④ B GET /notifications?page=1&size=2 → 读时组装出昵称 / 标题 / 楼层号
{ "code": 0, "message": "成功", "data": { "list": [
    { "id": 9, "type": "reply", "actorId": 25, "actorNickname": "小A", "targetType": "REPLY", "targetId": 19,
      "postId": 15, "postTitle": "CR050 通知冒烟帖", "floorNo": 4, "read": false,
      "createdAt": "2026-09-14T23:06:34Z" },            // ← ⚠️ 时区缺陷见下方注记
    { "id": 8, "type": "like", "actorId": 25, "actorNickname": "小A", "targetType": "POST", "targetId": 15,
      "postId": 15, "postTitle": "CR050 通知冒烟帖", "read": true,
      "createdAt": "2026-09-14T22:47:34Z" } ],
  "total": 8, "page": 1, "size": 2 }, "traceId": "14c9ec9413a8" }
// 注意第 2 条是**帖子级**点赞通知，JSON 里**根本没有 floorNo 这个键**（不是 "floorNo": null）——见下方 non_null 注记

// ⑤ B GET /notifications?unread=true&page=1&size=2 → 只回未读
{ "code": 0, "message": "成功", "data": { "list": [ { "id": 9, "type": "reply", "read": false, "…": "…" } ],
  "total": 1, "page": 1, "size": 2 }, "traceId": "d51d1f91eeb1" }

// ⑥ B GET /notifications?unread=false&page=1&size=1 → 只回已读，与 ⑤ 互补（1 + 7 = 8 = total）
{ "code": 0, "message": "成功", "data": { "list": [ { "id": 8, "type": "like", "read": true, "…": "…" } ],
  "total": 7, "page": 1, "size": 1 }, "traceId": "8ddbd46ee96e" }

// ⑦ B PUT /notifications/read-all → updated 是本次新标记条数
{ "code": 0, "message": "成功", "data": { "updated": 1 }, "traceId": "173bf2efd1a0" }
// ⑧ 再打一次（幂等，不报错、不重复计数）
{ "code": 0, "message": "成功", "data": { "updated": 0 }, "traceId": "68a8ffe50978" }
// ⑨ 未读数归零
{ "code": 0, "message": "成功", "data": { "unreadCount": 0 }, "traceId": "f74e5990399f" }

// ⑩⑪⑫ 三个端点匿名访问 → 一律 HTTP 401 / 4001（框架层在进入业务代码前拦截）
{ "code": 4001, "message": "未登录", "traceId": "888c2123f3b6" }   // GET /notifications
{ "code": 4001, "message": "未登录", "traceId": "fb3d27388803" }   // GET /notifications/unread-count
{ "code": 4001, "message": "未登录", "traceId": "d6c3a4083a31" }   // PUT /notifications/read-all

// ⑬ 受保护端点 + 非法入参 + 匿名：unread=maybe → 仍是 401（框架先于参数绑定，CR-031 的既有口径）
{ "code": 4001, "message": "未登录", "traceId": "ced22da1b269" }
// ⑭ 同一请求带合法 token → 才落 400 / 1001
{ "code": 1001, "message": "参数错误", "traceId": "d92d2f9586b0" }
```

> 🔍 **两处"只有真机才暴露"的缺陷，本次已修**（单测全绿期间它们一直存在）：
>
> | 缺陷 | 表现 | 根因 | 处置 |
> |------|------|------|------|
> | **`unread` 筛选语义反了** | `unread=true` 返回**已读**、`unread=false` 返回**未读**（实测：3 条未读时 `unread=true` 的 `total=0`；全部已读后反而 `total=3`） | 入参叫 `unread`（"只看未读"）、列叫 `is_read`，**语义相反**，适配器却直接 `.eq(is_read, unread)` | `NotificationRepositoryImpl.findPage` 改为 `!unread`（`unread == null` 时不加条件）；**新增适配层测试** `NotificationRepositoryImplFilterTest` 3 例——从捕获的 `QueryWrapper` 里正则取出 `is_read = #{ew.paramNameValuePairs.MPGENVALx}` 的绑定值断言（`true→false` / `false→true` / `null→该列根本不出现在 SQL 段`）。⚠️ **为何必须新增**：应用层是 Mockito 打桩的 service 测试，**结构上看不见适配器的列语义**，这类缺陷只能靠"捕 SQL 参数"的适配层测试或真机兜住 |
> | **前端把可空字段当 `null` 判** | 帖级通知渲染出「**#undefined 楼**」、点进去跳 `/post/undefined` | 全局 `default-property-inclusion: non_null` 把 `null` 字段**从 JSON 删掉**，前端拿到 `undefined`，而代码写的是 `n.postId === null` / `n.floorNo !== null` | 类型改为可选属性（`postId?: number` / `floorNo?: number`），判空改真值判断（`if (!n.postId)` / `v-if="n.floorNo"`）。**这条对所有接口通用**，已在 §3.3 写成口径 |
>
> ⚠️ **一处本次发现、未修的缺陷（属既有链路，非 CR-050 引入）**：上面 ④ 的 `createdAt` 是 **`2026-09-14T23:06:34Z`，而真实 UTC 时刻约 `15:06Z`**——写侧由 MySQL 填（V1 迁移各表均 `created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`，Java 侧不传值），存进去的是**服务器本地墙钟 +08:00**；读侧 JDBC URL 带 **`serverTimezone=UTC`**（`application.yml` 第 17 行），驱动把同一串数字**当 UTC** 读成 `Instant`，于是序列化出的 `Z` 值整体**偏 +8 小时**。表现：帖子详情显示"**发布于 2026-09-15 06:38**"（未来时间）、35 分钟前的内容显示"刚刚"、约 24 小时前的显示"16 小时前"。**Sprint 2 起影响全部时间戳**，通知中心只是把它照出来了。本次**不改**（根因在数据源与时区口径、跨所有读写链路，改法要先定"库里到底存 UTC 还是存本地"这一决策并可能重写历史行），已登记为问题台账条目与 [CR-051](../../变更日志/变更台账.md) 候选。
>
> ✅ **2026-09-15 加注：该缺陷已由 [CR-051](../../变更日志/变更台账.md) 闭环（台账外问题 L-6 置为已处置）**——决策为**库里存 UTC**：数据源 URL 的 `serverTimezone=UTC` 换成 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`（两参数必须成对，只改前者不触会话时区），新增 `config/DataSourceTimezoneTest` 守护；开发库存量 11 表 / 19 列先 `mysqldump` 备份再一次性 `−8 HOUR` 校正；**未新增 `V*.sql` 迁移、未改 `DATETIME` 列类型、未在前端做 −8h 补偿**。实测：新写入行与 `UTC_TIMESTAMP()` 差 0~1 秒，`formatRelativeTime` 由恒"刚刚"恢复正常递进（"13 分钟前"），**契约快照重抓 `git diff` 为空**（本 CR 不动接口形状）。⚠️ **本节 ④ 的 `2026-09-14T23:06:34Z` 是修复前的真实取证值，保留不改**（它是那条缺陷的证据），当前口径见 §1「时间字段口径」。
>
> ✅ **回归面**（同批实测，均无变化）：`GET /posts`、`GET /boards`、`GET /posts/15`、`GET /posts/15/replies` 形状不变（`GET /boards` 仅 `operationId` 由 `list_2` → `list_3`，见 §2）；点赞 / 收藏 / 采纳三条触发链在 CR-050 挂点后仍各自 200；**自触发不发**（自己赞自己的帖，`notifications` 行数不变）、**toggle 取消不发**（② 即为证据）。前端 `/notifications` 已浏览器实测（顶栏铃铛角标读数 → 列表 → 「全部 / 未读」切换 → 点楼层类通知跳 `/post/15?floor=<replyId>` 并高亮 → 「全部已读」后角标消失），⚠️ **目视走查仍受限于 `0×0` 视口**：本轮用 DOM 结构 + `getComputedStyle` + `scrollY` 取证（高亮类 `bg-primary-soft` 命中、滚动到 `scrollY=834`），**像素级视觉未验**，归 [W-03](../../流程偏离记录.md) 补偿③。

## 5. 与技术方案 §5 接口表的逐条差异

| 类别 | 端点 | 处置 |
|------|------|------|
| **§5 已列且已实现（账号 5 行）** | `POST /auth/verify-student`、`POST /auth/captcha`、`POST /auth/register`、`POST /auth/login`、`POST /admin/roster/import` | 无需动作 |
| **§5 未列、已补入并实现** | `GET /api/v1/users/me`（[CR-019](../../变更日志/变更台账.md#cr-019) 补入）、`GET /api/v1/boards`、`GET /api/v1/posts/{postId}/replies`（[CR-022](../../变更日志/变更台账.md#cr-022) 的 MVP 范围新增，**§5 原表没有这两行**） | ✅ 技术方案 §5 已同步补入这两行 |
| **本次由"计划项"转为"已实现"（MVP 子集）** | `GET /posts`（**`sort=latest` / `sort=hot` 双排序已由 [CR-058](../../变更日志/变更台账.md#cr-058) 补齐**——原限定"仅全站最新 / 版块内时间序，无 `sort=hot`"自 2026-09-16 起不再成立；**出参仍不含 `hotScore`**，无产品需求）、`POST /posts`（**无标签**）、`GET /posts/{id}`（**无 DELETE**）、`POST /posts/{id}/replies`（**无 `quotedReplyId`**） | 技术方案 §5 已标注"已实现（MVP 子集）"与未实现部分 |
| **§5 已列但仍未实现**（后续 Sprint） | `GET /boards/{code}/posts`（**已由 `GET /posts?boardCode=` 替代**，不是缺口）、~~`sort=hot` 与 `hot_score`（ADR-006 定时任务）~~ **热榜已由 [CR-058](../../变更日志/变更台账.md#cr-058) 实现，落点为 `GET /posts?sort=hot`**（`posts.hot_score` 由 `@Scheduled` 定时刷新；**出参不暴露 `hotScore`**，前端只消费顺序）、`DELETE /posts/{id}`、`PUT /posts/{id}/accepted-reply`、**toggle 版点赞 / 收藏已由 [CR-048](../../变更日志/变更台账.md) 实现并取代**（原列的 `POST\|DELETE` 双方法语义收敛为单一 POST toggle）、`GET\|PUT /notifications` **通知已由 [CR-050](../../变更日志/变更台账.md) 实现，落点为 `GET /notifications?unread=&page=&size=` + `GET /notifications/unread-count` + `PUT /notifications/read-all`**（原计划的 `PUT /notifications` 由 `read-all` **取代**、不留作缺口；**单条已读未实现**，`quote` 类通知不产出）、~~`GET /search`~~ **站内搜索已由 [CR-049](../../变更日志/变更台账.md) 实现，落点为 `GET /posts/search`**（**仅标题 + 标签**；正文全文检索 = F-FORUM-009，仍属 P1 未实现）、`POST /reports`、`/admin/...` 处置台与工单 | 契约尚未设计；各 Sprint 开工前补 §5 并在实现后重生成快照（对应 [sprint-2.md](../../开发/Sprint2计划.md) §1 的 OUT 清单） |
| **v1.13 / v1.14 新增、§5 原表没有的行** | `PUT /users/me` + `GET /posts/mine` + `GET /replies/mine`（[CR-071](../../变更日志/变更台账.md) 个人中心第一轮，v1.13）；`POST\|DELETE /users/{targetId}/follow` + `GET /users/{targetId}/follow-state` + `GET /posts/following`（[CR-074](../../变更日志/变更台账.md) C 档体验升级，v1.14） | ✅ **本轮已全部补入[技术方案](../技术方案.md) §5**（含新错误码 `2009 CANNOT_FOLLOW_SELF` / 400、`2008 PROFILE_UPDATE_TOO_FREQUENT` / 429 两行）。⚠️ **分段规则没有变化**：`2008` / `2009` 都落在账号段既有区间（2xxx），新增一行不等于新增一个分段 |
| **§5 有约定但快照无对应表达** | 时间 ISO 8601 UTC、错误码**分段规则**（分页口径已随本次同步为 `page/size`，不再是差异） | **错误码自 [CR-021](../../变更日志/变更台账.md#cr-021) 起部分可见**：具体码与提示语出现在各响应的 description 里（§3 的非 200 表），但"1xxx 通用 / 2xxx 账号 / 21xx 学籍 …"这一**分段规则**OpenAPI 表达不了，仍须读技术方案 §5。⚠️ **时间这一项现在不只是"快照表达不了"，而是"实现与约定相反"**：字段类型确为 `string` + `format: date-time`、后缀 `Z`，**但值带 +8 小时偏移**（JDBC `serverTimezone=UTC` vs MySQL 写本地墙钟），故**读快照不得把 `Z` 当作真 UTC**，实测取证见 §4.7 的时区注记。**✅ 2026-09-15 加注：该"实现与约定相反"的差异已由 [CR-051](../../变更日志/变更台账.md) 消除**——会话时区被数据源强制为 `+00:00` 后，`Z` 即为真 UTC，本行"时间"一项退回纯"OpenAPI 表达不了分段规则"式的表达极限问题，现行口径改见 §1「时间字段口径」；上文对 L-6 时期的叙述保留为历史记录，不改写。 |

## 6. 快照的已知缺口与表达极限（读快照前必须知道）

### 6.1 已由 [CR-021](../../变更日志/变更台账.md#cr-021) 处置的三项缺口（2026-09-12）

| # | 原缺口（[CR-019](../../变更日志/变更台账.md#cr-019) 归档时发现） | 修复方式 | 复测结论 |
|---|--------------------------------------------|---------|---------|
| **N-1** | **错误响应完全缺失**：6 个端点只声明 `200`。springdoc 仅按 Controller 返回类型生成，而实际错误经 `GlobalExceptionHandler` 按 `ResultCode.getHttpStatus()` 返回真实状态码，契约里完全看不到 | 新增项目自有注解 `common/result/ErrorCodes`，端点用它声明会抛的业务码；新增 `config/OpenApiErrorResponseCustomizer`（同时实现 `OperationCustomizer` 与 `OpenApiCustomizer`）统一生成 4xx / 5xx 响应，并把错误体 schema `ApiError` 注册进 `components`。**状态码与提示语一律取自 `ResultCode`，定制器不手写任何字面量** | ✅ 快照现有 `ApiError` schema，6 端点全部声明非 200 响应（逐条见 §3 第二张表）。**未采用**原计划的 swagger `@ApiResponses`——那要在每个端点重复书写 `ResultCode` 已有的状态码与提示语，同一事实存两份，改错误码必漂移 |
| **N-2** | **未声明鉴权**：`OpenApiConfig` 只设 `info`，无 `securitySchemes` → 快照无 bearerAuth、Swagger UI 无 Authorize 按钮、需登录的 `GET /users/me` 看起来是公开接口 | `OpenApiConfig` 补 `components.securitySchemes.bearerAuth`（HTTP bearer / JWT）；受保护端点加 `@SecurityRequirement`，常量集中在 `common/web/ApiDocs` | ✅ 快照有 `securitySchemes: [bearerAuth]`，`users/me` 与 `admin/roster/import` 两个操作带 `security`，Swagger UI 出现 Authorize 按钮。**但见 N-4：声明 ≠ 强制** |
| **N-3** | **不可解析的请求体返回 500 / 9999（缺陷，[手册](../../流程手册.md) 4.3 定级 P2）**：`HttpMessageNotReadableException` 无专用处理器，落到兜底 `@ExceptionHandler(Exception.class)`，既报成服务端错误、又 `log.error` 打全栈 | 重写 `GlobalExceptionHandler`：新增 **6 个**框架异常处理器（覆盖 **8 类**异常：校验失败、不可解析请求体、缺参与类型不匹配共用一个、方法不支持、媒体类型不支持、无路由含 `NoResourceFound` 与 `NoHandlerFound` 两类），返回类型改 `ApiError`，并按"客户端错误 WARN 不打栈、业务错误不记日志、未知异常才 ERROR 打全栈"分档 | ✅ **范围比原记录更大**：改前不止畸形 JSON，共 **5 类**客户端错误都落兜底（畸形/缺失请求体 → `400/1001`、`text/plain` → `415/1003`、错方法 → `405/1002` 且带 `Allow`、无路由 → `404/1004`）。10 条探针全部符合预期，整轮复测后端日志 **ERROR 行数 = 0**、`unhandled exception` 出现 **0 次**（逐条实测响应见 §4.4） |

### 6.2 原开放问题 N-4 / N-5 / N-6 —— 已由 [CR-028](../../变更日志/变更台账.md#cr-028) 处置（2026-09-12；N-4 的框架级强制随后由 [CR-031](../../变更日志/变更台账.md#cr-031) 补齐）

| # | 原问题 | 处置 | 复测结论 |
|---|------|------|---------|
| **N-4** | 🔴 **契约声明了鉴权，运行时并未按该声明强制** | 新增统一入口 `common/web/CurrentUser`（`requireId` / `requireRole`）+ 公开声明锚点 `common/web/PublicEndpoint`，并由 `ArchitectureGuardTest` **机器校验**两条纪律：每个 HTTP 映射方法必须且只能声明 `@PublicEndpoint` 或 `@SecurityRequirement`；凡声明 `@SecurityRequirement` 的方法必须真的调用 `CurrentUser`。4 个受保护端点全部改走该入口（逐个见 §3 第 8 列） | ✅ **"漏写鉴权静默变公开"缺口闭环**：负向对照实验（临时注入 3 个违规探针文件 → 守护测试 **7 例失败**、探针删除后全绿）证明规则**真的会拦**；真机复测 4 个受保护端点匿名 / 非法 token → 全部 `401`。⚠️ **当时残留的"路径级拦截未做"已由 [CR-031](../../变更日志/变更台账.md#cr-031)（2026-09-12）闭环**：`SecurityConfig` 去 `permitAll()`，按同一批端点注解在过滤器链内裁决（`module` 未声明 fail-closed），匿名在进入业务代码前即被拒（§4.5 ⑫ 实测）；**残留边界变为**：框架只区分"登录 / 未登录"，角色与资源级授权仍归业务代码（详见 §3 的 N-4 说明段） |
| **N-5** | 技术方案 §5 的鉴权表述与实现机制不符（写"未登录可读、写操作 401"，而实现是各 Controller 自判） | 修正 §5 的鉴权表述，使其与 **`CurrentUser` + `ArchitectureGuardTest` 的机器校验机制**一致 | ✅ 技术方案 §5 已同步（[CR-028](../../变更日志/变更台账.md#cr-028)） |
| **N-6** | **名册 CSV 导入的表头识别只认中文（缺陷，P3）** | `RosterImportApplicationService` 的表头识别改为**中英双认**（`studentId` / `student_id` / `Student ID` / `STUDENT-ID` 等别名归一后比对），并**在导入侧补 `^\d{9}$` 格式校验** | ✅ 新增 10 个回归用例（`RosterImportApplicationServiceTest`，含表头跳过、非法学号不入库、失败也写审计等）；真机复测英文表头 CSV → `inserted:0 skipped:0 failed:2` 且**不再污染名册**，`audit_logs` 如实写入 |

> **本次的净效果**：N-1 / N-2 / N-3（[CR-021](../../变更日志/变更台账.md#cr-021)）+ N-4 / N-5 / N-6（[CR-028](../../变更日志/变更台账.md#cr-028)）共 **6 项**契约缺口全部处置；N-4 中"框架级强制"这一层随后由 [CR-031](../../变更日志/变更台账.md#cr-031) 补齐（**声明、机器校验、运行时拦截三层齐备**，边界与行为变化见 §3 与 §4.5 ⑫）。**但快照本身仍不是"运行时真相"**，读它之前请先看 §6.3 的表达极限。

### 6.3 OpenAPI 的固有表达极限（非缺陷，快照里**永远**看不到）

| 内容 | 为什么表达不了 | 读者该看哪里 |
|------|--------------|------------|
| 错误码语义 | `2101` 三态同码同提示的**防名册枚举**约定（PRD F-ACC-004）在 OpenAPI 里没有对应结构，只体现在 §4.1 的实测响应里 | [技术方案](../技术方案.md) §5 与 [PRD](../../需求/产品需求文档.md) |
| 统一响应外壳是逐端点展开的 | 快照里是 `ApiResponseAuthResponse`、`ApiResponseUserVo` 等 **15 个具体类型**（⚠️ 本次更正：[CR-048](../../变更日志/变更台账.md) 新增 `ApiResponseInteractionResult` 后即为 12，v1.7 的本文仍写 11；[CR-049](../../变更日志/变更台账.md) 的搜索端点**未新增**——复用 `ApiResponsePageVoPostSummaryVo`；[CR-050](../../变更日志/变更台账.md) 通知中心 +3 → 现为 **15**：`ApiResponsePageVoNotificationVo` / `ApiResponseUnreadCountResult` / `ApiResponseMarkAllReadResult`），没有 `ApiResponse<T>` 泛型表达；改外壳字段会同时改动这 15 个 schema。〔**2026-09-19 再更正（[CR-071](../../变更日志/变更台账.md)）**：上句"15"又过时，本轮两个"我的"列表各带一个分页外壳 ⇒ **现为 17 个**（新增 `ApiResponsePageVoMyPostSummaryVo` / `ApiResponsePageVoMyReplyVo`）。⚠️ 这条计数的意义不是数字本身，而是**"每加一个出参类型就多一个 `ApiResponse*` schema"这条放大规则**——看到 schema 数比预期多，先按它核对是不是又新造了出参形状（本轮 48 = 41 + 7，7 个新 schema 里 2 个是外壳、4 个是"我的"列表模型、1 个是 `UpdateProfileCommand`）〕 | 读 diff 时注意"一处改动、多处变化" |
| **`405`（`1002` 方法不允许）与 `404`（`1004` 无此路由）** | `OperationCustomizer` 只能修改**已注册的 operation**，而"不存在的路径"根本没有 operation，"不允许的方法"也无从挂在某个 operation 上 | §4.4 的 ④⑤ 实测响应 + `GlobalExceptionHandler` |
| **`415`（`1003` 媒体类型不支持）** | springdoc 按 Controller 的 `consumes` 生成请求体媒体类型，不生成 415 响应分支 | §4.4 的 ③ 实测响应 |
| **同一属性上可重复注解的取值（[CR-071](../../变更日志/变更台账.md) 实测新增）** | `UpdateProfileCommand.nickname` 叠了**两个 `@Pattern`**（字符集禁则 `^[^<>…]*$` + 非空白 `[\s\S]*\S[\s\S]*`）。Jakarta 的 `@Pattern` 是 `@Repeatable`，Bean Validation **两条都会执行**，但 springdoc 读注解时按"每类注解取一个值"处理 ⇒ **快照里该属性只剩 `minLength` / `maxLength`，两条 `pattern` 一条都没出现**。对照证据：同 schema 内**只挂单条** `@Pattern` 的 `major` / `bio` 正常带出 `pattern`。⚠️ **后果是"读快照会以为昵称能填尖括号"**——这是安全判据，不是文案 | 读 `common/validation/FieldRules`（长度与字符集的唯一事实源）与 `Account#updateProfile`（聚合内二次校验，无 setter 可绕）；处置建议见 §7 v1.13 遗留① |
| **枚举字段的取值域** | `MyPostSummaryVo.status` / `MyReplyVo.status` 在快照里只是 `type: string`——后端是 `PostStatus` / `ReplyStatus` 枚举，springdoc **不导出 `enum`**（同类的还有 `NotificationVo.type`、`BoardVo.type`） | 读 `module/forum/domain/model/*Status.java`；"REMOVED 条目前端不可点"是页侧约定，实测取证在[实施方案 §8](../../开发/实施方案/CR-071-个人中心第一轮.md) |
| **可为 null 的出参字段在快照里与必填字段长得一样（[CR-074](../../变更日志/变更台账.md) 补做轮实测登记）** | `PostSummaryVo.coverUrl` 只有 `type: string`、**没有 `nullable: true`、也不在 `required` 里**——springdoc 不读 Java 侧的可空性。⚠️ 更要紧的是全局 `spring.jackson.default-property-inclusion: non_null` 会把 `null` 字段**从响应里整个删掉**：`cover_url` 是 [V4](../../../backend/src/main/resources/db/migration/V4__post_views_and_follows.sql) 新列、**旧帖不回填**，所以旧帖的列表项里这个键**根本不出现**（不是"值为 null"）。前端一律用真值判断（`if (p.coverUrl)`），`=== null` 不可靠——同一条教训已由 `NotificationVo` 的三个字段实测踩过一次（见 §3.3 注） |

| **"一定有值、但值取决于你有没有带令牌"的字段（[CR-080](../../变更日志/变更台账.md) 实测登记）** | `PostSummaryVo.likedByMe` 是 `boolean` 原始类型 ⇒ springdoc 把它写进 `required`，快照读起来"每条都会给"是对的，但**"匿名恒 false、只有带令牌才是真实状态"这层口径完全不在契约里**。同一形状还有 `PostDetailVo.likedByMe` / `favoritedByMe` 与 `ReplyVo.likedByMe`（[CR-048](../../变更日志/变更台账.md) 起就如此）。OpenAPI 3.1 没有"按请求身份条件取值"的表达位 | 本表 §3.2 `GET /posts` 那行的〔CR-080 注〕+ 三条单测（登录批量 / 匿名零仓储调用 / 空页不查）与真机两路（带令牌 `true`、匿名 `false`） |

> **当前状态**：N-1 / N-2 / N-3 / N-4 / N-5 / N-6 **六项全部处置**（[CR-021](../../变更日志/变更台账.md#cr-021) 三项 + [CR-028](../../变更日志/变更台账.md#cr-028) 三项），其中 **N-4 的运行时强制已由 [CR-031](../../变更日志/变更台账.md#cr-031) 补齐第二层**——**声明（契约）、机器校验（守护测试）、运行时拦截（框架）三层齐备**；框架只区分"登录 / 未登录"，角色与资源级授权仍归业务代码。**上表四类是表达极限而非缺陷**：它们不是"忘了写"，是 OpenAPI 格式本身装不下，故**再生成多少次也不会出现**，只能读文字约定（§4.4 / 技术方案 §5 / `GlobalExceptionHandler`）。〔**2026-09-19 注（[CR-071](../../变更日志/变更台账.md)）**：上句"四类"现为**六类**（新增"可重复注解取值"与"枚举取值域"两行）。⚠️ 这两行与前四行**性质不同**：前四行是 OpenAPI **格式**装不下，后两行是 **springdoc 的读取实现**装不下——理论上可通过自定义 `ParameterSchemaVisitor` / 改用 `@Schema(allowableValues=…)` 补进快照，但那等于把同一条规则**再存一份**（与"禁止用 `@ApiResponses` 手写错误响应"同一判据冲突）。**本轮选择不补、只登记**，并把判据的唯一事实源指向代码（`FieldRules` / 枚举类）；若将来要机器化，正解是"由 `FieldRules` 生成 `@Schema`"而不是手写，属独立一批改动〕

## 7. 变更记录

- **v1.15（2026-09-24）——[CR-080](../../变更日志/变更台账.md) 列表点赞落地后再生成，并补做 [CR-077](../../变更日志/变更台账.md) / [CR-078](../../变更日志/变更台账.md) 欠下的同一件**：`openapi.json` 由 31 端点 / 26 路径 / 50 schema / 22 受保护 → **35 / 30 / 54 / 24**，tag 仍 10，文件 88437 → **99031 字节**。**三笔改动必须分开记**：① **CR-077 / CR-078 的 4 个 operation 与 4 个 schema 全部在本版才进快照**——那两轮把代码提交推送了（`b4ecb5e`）、AGENTS 的基线数也改了，**唯独快照与本文 §3 没动**，于是出现"已提交代码 ≠ 已归档契约"的三天窗口（本轮 §3.1 / §3.2 / §3.3 三张表据此补 4 行、§3.3 那句"没有单条已读"随之更正）；② **CR-080 自己只加一个字段** `PostSummaryVo.likedByMe`，外加 `GET /posts` 与 `GET /users/me` 两行 `summary` 文案据实改写；③ **顺手修掉一处 CR-077 的契约瑕疵**：`GET /posts/similar` 的 400 description 实测是 **`参数错误（1001）；参数错误（1001）`**——`@ErrorCodes` 里显式写了 `INVALID_PARAM`，而通用 400 本就由 `OpenApiErrorResponseCustomizer` 自动补，**这是 v1.8 记过、CR-071 又自修过一次的同一缺陷第三次出现**（同一份 §2 教训隔两轮再犯，说明"靠人记得住"这条防线本身不可靠）；删去该声明后重建重抓，归一为单条 `参数错误（1001）`。**同步动作**：§1 覆盖范围 31 → 35、§2 五格换现行值（原句保留为〔…〕）、§3 四行新增 + 两处可选身份注、§6.3 补一条"boolean 字段进 `required` 但语义看不见"的表达极限。**⚠️ 本版来源＝未提交的工作区 ⇒ 「提交后核对」义务重新挂上**（上一版 v1.14 是唯一一次无此义务的，本轮不再成立）。**一条本轮自纠**：§2「本次 diff 性质」初稿把 −31 行归因写成"新 operation 换位 + `GET /posts` 新增 `2010`"，逐条列出删除行后发现两处都不成立（新 operation 无"换位"可言、`GET /posts` 也没有 `2010`），已按实测改正为"2 行 `summary` 改写 + 29 行 `MyReplyVo` 三块整体换位"。
- **v1.14（2026-09-20）——[CR-074](../../变更日志/变更台账.md) C 档体验升级的「快照再生成」补做轮（本轮零业务代码改动）**：CR-074 落地时把再生成挂账（台账该行明写"openapi 快照 v1.14 未再生成"），本轮补上。**快照变化**：27 → **31 操作**、23 → **26 路径**、48 → **50 schema**、9 → **10 tag**（新开 `follow`，是第一个由关系类资源开出的 tag）、18 → **22 受保护操作**，文件 79158 → **88437 字节**（`sha256 3e7f377c4b20f006…c579da71`）；diff **+357 / −1**——**唯一一条删除行是 `GET /posts/{id}` 的 `summary` 文案**（补进"阅读数按每帖每账号每日一次去重累加"），结构化比对复核为"既有 operation 仅 1 处被改写、既有 schema 仅 2 处字段增加（`PostSummaryVo` +`viewCount` +`coverUrl`、`PostDetailVo` +`viewCount`）、`sort` 参数一字未变"。**三条本轮才成立的取证事实**：① **抓取来源＝已提交且已推送的树**（`git status --porcelain backend/` 为空）⇒ **历轮「提交后核对」义务在本轮不存在**，这是本仓第一次"抓取发生在代码提交之后"；② **jar 新鲜度判据换了**——`mvn -o package` 在源码未变时**不会重写 jar**（`maven-jar-plugin` 无变更即跳过），"jar mtime 晚于源文件"自此不再是有效证据，改用「`find backend/src -name "*.java" -newer <jar>` 为空」+「抓出的 `/api/docs` 确实含本次要收的 4 个 operation」两条硬核对；③ **`V4__post_views_and_follows.sql` 的真实执行由 `flyway_schema_history` 第 4 行 `success=1` 佐证**（`installed_on` 读前先 `SET SESSION time_zone='+00:00'`，因为该列是 `timestamp`）。**本文同步四处**：§2 五行改写（历史原句逐字保留）、§3 标题与分项计数（新增 §3.5 关注关系）、§3 末非 200 表补 4 行、§5 补一行、§6.3 补一行（可空字段的表达极限）。**收尾机器校验**：非 200 表的 31 行与快照的 31 个 operation **按方法 + 路径做集合比对，双向零差集**；全表竖线计数逐行校验（本轮踩过一次：一处单元格里写了裸 ` | ` 把表格列数撑乱，另一次 `Edit` 吞掉一行的前三个单元格，两次都由机器校验当场抓回）。**一处本轮新登记的表达极限**：`2009` 在快照里**只出现在 400 的 description 文本**里（`参数错误（1001）；不能关注自己（2009）`），OpenAPI 不产出结构化 `code`——机器读契约拿不到错误码，这一点与 §6.3 既有各条同性质。

- **v1.13（2026-09-19）——[CR-071](../../变更日志/变更台账.md) 个人中心第一轮（F-ACC-007：资料编辑 + 我的帖子 / 我的回帖）落地后再生成**：新增 **3 个受保护 operation**（`PUT /api/v1/users/me`、`GET /api/v1/posts/mine`、`GET /api/v1/replies/mine`），规模 **24 → 27 操作 / 21 → 23 路径 / 41 → 48 schema / 受保护 15 → 18**，**tag 仍 9 个**（`user` / `post` / `reply` 各加行，未新建上下文）；文件 **68470 → 79158 字节**，`sha256 b76663ae8878e348a0cf04e347e7a762fdeadd2341934a23f6eac6e0237db6b8`。**新增 schema 7 个**：`UpdateProfileCommand` 与 `MyPostSummaryVo` / `MyReplyVo` 及其 `PageVo` / `ApiResponse` 外壳（⚠️ `ApiResponse*` 外壳由 15 → **17** 个，快照里"每个响应形状一份外壳"的膨胀自此可见——纯机械产物，不是设计）。**diff 性质：非纯新增，`git diff --numstat` +462 / −49**，49 行删除**逐行归因**（判据沿用 v1.12 的"删除行逐行归因 + 结构化比对"，不用"−0 即纯新增"）：① `/api/v1/users/me` 路径块内 **9 行**——其中真正被改写的只有 `summary` **1 行**（原文"他人主页**与资料编辑**在后续 Sprint"已过时，据实删去"与资料编辑"），其余 8 行是同一 path 新插 PUT 后 `tags` / `description` / `$ref` / `"401"` / `"404"` 各行的**对齐移位**（新文件里逐字仍在：`未登录（4001）` 出现 18 次、`用户不存在（2007）` 2 次）；② `RegisterCommand.nickname` 的 `minLength: 2` **1 行**被替换为带 `pattern` 的多行；③ **37 行 + 2 行**分别是 `UserVo` 与 `ApiResponseUserVo` 两个 schema 块**整体换位**（新增 7 个 schema 引起 springdoc 排序变化，与 v1.12 的 `ApiResponseVoid` 同现象）。**结构化比对佐证**（`json.load` 两版逐项比）：既有 41 个 schema 中仅 `RegisterCommand` 与 `UserVo` 内容变化、既有 operation 中仅 `GET /users/me` 的 `summary` 变化，**无第三处**。⚠️ **两处"既有端点被改动"必须点名，不得混进"纯新增"口径**：`RegisterCommand.nickname` 自本轮起带禁则字符集 `pattern`（`common/validation/FieldRules.TEXT_ALLOWED_PATTERN` 第一次进注册侧契约，是对**已上线端点的收紧**）；`UserVo` 新增 `status` 字段（⚠️ springdoc 只出 `{"type":"string"}`、**不出枚举取值域**，`NORMAL|BANNED|DEACTIVATING` 三值快照表达不了，见 §6.3）。**两处本轮自修，如实登记**：① 首次抓取时 `PUT /users/me` 的 `400` description 是 `参数错误（1001）；参数错误（1001）`——因为 `@ErrorCodes` 显式列了 `INVALID_PARAM`，而通用 400 本就由 `OpenApiErrorResponseCustomizer` 自动补，**这是 §2 记过的 v1.8 教训重犯**，删去该声明后重建实例重抓才只剩一条；② 新的 `NON_BLANK_PATTERN`（"昵称不能为空白"）**在快照里完全看不见**——`UpdateProfileCommand.nickname` 上叠加了**两个** `@Pattern`，springdoc 对可重复注解**一个都不输出**（`major` / `bio` 各只有一个 `@Pattern`，就正常输出），已作为 §6.3 新增表达极限行登记，判据唯一事实源指向代码。**抓取方式**：`mvn -o package`（同一条命令顺带复验 **`Tests run: 294, Failures: 0, Errors: 0`**）后起**临时端口 8096** 实例、`APP_ROSTER_BYPASS=false`，抓 `http://localhost:8096/api/docs`（一律 `localhost`），`servers` 照 v1.6 / v1.10 / v1.11 / v1.12 先例归一回 `http://localhost:8088`，除此之外未改任何字段。⚠️ **代码来源为未提交的工作区**（CR-071 前后端与文档均未提交）⇒ 按本节纪律**提交后须自己提交源码重抓一次、与本次产物逐字节比对**，比照 v1.5.1 / v1.6.1 / v1.7.1 / v1.8.1 / v1.9.1 / v1.10.1 / v1.12.1 的闭环先例。**本文同步范围**：头部〈版本〉〈最后更新〉与变更登记；§1 覆盖范围（27 / 23、账号 7 + 论坛 15 + 通知 3 + 处置 2）与受保护条加注；§2 五格重写 + 新增「CR-071 再生成」行；§3 三处标题（27 / 7 / 15）+ 三行新端点 + 三行非 200 口径；§4.3 四行字段约束；§6.3 两行表达极限 + 外壳数加注。**联动**：[技术方案](../技术方案.md) §5 新增 3 行并升 v0.7.16、`docs/README.md` §2 登记两版、AGENTS.md 单测基线 249 → 294。**遗留不变**：① `info.version` 仍为 `0.1.0`（老遗留）；② **注销账号（F-ACC-003）仍无任何端点**——设置页那一格是禁用按钮 + tooltip 占位，不得读成"注销已实现"；③ 他人主页 `/u/:id` 未实现（`GET /users/me` 只回自己）；④ `openapi.json` **仍无任何机器校验**，"重抓后 `git diff` 为空"只能靠人跑（[W-05](../../流程偏离记录.md) 不得关闭）。
- **v1.12.1（2026-09-19）——[CR-066](../../变更日志/变更台账.md#cr-066) 与 [CR-068](../../变更日志/变更台账.md) 提交后的「提交后核对」一并完成，`openapi.json` 快照文件零改动**：两条 CR 已随 **`f58ef73`** 提交并推送 origin/main，**`git status --porcelain` 为空 ⇒ 工作区即已提交源码**，v1.11（[CR-065](../../变更日志/变更台账.md#cr-065)）与 v1.12 两轮遗留的"代码来源为未提交工作区"敞口**同日关闭**。**核对方法（比 v1.10.1 收紧一档）**：本轮**不复用** `target/` 下的既有产物（其 jar 时间戳早于最近一次测试运行，"跑的就是已提交源码"这条证据不够硬），改为 `mvn -o package` **从该树重建**（同一命令顺带复验 **`Tests run: 249, Failures: 0, Errors: 0`**），再起**可执行 jar**、临时端口 **8097**、`APP_ROSTER_BYPASS=false`（8088 / 8089 一律未动），抓 `http://localhost:8097/api/docs`（⚠️ 必须用 `localhost`：springdoc 把 `servers` 回显成请求 Host，用 `127.0.0.1` 会多出一处伪 diff）、按 §2 命令格式化、`servers` 照 v1.6 / v1.10.1 先例逐字归一回 8088，结果与仓库快照 **68470 字节逐字节一致、sha256 同为 `28327bc89910d9c1…19266cfb`**，`git diff -- openapi.json` 为空；规模仍 **9 tag / 21 路径 / 24 操作 / 41 schema / 15 受保护操作**。**v1.11 的闭环口径如实标注**：本轮没有单独回退到 v1.11 的代码态重抓一次，核到的是"**当前树包含 v1.11 的全部契约改动、且其产物与 v1.12 快照逐字节一致**"这条推论——**两版的代码来源自此都是提交，不再是断言**。**外部行为冒烟（顺带，非本轮验收点）**：`/actuator/health` 200、`GET /api/v1/posts` 200、`?sort=hot` 200，三个受保护调用（名册导入 / 两个处置端点）匿名一律 `401 / 4001`。**收尾**：8097 实例已停止（`taskkill`），本机 Redis 按惯例保留在跑。**本文同步范围**：头部〈版本〉〈最后更新〉与变更登记补本轮；**§2**「代码来源」格加"已闭环"注、并新增 **CR-066 + CR-068 提交后核对**一行；§1 / §3 / §4 / §5 / §6 **一字未动**（形状未变即无同步义务）。**残余一条如实登记、不静默关掉**：`openapi.json` **仍无任何机器校验**——"重抓后 `git diff` 为空"这件事只能靠人跑、靠约定拦，把快照纳入机器校验（CI 起栈比对）属提测准入门欠账（[W-05](../../流程偏离记录.md) 无截止日期）。**遗留不变**：① `info.version` 仍为 `0.1.0`（老遗留）；② **CR-066 只有 API、页面无处置入口**，PRD F-SAFE-003 的用户可见验收与"作者收到通知"**均不签**；③ **F-FORUM-003 首页「最新 / 热门」切换 UI 仍未做**，不得声称双排序已上线。
- **v1.12（2026-09-18）——[CR-066](../../变更日志/变更台账.md#cr-066) 内容处置「下架 / 恢复」（F-SAFE-003 一支）落地后再生成**：新增 **2 个受保护 operation**（`PUT /api/v1/admin/posts/{id}/status`、`PUT /api/v1/admin/replies/{id}/status`），规模 **22 → 24 操作 / 19 → 21 路径 / 40 → 41 schema / 8 → 9 tag / 13 → 15 受保护**，文件 62546 → **68470 字节**。新 schema 只有一个：`ModerationCommand`（`status` 必填 + `pattern: PUBLISHED|REMOVED`，`reason` 可选 + `maxLength: 200`）；出参复用既有 `ApiResponseVoid` ⇒ **没顺手扩大出参契约面**。⚠️ **本轮最重要的方法论修正**：既有"删除行为零即纯新增"的判据**第一次失效**（+232 / **−16**），原因是新 operation 引用 `ApiResponseVoid` 触发 springdoc 的 schema 重排——改判据为"**逐行归因 + 结构化比对**"，实测 40 个既有 schema 与全部既有 operation **零变化**，仍属纯新增。**同步范围**：头部〈版本〉；**§1** 覆盖范围 22 → 24 并保留上一版数字；**§2** 生成时间 / 代码来源 / 规模 / 落盘处理 / diff 性质五行 + 新增「CR-066 再生成」行；**§3** 标题 22 → 24、**新增 §3.4 两行**（含"角色层无机器强制"的指针）、§3 末非 200 表补两行；§4 / §5 / §6 未动。**遗留**：① `info.version` 仍为 `0.1.0`（老遗留）；② **只有 API、页面无处置入口**，PRD F-SAFE-003 的用户可见验收与第三句"作者收到通知"**均不签**；③ **v1.11 + v1.12 两次「提交后核对」合并待做**；④ 新增流程缺口 E-011（"仅 SUPERADMIN"无机器强制，登记在问题清单、未处置）。
- **v1.11（2026-09-18）——[CR-065](../../变更日志/变更台账.md#cr-065) 帖子删除（F-FORUM-006 后端）落地后再生成**：新增 1 个**受保护** operation `DELETE /api/v1/posts/{id}`（tag `post`，`@SecurityRequirement` + `CurrentUser.requireId`），21 → **22** 操作、受保护 12 → **13**；**19 路径 / 40 schema / 8 tag 三项全部不变**——新操作挂在既有 `/api/v1/posts/{id}` 上，出参 `ApiResponseVoid` 已由 `accept` 产出，未新增任何模型（**"schema 数未变"即"没顺手扩大出参契约面"的自查依据**，沿用 v1.10 的提法）。`git diff --numstat` **+85 / −0**：删除行为零，既无 summary 改写、也**未复现 v1.9 的 `operationId` 漂移**（新操作自得 `delete`，`list` / `list_3` 等既有编号未动）；文件 60251 → **62546 字节**（+2295）。**抓取方式**：8088 / 8089 由并发会话占用，本轮在**临时端口 8099** 起 jar（`APP_ROSTER_BYPASS=false`），`servers` 照 v1.6 / v1.7.1 / v1.10 先例逐字归一回 8088。⚠️ **快照表达不了"这是软删"**：出参是空的 `ApiResponseVoid`，schema 里既无 `is_deleted` 也无 `deletedAt`，"写墓碑 / 不级联删楼层与互动行 / 同事务记 `POST_DELETE` 审计"三条只能在 §3.2 该行与[实施方案](../../开发/实施方案/CR-065-帖子删除.md)里读——**读快照得到的是形状，不是处置语义**。⚠️ **代码来源为「未提交的工作区」**（CR-064 的三个 `account` 文件 + CR-065 的四个 `forum` 文件都在其中），**「提交后核对」本轮未做**（发起人尚未授权提交），敞口如实登记在 §2 末行。**同步范围**：头部〈版本〉〈最后更新〉与变更登记补 CR-065；**§1** 覆盖范围 21 → 22、受保护清单补 `DELETE /posts/{id}`；**§2** 生成时间 / 代码来源 / 规模 / 落盘处理 / diff 性质五行 + 新增「CR-065 再生成」行；**§3** 标题 21 → 22、**§3.2** 新增一行（论坛 12 → 13）；**§3 末非 200 表**补 `400 / 401 / 403 / 404 / 500` 一行——`@ErrorCodes` 只声明 `NOT_LOGGED_IN` + `FORBIDDEN` + `NOT_FOUND`，`1001` / `9999` 由 `OpenApiErrorResponseCustomizer` 自动补（**沿用 v1.8 教训，不再显式写 `INVALID_PARAM`**，否则 description 重复）。§4 / §5 / §6 未动：本 CR 不新增主链路实测小节（真机 38 项取证记在[实施方案 §7](../../开发/实施方案/CR-065-帖子删除.md)），技术方案 §5 的接口表同步随该 CR 一并完成。**遗留**：① `info.version` 仍为 `0.1.0`（老遗留）；② **`DELETE` 只有 API、前端无入口**，PRD F-FORUM-006 的用户可见验收**不签**；③ §2 的"提交后核对"待 CR-064 / CR-065 提交后补做。
- **v1.10.1（2026-09-18）——[CR-058](../../变更日志/变更台账.md#cr-058) 与 [CR-060](../../变更日志/变更台账.md#cr-060) 的「提交后核对」一并完成，`openapi.json` 快照文件零改动**：CR-058 已随 **`286739b`** 提交（六条 CR 合一），CR-060 已提交为 **`8b37194`**，v1.10 遗留的"提交后核对"与 §2 长期挂着的"⚠️ 未提交的工作区"敞口**同日关闭**——快照的代码来源自此可追溯到提交。**核对方法与判定**：不起新构建、而是直接跑**已提交源码**的产物（`target/` 下 13:12 的 jar，其时间戳晚于全部 java 源文件、且 `git status --porcelain -- backend/src` 为空，两条一起构成"跑的就是 `8b37194`"的证据），临时端口 **8097** 起实例（`APP_ROSTER_BYPASS=false`，8088 / 8089 一律未动），抓 `http://localhost:8097/api/docs`、按 §2 命令格式化、`servers` 依 v1.6 / v1.6.1 / v1.7.1 / v1.9.1 先例逐字归一回 8088，结果与仓库快照 **60251 字节逐字节一致、sha256 同为 `c3116d02bc76…`**，`git diff --numstat` 为空；规模仍 **8 tag / 19 路径 / 21 操作 / 40 schema / 12 受保护操作**。**为什么 CR-060 也归"非再生成"**：它只把两条楼层写用例的读侧换成带 `status` 过滤的 `findVisibleById`，**不动 Controller / VO / `ResultCode` / 鉴权声明**——与 [CR-051](../../变更日志/变更台账.md#cr-051) 同类（v1.9.2 已就此立过一条纪律：**diff 为空不等于对外行为未变**，CR-060 的真实行为变化是"下架楼层由可点赞 / 可采纳变为 404 / 3001"，属**数据可见性判定**、不体现在 schema 上）。**顺带据实核掉一条遗留**：本次探测端口时发现 CR-058 记下的 **8095 临时实例（PID 28436 / maven 13524）此刻已不在监听**（8088 / 8089 / 8095 全无监听），那条"待发起人清理"可视为已消解，台账 §4 v1.60 同步。**本文同步范围**：头部〈版本〉〈最后更新〉与变更登记补 CR-060 一项；**§2**「本次生成时间」补"快照文件自 2026-09-16 起未再改动"、「代码来源」由未提交态更正为提交号、并新增 **CR-058 / CR-060 提交后核对**两行；§3 / §4 / §5 / §6 **一字未动**（形状未变即无同步义务）。**仍开放**：`info.version` 仍为 `0.1.0`（§7 老遗留）；**F-FORUM-003 首页「最新 / 热门」切换 UI 仍未做**——契约里有 `sort` 而页面不能选，不得声称双排序已上线。
- **v1.10（2026-09-16）——[CR-058](../../变更日志/变更台账.md#cr-058) 热榜定时任务（ADR-006）+ `GET /posts?sort=hot` 落地后再生成**：**契约规模零变化**——仍 **8 tag / 21 操作（19 路径）/ 40 schema / 12 受保护操作**，端点与模型一个未增未减；唯一变化是既有公开端点 `GET /api/v1/posts` **多一个 `sort` 查询参数**（`in: query` / `required: false` / `schema.type: string` / `default: "latest"`，插在 `boardCode` 与 `page` 之间）与该操作 `summary` 的一处据实改写（"排序固定 `created_at DESC`" → "`sort=latest`（缺省，`created_at DESC`）或 `hot`（`hot_score DESC`，热榜），非法值 400"）。`git diff --numstat` **+10 / −1**，**唯一的删除行即那条 summary**——按"见删除行必逐行判定"的纪律逐行看清后判定为**据实改写、非契约破坏**（路径 / 方法 / 出参 / `security` 全未变，仍是 `@PublicEndpoint`）。⚠️ **本次未复现 v1.9 的 `operationId` 漂移**：只加参数不加方法，`GET /api/v1/posts` 仍为 `list`、`GET /api/v1/boards` 仍为 `list_3`。**抓取方式（与历次不同，如实记）**：8088 上有并发会话的运行实例，为不动它，本轮在**临时端口 8095** 起 CR-058 代码抓取，再把 `servers` **逐字归一**回仓库既定值 `[{"url":"http://localhost:8088","description":"Generated server url"}]`——依据 v1.6 / v1.6.1 / v1.7.1 的同一先例（springdoc 把 `servers` 回显成请求 Host，属**抓取方式**差异、不是契约差异）。⚠️ **代码来源为「未提交的工作区」**：CR-058 尚未提交，提交后须按 v1.5.1 / v1.8.1 / v1.9.1 同例补做**「提交后核对」并回填提交号**（上一份可追溯到提交的快照为 `70c6ca9`）。**出参刻意不暴露 `hotScore`**：无产品需求（前端只消费顺序），暴露即扩大契约面并诱导前端自行排序——故 40 schema 一个未变，这也是"若 schema 数变了即说明越界动到出参"的自查依据。同步范围：**§2** 生成时间 / 代码来源 / 规模 / 落盘处理 / diff 性质五行；**§3.2** 帖子列表行的路径、摘要与鉴权列（公开读不因加参数而变）；**§3** 非 200 表 `GET /posts` 行补 `sort` 非法值这一新触发点（`1001` 由 `OpenApiErrorResponseCustomizer` 自动补，**不得再显式声明 `INVALID_PARAM`**，否则重犯 v1.8 记过的 description 重复）；**§5** 把 `sort=hot` 与 `hot_score` 从"已列但仍未实现"移入"已实现"。**真机取证不在此重复**：短周期刷新日志、`hot_score` 与手算公式对齐（相对误差 1.4e-8）、`sort=hot|latest` 顺序差、版块内热榜、分页 total 一致、非法值 400/1001、Redis 锁键前缀与抑制效果、CR-043/048/049/050 与匿名 401 回归，全部记在[实施方案 §7](../../开发/实施方案/CR-058-热榜定时任务与热门排序.md)与台账。单测 **164 → 185** 全绿（含 `ArchitectureGuardTest` 11 例：新增的 `infrastructure → application` 方向属 G1 放行，未触发断言）。**遗留**：① 快照的"提交后核对"待 CR-058 提交后补做；② `info.version` 仍为 `0.1.0`；③ **F-FORUM-003 首页"最新 / 热门"双排序本轮只完成后端一半**——前端切换 UI 由并发会话占用、本轮零前端改动，故契约里已有 `sort` 而页面尚不能选，属**已知缺口、归后续 CR**，不得声称"双排序已上线"。
- **v1.9.2（2026-09-15）——[CR-051](../../变更日志/变更台账.md) 时间戳时区口径修复：`openapi.json` 快照文件零改动，只改本文口径**：① **§1 新增「时间字段口径」条**——写明 `format: date-time` 的 `Z` 自本日起即为真 UTC、`Instant` 序列化与 `spring.jackson.time-zone` 无关、存储侧靠 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` 成对强制会话时区、由 `DataSourceTimezoneTest` 守护，并单列 `expiresAtEpochSecond`（epoch 秒、不经数据库，L-6 期间也一直正确）；② **§2 新增「CR-051 重抓核对」行**——改配置后按本节命令重抓，**`git diff` 为空**，故快照代码来源仍为 `70c6ca9`；③ **§4.7 与 §5 各加一条"已闭环"注记**（原叙述与修复前实测值 `2026-09-14T23:06:34Z` **保留不改写**，它们是 L-6 的证据）。⚠️ **一处必读的判定纪律**：快照 diff 为空 **≠** 时间口径没变——快照里不含取值，时间口径属**数据层**变化、不属**契约层**变化；因此本 CR 不触发 §5 技术方案接口表同步，只动本文口径条与历史记录处的加注。
- **v1.9.1（2026-09-15）——补记提交凭证与"提交后核对"结果，快照文件零改动**：CR-050 已提交为 `70c6ca9`；自**运行中后端**（`http://localhost:8088/api/docs`，`APP_ROSTER_BYPASS=false` 实例）重抓并按 §2 命令格式化，与仓库快照**逐字节一致**（59977 字节，并记录 `servers` 即为既定值 `http://localhost:8088`，无需回写）——v1.9 遗留的"提交后核对"就此完成，快照来源由"未提交的工作区"更正为**提交 `70c6ca9`**。本次未复现 v1.8.1 记过的 `servers` 回显差异（抓取即用 `localhost`）。
- **v1.9（2026-09-14）——[CR-050](../../变更日志/变更台账.md) 通知中心落地后再生成**：新建限界上下文 `module/notification`（第三个上下文），新增 **3 个受保护端点**（`GET /notifications?unread=&page=&size=` / `GET /notifications/unread-count` / `PUT /notifications/read-all`），18 → **21** 操作（19 条路径）/ 33 → **40** schema（+7：`NotificationVo`、`PageVoNotificationVo`、`UnreadCountResult`、`MarkAllReadResult` 与 3 个 `ApiResponse*` 外壳）/ tag 7 → **8**（新增 `notification`）/ 受保护操作 9 → **12**。⚠️ **本次 diff 非纯新增**：`git diff --numstat` **322 增 / 1 删**，删除行是 `GET /api/v1/boards` 的 `operationId` **`list_2` → `list_3`**——springdoc 按"方法名 + 同名次序"编号，`NotificationController.list` 加入后把 boards 的序号顶后一位；**判定为生成器编号漂移、非契约变化、无破坏性**（该端点路径 / 方法 / 入参 / 出参 / `security` 全未变，前端 `src/api/*` 手写、无代码生成环节），但这是历次再生成**第一次动到既有端点自身内容**，纪律随之收紧为"见删除行必逐行判定"。同步范围：**§1** 覆盖 18 → 21、受保护 9 → 12；**§2** 本次来源与规模、diff 性质；**§3** 新增 **3.3 通知上下文**表（含 `unread` 三态、读时组装、**`non_null` 使可空字段在前端是"可选"而非"null"**这条通用口径）与非 200 表 3 行（分页越界口径实测）；**§4.7** 新增通知中心真机实测（触发链 + 三端点 + 幂等 + 3 条匿名 401 + 非法入参的 401 / 400 分档，全部真实 `traceId`）；**§5** 通知行由"仍未实现"改为"已实现并取代原计划 `PUT /notifications`"，并对"时间 ISO 8601 UTC"补一条**实现与约定相反**的警示；**§6.3** `ApiResponse*` 具体类型 12 → 15。**真机才暴露的两处缺陷已修**：① `GET /notifications` 的 **`unread` 筛选反义**（入参 `unread` 与列 `is_read` 语义相反，适配器直接 `.eq(is_read, unread)`），改 `!unread` 并**新增适配层测试** `NotificationRepositoryImplFilterTest` 3 例（应用层 Mockito 结构上看不见列语义）；② 前端 `n.postId === null` / `n.floorNo !== null` 在 `non_null` 下恒不成立，渲染出「#undefined 楼」并跳 `/post/undefined`，改真值判断 + 类型改可选。**发现 1 处未修缺陷（不属本次范围、Sprint 2 起即存在）**：时间戳整体偏 **+8 小时**（V1 各表 `DEFAULT CURRENT_TIMESTAMP` 写本地墙钟 + JDBC `serverTimezone=UTC` 读成 UTC），已登记为问题台账条目与 **CR-051 候选**。单测 145 → **162** 全绿。快照自本机 8088 实例（`APP_ROSTER_BYPASS=false` 重启实例）抓取，`servers` 无需回写。**遗留**：① 快照的"提交后核对"须在 CR-050 代码提交后按 §2 方法补做（与 v1.3 / v1.5 / v1.7 同例）；② `info.version` 仍为 `0.1.0`；③ **`/notifications` 页像素级目视走查未完成**（内嵌浏览器视口 `0×0`，只做了 DOM 结构 / `getComputedStyle` / `scrollY` 取证），归 [W-03](../../流程偏离记录.md) 补偿③；④ 通知**无实时推送**（30s 轮询 + 切页补读），单条已读与 `quote` 类通知未实现。
- **v1.8.1（2026-09-13）——补记提交凭证与"提交后核对"结果，快照文件零改动**：CR-049 已提交为 `5cdcf93`；自**运行中后端**（8088 实例）重抓 `/api/docs` 并规范化后与仓库快照**逐字节一致**——v1.8 遗留的"提交后核对"就此完成，快照来源由"未提交的工作区"更正为**提交 `5cdcf93`**。**过程如实记**：首次以 `http://127.0.0.1:8088` 为 Host 抓取时，springdoc 会把 `servers` 回显为请求 Host（`http://127.0.0.1:8088`），与快照仅此一处不同；改以 `http://localhost:8088` 重抓即完全一致——这是 springdoc 回显请求 Host 的行为，**不是契约变化**，快照文件从未因此变动。
- **v1.8（2026-09-13）——[CR-049](../../变更日志/变更台账.md) 站内搜索落地后再生成，契约纯新增**：新增**公开**端点 `GET /api/v1/posts/search`（5 个 query 参数：`keyword` 必填 2~50 字、`boardCode`、`days` 仅 7/30/90、`page`、`size`；出参复用 `PageVo<PostSummaryVo>`），17 → **18** 端点；**schema 数未变**（33）、**tag 数未变**、**`security` 声明未变**（受保护操作仍 9 个）；`git diff` **98 行新增 / 0 行删除**。本次直接自 8088 实例抓取，`servers` **无需回写**（不同于 v1.6 / v1.7 的 8093 / 8094 变通）。**再生成过程中发现并修正一处自己引入的契约瑕疵**：`@ErrorCodes` 里显式写了 `INVALID_PARAM`，而 `OpenApiErrorResponseCustomizer` 本就会自动补通用 400，导致 400 的 description 变成 `参数错误（1001）；参数错误（1001）`——已删去显式声明（只留 `NOT_FOUND`），重编译重启后重抓确认归一为单条 `参数错误（1001）`；旧快照零重复，属本次新增端点独有。并同步本文：**§1** 覆盖范围 17 → 18（论坛 11 → 12）；**§2** 换成本次来源与规模；**§3** 标题与 §3.2 表新增搜索行（含 `ngram_token_size` 决定 2 字下限的约束说明）、非 200 表新增一行；**§4.6** 新增站内搜索实测（PRD 验收主用例 + 相关度优先于时间的取证 + 校验矩阵 + 两处临时置数的还原确认 + 回归面）；**§5** `GET /search` 由"仍未实现"划掉并标注落点。**顺带更正两处陈旧计数**（均只改本文、快照文件从未因此变动）：tag 数自 CR-048 起实际为 **7**（本文自 v1.3 起一直写 6）、`ApiResponse*` 具体类型自 CR-048 起实际为 **12**（v1.7 仍写 11）。**遗留**：① 快照的"提交后核对"须在 CR-049 代码提交后按 §2 方法补做（与 v1.3 / v1.5 / v1.7 同例）；② `info.version` 仍为 `0.1.0`；③ **`tags` 侧只有 `LIKE` 兜底、无真实数据来源**（发帖链路从未建模标签，详见 §4.6 的如实说明）；④ 前端**宽屏 / 窄屏目视走查未完成**（内嵌浏览器视口 `0×0`，只做了 DOM 结构校验），归 [W-03](../../流程偏离记录.md) 补偿③。
- **v1.7.1（2026-09-13）——补记提交凭证与"提交后核对"结果，快照文件零改动**：CR-048 已提交为 `c510493`（同批含 CR-046 / CR-047 文档）；自**运行中后端**（8094 临时端口实例，Redis 已另行启动）重抓 `/api/docs` 并按 §2 命令格式化、`servers` 归一回 `http://localhost:8088`，与仓库快照**逐字节一致**——v1.7 遗留的"提交后核对"就此完成，快照来源由"未提交的工作区"更正为**提交 `c510493`**。
- **v1.7（2026-09-13）——[CR-048](../../变更日志/变更台账.md) 点赞收藏落地后再生成，契约纯新增 + 出参扩展**：新增 4 个受保护端点——`POST /posts/{id}/like`、`POST /posts/{id}/favorite`、`POST /posts/{postId}/replies/{replyId}/like`（三者均为 **toggle**，响应 `InteractionResult{active, count}`）与 `GET /favorites/mine`（`PageVo<PostSummaryVo>`），13 → **17** 端点 / 31 → **33** schema（新增 `InteractionResult`、`ApiResponseInteractionResult`）；两个既有 VO 扩展字段：`PostDetailVo` +`likedByMe` +`favoritedByMe`、`ReplyVo` +`likeCount` +`likedByMe`（匿名访问详情 / 楼层列表仍 200，登录态回显字段恒 false）。快照自 8094 临时端口实例抓取（8088 被旧栈占用），`servers` 回写既定值；与 v1.6 比对核对差异恰为上述内容，无意外漂移。§1 / §2 / §3.2 / 非 200 表 / §5 已同步。**遗留**：提交后核对待 CR-048 提交后补做（→ v1.7.1 已完成）；`info.version` 仍为 `0.1.0`。
- **v1.6.1（2026-09-13）——补记提交凭证与"提交后核对"结果，快照文件零改动**：CR-043 已提交为 `493ef87`；自**运行中后端**（8094 临时端口实例，8088 仍被旧版进程占用）重抓 `/api/docs` 并按 §2 命令格式化、`servers` 归一回 `http://localhost:8088`，与仓库快照**逐字节一致**（唯一原始差异即 `servers` 抓取端口，归一后零差异）——v1.6 遗留的"提交后核对"就此完成，快照来源由"未提交的工作区"更正为**提交 `493ef87`**。
- **v1.6（2026-09-13）——[CR-043](../../变更日志/变更台账.md) 采纳最佳答案落地后再生成，契约纯新增**：新增 `POST /api/v1/posts/{id}/accept`（受保护，`AcceptReplyCommand` 请求体）与 schema `AcceptReplyCommand`，12 → **13** 端点 / 30 → **31** schema；三个既有 VO 扩展字段：`PostSummaryVo` +`accepted`、`PostDetailVo` +`boardType` +`authorId`、`ReplyVo` +`authorId` +`accepted`；楼层列表排序改为最佳答案置顶。快照由 8093 端口实例抓取（8088 被旧版进程占用），`servers` 回写既定值，见 §2 方法学备注。§3.2 / 非 200 表已同步，§5 中 `PUT /{id}/accepted-reply` 计划项由本实现取代。
- **v1.5.1（2026-09-13）——补记提交凭证与"提交后核对"结果，快照文件零改动**：CR-031 已提交为 `7720adc`；自**运行中后端**（`http://localhost:8088/api/docs`）重抓并按 §2 命令格式化，与仓库快照**逐字节一致**（`git diff --no-index` 零差异）——v1.5 遗留的"提交后核对"就此完成，快照来源由"未提交的工作区"更正为**提交 `7720adc`**（`info.version` 仍为 `0.1.0`）。一处方法学备注：核对须抓**配置的契约入口 `/api/docs`**——springdoc 默认的 `/v3/api-docs` 在本项目返回的是另一份更小的文档，不是快照的事实来源。
- **v1.5（2026-09-12）**——配合 [CR-031](../../变更日志/变更台账.md#cr-031)（**路径级鉴权落地**：`SecurityConfig` 去 `permitAll()`，`EndpointAuthorizationManager` 按端点注解在过滤器链内裁决）**再生成快照**：**端点 / schema / tag / `security` 声明全部未变**（仍 12 端点 / 30 schema / 6 tag / 4 个受保护操作），`git diff` **仅 1 行**——`components.securitySchemes.bearerAuth.description` 据实改写（改为"框架按端点注解裁决、`module` 未声明 fail-closed、未登录由过滤器链产出 401 / 4001"，并注明角色与资源级授权仍归 `CurrentUser`）。并同步本文：**§1** 改述为"**路径级拦截已由 CR-031 落地**"；**§2** 换成本次来源（**仍是未提交的工作区**，`HEAD` = `c806153`，时刻核验：落盘 22:29:12 晚于 `backend/src/main` 最后的 mtime 22:26:32）与规模（18481 → 36529 字节），触发点⑥改为**已完成**、新增 ⑩（改 `EndpointAuthorizationManager` 裁决口径后须重抓）；**§3** 两张表第 8 列对 4 个受保护端点补"**框架层前置拦截 + 业务层同码兜底**"两层描述、`admin/roster/import` 行如实标注 403 支**未在真机复现**（单测覆盖），N-4 说明段重写为"**三层机制 + 两条边界**"；**§4.5** 新增 **⑫ CR-031 复测段**（匿名非法 body / 匿名合法 body / 伪造 token 三条真实 `traceId`；405 / 404 / 公开端点对照）并把原 🔍 注记改写为**行为变化说明**（受保护端点"匿名 + 非法 body"由 `400 / 1001` 前置为 `401 / 4001`，公开端点不受影响）；**§6.2** N-4 行补"路径级强制已由 CR-031 闭环"，尾注与 §6.3 当前状态改为"声明 / 机器校验 / 运行时拦截三层齐备"。**遗留**：快照的"提交后核对"须在 CR-031 代码提交后按 §2 方法补做（与 v1.3 / v1.4 同）；`info.version` 仍为 `0.1.0`。【本快照的端点契约与 v1.4 **完全一致**，若前端只关心接口形状，v1.4 → v1.5 **无破坏性变更**】
- **v1.4（2026-09-12）**——配合 [CR-028](../../变更日志/变更台账.md#cr-028)（B4 / B5：A3-9 架构守护测试 + A3-10 文档-代码一致性）**再生成快照**：**端点 / schema / tag / `security` 声明全部未变**（仍 12 端点 / 30 schema / 6 tag / 4 个受保护操作），`git diff` **仅 1 行**——`components.securitySchemes.bearerAuth.description` 据实改写（原描述说"实际强制点是 Controller 内校验"，现改为指向 `CurrentUser` 统一入口 + `ArchitectureGuardTest` 机器校验，并写明 `SecurityConfig` 仍为 permitAll）。并同步本文：**§1** N-4 改述为"**漏写缺口已闭环**、路径级拦截仍未做"；**§2** 换成本次来源（**仍是未提交的工作区**，`HEAD` = `e11a2a2`）与规模（18290 → 36338 字节），diff 性质改为"单行文案改动"，触发点⑥改为"迄今未发生"、新增 ⑧（增删端点声明 / 改 `CurrentUser` 语义）与 ⑨（本次已完成）；**§3** 两张表的第 8 列由"Controller 内手工判空 / `AdminRosterController.requireSuperadmin`"改为 **`CurrentUser.requireId` / `requireRole`** 统一入口，N-4 说明段重写为"已闭环 + 两条残留事实 + 历史沿革"；**§4.5** 🔍 行为顺序注补"位置不变、仍为方法体内"，并补 `GET /users/me` 无此先后问题的说明；**§6.2** 由"仍开放"改写为"**已处置**"（N-4 / N-5 / N-6 逐条给处置与复测结论，N-4 记录**负向对照实验**证据：注入 3 个违规探针 → 守护测试 7 例失败、删除后全绿）；**§6.3** 尾注由"N-4 未闭环更危险"改写为"六项全部处置，N-4 闭环的是漏写而非框架拦截"。**遗留**：快照的"提交后核对"须在 CR-028 代码提交后按 §2 方法补做（与 v1.3 同）；`SecurityConfig` 的路径级拦截仍未做（**不属 N-4 范围**，[CR-028](../../变更日志/变更台账.md#cr-028) 未承诺）；`info.version` 仍为 `0.1.0`。【本快照的端点契约与 v1.3 **完全一致**，若前端只关心接口形状，v1.3 → v1.4 **无破坏性变更**】
- **v1.3（2026-09-12）**——配合 [CR-022](../../变更日志/变更台账.md#cr-022) 的 Sprint 2 MVP（6 个论坛端点）**再生成快照**（**12 端点 / 30 schema**；18185 → 36233 字节），并同步本文：**§1** 覆盖范围由"6 个"改为"**12 个**（账号 6 + 论坛 6）"，受保护操作由 2 个改为 **4 个**；**§2** 换成本次的来源与规模，**如实标注代码来源仍是未提交的工作区**（与 v1.1 的差别：v1.1 事后补齐了"对应 `63ed21c`"，本次**无法**声称对应任何提交，只有"快照落盘时刻晚于 `backend/src/main` 全部 mtime"的时刻核验），新增触发点⑦与"① 是高频触发点"的提示；**§3** 拆为 3.1 账号（原表原样保留）/ **3.2 论坛**两张表，非 200 响应表新增 6 行，N-4 说明段补"Sprint 2 的三个新防线"与两条**文档-代码差异**（`{postId}` vs `{id}` 路径参数名、`accepted` vs `isAccepted`）；**§4** 证据分档增【Sprint 2 实测】一档，新增 **§4.5 论坛主链路**（11 条实测响应：6 条成功 + 5 条错误，含两个受保护端点的 401）+ 一条实测发现的**行为顺序**（参数校验先于登录态校验，故"匿名 + 非法 body"返回 400 而非 401）；**§5** 差异表重写为五类（已实现 / 已补入 / 计划项转已实现 / 仍未实现 / 无对应表达）；**§6.2** N-4 补"人工处置但机制未闭环"、N-5 补"差距又扩大一次"；**§6.3** `ApiResponse*` 具体类型 5 → **11**。**遗留**：N-4（→ A3-9，**A3-9 已被推迟至 MVP 验收后**）、N-5（→ A3-10）、N-6（P3，未修）；快照的"提交后核对"须在本轮代码提交后按 §2 方法补做；`.openapi` 的 `info.version` 仍为 `0.1.0`、**未随 Sprint 递增**，本轮未改（是否引入版本策略归 A3-8 / 部署形态讨论）。
- **v1.2（2026-09-12）**——**补记提交凭证，无内容变更**：把"代码来源为未提交的工作区"改为**来源即提交 `63ed21c`**（CR-020 与 CR-021 合并为一次提交）。依据为**文件时间戳核验**：本快照导出（01:24:21）之后 `backend/src/main` **零改动**，唯一变动是 01:29 新增的测试类 `AdminRosterControllerAuthTest`，而快照只由生产代码与注解生成、测试类不影响其内容，故 §2 原要求的"提交后重抓一次比对"**已由该证据解除、无需执行**。快照文件本身与 §1 / §3 / §4 / §6 的实测内容**一字未改**。
- **v1.1（2026-09-12）**——配合 [CR-021](../../变更日志/变更台账.md#cr-021) 修复 N-1 / N-2 / N-3 后**再生成快照**（6 端点 / **14** schema，新增 `ApiError`；9108 → 17545 字节），并同步本文：**§1** 增"快照现在也表达错误响应与鉴权，但声明 ≠ 强制"一条；**§2** 换成本次的来源与规模，并**如实标注代码来源是未提交的工作区**（v1.0 能声称对应提交 `68d40ae`，本次不能），补 diff 纯新增性的脚本比对结论与两条新触发点（改 `ResultCode`、`SecurityConfig` 收紧后）；**§3** 鉴权列拆为"快照声明"与"运行时强制方式"两列，新增各端点非 200 响应清单表，并把原"鉴权为什么不在快照里"整段（已过期）改写为 **N-4**；**§4** 证据分档增【CR-021 实测】一档，新增 **§4.4 错误响应实例**（7 条实测响应体 + 与快照的对应关系 + 日志档位）；**§6** 重构为三小节：6.1 已修的三项（含修复方式与复测结论）、6.2 仍开放的 **N-4 / N-5 / N-6**、6.3 OpenAPI 固有表达极限（新增 `405`/`404`/`415` 三行），删掉"N-1/N-2/N-3 均未处置"的过期结论。**遗留**：N-4（→ A3-9）、N-5（→ A3-10）、N-6（P3，未修）；本 CR 代码已于 2026-09-12 提交为 `63ed21c`，且经文件时间戳核验快照与该提交一致（`backend/src/main` 在导出后零改动），**原要求的"提交后重抓一次"已解除**；本次**未在浏览器目视 UI 回归**（改经 Vite 代理实测 + 审阅 `client.ts`，详见 [CR-021](../../变更日志/变更台账.md#cr-021) 复测结果⑦）。
- v1.0（2026-09-11）——创建。归档 `openapi.json` 快照（提交 `68d40ae`，6 端点 / 13 schema），补 2 条主链路的实测请求响应结构、与技术方案 §5 的逐条差异、快照未表达的 5 项内容（含新发现缺陷 N-3）。闭环设计门 **A3-4**，登记为 [CR-019](../../变更日志/变更台账.md#cr-019)。**设计门出口标准第 2 项仍为 ❌**（A3-5 ER 图与数据量级预估未做）。
