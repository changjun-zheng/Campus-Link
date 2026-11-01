# CR-181 · 搜索容量优化：MeiliSearch 正文检索（10 万帖 P95 ≤ 500ms）

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.1 |
| 状态 | 已实施（2026-11-01） |
| 维护人 | AI 会话（2026-11-01） |
| 最后更新 | 2026-11-01（实施结果见 §7） |
| 关联 CR | CR-181（本方案） · 输入 [CR-180](../变更日志/变更台账.md#cr-180)（搜索改写实测退化并回退） |

## 1. 目标

把 CR-180 未达标的搜索（10 万帖、关键词命中全库时 P50 **40.7s** / P95 **41.4s**，基线 12.7s）压到 **P95 ≤ 500ms**，保持 `/api/v1/posts/search` 路径 / 参数 / 响应外壳与「标题 + 正文 + 标签」三路命中不变；兑现 **ADR-004**「P1 正文全文搜索引入 MeiliSearch」。

## 2. 范围

- **做**：
  1. 引入 **MeiliSearch**（开发期本机 `127.0.0.1:7700`；**不新增 Maven 依赖**，用 Spring `RestClient` 直连 HTTP）。
  2. domain 新增 `PostSearchGateway` 端口，infrastructure 新增 `MeiliPostSearchAdapter`；MySQL 原搜索保留为 **fallback**。
  3. 索引同步：启动全量 + `@Scheduled` 增量（按 `posts.updated_at` + id 游标，Redis 存游标与锁，丢失则全量重建），周期 30s；只索引 `PUBLISHED + is_deleted=0`，删除 / 下架经 `updated_at` 捕获后从索引删除。
  4. 配置：`campuslink.search.engine=mysql|meilisearch`（默认 `mysql`）、`campuslink.search.meili.url`、`api-key`（env）、同步周期；Meili 异常自动回落 MySQL 并记 WARN。
  5. 文档：技术方案 §1.1 / §2.3 / §5、ADR-004 状态注、部署文档补 `meilisearch` 服务；台账 / README / 驾驶舱。
- **不做**：不改接口路径 / 参数 / 响应外壳；不改前端；不引入 Elasticsearch；不索引回复 / 组队 / 用户；不做发布后实时索引（≤30s 可见，如需即时另立小 CR）；不替换 MySQL fallback。
- **需要发起人批准的边界**：MeiliSearch 是**新增外部服务**（需本机安装二进制、部署期新增一个进程 / 容器），且 `total` 改由 Meili 的 `estimatedTotalHits` 提供（估算值，可能略偏离精确值；前端分页与空态不变）。若不接受新服务，见文末「备选」。

## 3. 改动点（文件级）

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/pom.xml` | 不改 | Spring `RestClient` 已在 spring-web |
| `backend/src/main/resources/application.yml` | 改 | `campuslink.search` 配置段（engine / meili / sync） |
| `backend/src/main/java/com/campuslink/config/AppProperties.java` | 改 | 绑定 search 配置（非 `@Scheduled` 项） |
| `backend/src/main/java/com/campuslink/config/ProductionSafetyGuard.java`（+ Test） | 改 | prod 下 `engine=meilisearch` 时 api-key 不得为空 / 不得用开发默认 |
| `.../module/forum/domain/gateway/PostSearchGateway.java` | 新增 | 搜索端口：关键词 / board / days / 排除作者与帖子 / 分页 → 有序 id + 估算 total |
| `.../module/forum/infrastructure/search/MeiliPostSearchAdapter.java` | 新增 | HTTP 搜索 / upsert / delete / 索引配置（filterable、sortable、rankingRules 含 `created_at:desc`） |
| `.../module/forum/infrastructure/search/PostSearchIndexScheduler.java` | 新增 | `@Scheduled` 增量 + 启动全量 + `ScheduledLockGateway` 锁 |
| `.../module/forum/application/SearchIndexApplicationService.java` | 新增 | 读增量批次、组装索引文档、调用端口；全量重建入口 |
| `.../module/forum/application/ForumQueryApplicationService.java` | 改 | `searchPosts` 走端口；engine=mysql 保留原路径；Meili 异常降级 |
| `.../module/forum/infrastructure/persistence/mapper/PostMapper.java` | 改 | 新增 `findChangedSince`（窄列）与 `findSearchDocumentsByIds`（窄列） |
| `.../module/forum/infrastructure/persistence/PostRepositoryImpl.java` | 改 | 增量 / 按 id 取文档；fallback 保留 |
| `backend/src/test/.../forum/...` | 新增 / 改 | adapter（`MockRestServiceServer`）、同步游标、可见性过滤、fallback、ArchUnit |
| `docs/设计/技术方案.md` · `docs/设计/接口契约/README.md` | 改 | 搜索实现口径 / Meili 状态 / `total` 估算说明；契约形状不变，快照零 diff |
| `docs/README.md` · `docs/变更日志/变更台账.md` · `docs/进度驾驶舱.html` | 改 | 登记与快照同步 |

## 4. 步骤

- [ ] 1. 起 MeiliSearch（dev 二进制，`127.0.0.1:7700`，master key 走环境变量；**下载 / 安装需发起人同意**）→ 配 index 的 `filterableAttributes` / `sortableAttributes` / `rankingRules`。
- [ ] 2. 加端口与 adapter（search / upsert / delete / ensureIndex），单测用 `MockRestServiceServer`。
- [ ] 3. 索引同步：全量批 1000；增量按 `(updated_at,id)` 游标（Redis 逻辑键 `search:sync:cursor` + 锁）；可见性变化 upsert / delete。
- [ ] 4. `ForumQueryApplicationService.searchPosts` 走端口；`engine=mysql` 保留原路径；Meili 失败降级 WARN（不 500）。
- [ ] 5. 单测 / ArchUnit / 契约零 diff / 真机三路命中 + `board`/`days` + 拉黑 / 举报排除 + 删除后 ≤30s 消失。
- [ ] 6. 容量：`seed_capacity.py --apply` 灌 10 万帖 → 全量索引 → `run_api_perf.mjs --reads 100`；达标后 `--cleanup` + `OPTIMIZE TABLE posts`；文档同步。

## 5. 验证

- **容量**：10 万帖、关键词命中全库时 search **P95 ≤500ms**（同口径并发 10）；latest / 详情 / 回复页不回归。
- **功能**：标题 / 正文 / 标签三路命中；`board` / `days` 过滤；拉黑作者 / 举报帖排除；删除 / 下架后 ≤30s 从搜索结果消失。
- **降级**：停掉 Meili，搜索回落 MySQL（功能可用、性能退化），不返回 500。
- **门禁**：`mvn verify` 全绿（基线按实际 +N，只改 AGENTS 一处）、契约零 diff、无新 Flyway 迁移（游标在 Redis）。
- **已知口径**：`total` 为 Meili `estimatedTotalHits`（估算值）；搜索语义由 MySQL ngram 切到 Meili 的中文分词 + 相关度，标题 2× 权重的旧口径不再适用（技术方案同步注明）。

## 6. 回滚

- 配置切回 `campuslink.search.engine=mysql`（默认值）即可停用 Meili，无需回滚数据；Meili 服务可停用。
- adapter / 同步器 / 端口为单提交，可整体 revert；Redis 游标删键即可，无 DB 结构变更。

## 备选（发起人不接受新服务时）

改走**有界 SQL 检索**：最近 2000 条命中内相关度排序、`total` 上限 2000，能进 500ms 但改变搜索语义（截断与排序口径）；需另立 CR-181A 并先改 PRD / 技术方案，不在本方案实施。

## 7. 实施结果（2026-11-01）

**结论**：已实施并达标。10 万帖搜索 P95 **40.23ms**（CR-179 基线 12,722ms），全部读接口 ≤160ms、0 错误。

| 计划项 | 实际 |
|---|---|
| MeiliSearch | v1.12.8，本机 `127.0.0.1:7700`；二进制在 `D:/Workspace/github_repos/meilisearch/`（**不入仓**） |
| 端口 / 适配器 | `PostSearchGateway` + `PostSearchIndexGateway` + `SearchSyncStateGateway`；`MeiliPostSearchAdapter` 用 Spring `RestClient` 直连，**零 Maven 依赖** |
| 索引同步 | 启动 / 游标缺失全量 **100,025 条 / 44.9s**；30s 增量（`(updated_at,id)` 游标 + Redis 锁）；新建 / 编辑 / 删除 / 管理员下架（REMOVED）真机验证 |
| 路由 / 降级 | `engine=meilisearch` 走 Meili；停 Meili 实测回落 MySQL 200；恢复后自动继续 |
| 配置 / 守卫 | `engine` 缺省 `mysql`；prod + meilisearch 无 key 时 `ProductionSafetyGuard` 启动即失败 |
| 测试 | 新增 15 例（adapter 4 / 同步 2 / Redis 3 / 路由 3 / guard 3）；`mvn verify` **758 全绿 / 5 skipped**，契约零 diff |
| 容量清理 | 100,117 → **115 帖 / 85 楼**；Meili 索引重建 24 条可见帖；8099 与 Meili 已停 |

**偏差与修复**：
- Meili settings 只接受 PATCH，`SimpleClientHttpRequestFactory` 底层 `HttpURLConnection` 不支持 → 改 `JdkClientHttpRequestFactory`（JDK HttpClient）。
- 默认拼写容错把「分页」以 1 个 typo 误配「分数」→ 索引设置 `typoTolerance.enabled=false`，与 MySQL 精确子串语义对齐。
- 调度器每 10s 调 `ensureIndex` 会反复 PATCH 设置 → adapter 进程内缓存，只在启动 / 首次同步做一次。
- 调度线程池 1 → 2：搜索全量重建不再阻塞热榜刷新。
- SpotBugs 新增 15 条 `EI_EXPOSE_REP/REP2`（DI 与不可变载体），按既有四组规则在 `spotbugs-exclude.xml` 逐条登记理由。
- `total` 由 Meili `estimatedTotalHits` 提供（估算值），技术方案已注明；接口形状未变。
