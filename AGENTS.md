---
name: Campus-Link
description: 重庆工程学院计算机专业学生交流论坛（Spring Boot 4.1 + Java 21 + Vue 3 单仓库，企业流程 BRD→PRD→设计→开发，单人 + AI 协作）
---

# AGENTS.md — Campus-Link 项目协作指引

编码代理在本工作区工作的上下文入口。**沟通用中文；代码、命令、变量名、文件路径保持英文。**

> 本文件只写**当前有效的规则与事实**，不记录沿革——"某结论何时成立、为什么"看 [`docs/变更日志/变更台账.md`](docs/变更日志/变更台账.md)。**项目状态只维护台账 §1 + [进度驾驶舱](docs/进度驾驶舱.html) 两处**（CR-029 收口规则），本文件不复制状态。

## 首要阅读（新会话启动顺序）

1. **本文件** —— 工作约定与红线；
2. **[`docs/下一步行动.md`](docs/下一步行动.md)** —— "现在该干什么"（阻塞项 / 并行项 / 三件事）；
3. **[`docs/进度驾驶舱.html`](docs/进度驾驶舱.html)** —— 进度 / 问题 / 待办聚合视图（浏览器打开；数据可能滞后，以事实源文档为准）；
4. 专项文档按「必读文档」表**按需加载**，不要一次读完全部。

端口、命令、开关以「常用命令」为准；核对本地实际配置前不要假设通用约定。

## 项目定位

- **产品**：面向重庆工程学院计算机专业学生的垂直交流论坛（固定 6 版块、Markdown 帖子与代码高亮、最佳答案采纳、通知与搜索、**学籍核验注册**——限本校，学号 + 姓名比对）；
- **技术栈**：后端 Spring Boot 4.1 + Java 21 + MyBatis-Plus 3.5.17（Boot4 starter）+ MySQL 8 + Redis 7；前端 Vue 3 + Vite + TS + Pinia + Element Plus（按需自动引入）；
- **架构**：模块化单体 + DDD 四层（ADR-012），限界上下文 `module/{account,forum,...}`；
- **流程**：企业七阶段流程，基线级变更走 CR 台账（一行式）；单人项目 + AI 协作。

## 仓库结构

- 根目录即**唯一** git 仓库（默认分支 `main`），git 操作在根目录进行；
- `docs/` —— 流程手册与全部项目文档；`backend/` —— Spring Boot；`frontend/` —— Vue 3。

## 必读文档（按需加载）

| 文档 | 什么时候读 |
|------|-----------|
| [`docs/README.md`](docs/README.md) | **文档版本号唯一登记处**（§2）+ 阶段门状态表（§1）；新增文档必须在此登记 |
| [`docs/下一步行动.md`](docs/下一步行动.md) | 动工前：阻塞项、并行项、当前该做的三件事 |
| [`docs/变更日志/变更台账.md`](docs/变更日志/变更台账.md) | 任何基线级变更**登记前**；查某次变更的来龙去脉（§1 汇总表先看） |
| [`docs/流程偏离记录.md`](docs/流程偏离记录.md) | 涉及流程裁剪 / 偏离时（W-xxx / T-xxx 单一登记处） |
| [`docs/流程手册.md`](docs/流程手册.md) | 流程规则本身：4.2 变更登记、4.5 文档归档、5.1 裁剪指南 |
| [`docs/设计/技术方案.md`](docs/设计/技术方案.md) | 技术选型与 **ADR-001~012**；接口清单与错误码分段见 §5 |
| [`docs/设计/接口契约/README.md`](docs/设计/接口契约/README.md) | 改接口 / 错误码 / 鉴权**之后**重生成快照（§2 命令与现行版本） |
| [`docs/设计/UI规范/界面规范.md`](docs/设计/UI规范/界面规范.md) · [`前端界面素材选型.md`](docs/设计/UI规范/前端界面素材选型.md) | 新增 / 修改前端页面前：令牌表、组件与交互约定；**引入任何外部素材（组件库 / 图标 / 字体 / 插画 / 动效）前先查选型文档 §3「禁区汇总」** |
| [`docs/开发/实施方案/说明与模板.md`](docs/开发/实施方案/说明与模板.md) | 要做「够大」的改动前：先写 `CR-xxx-主题.md` 并**停下等确认** |
| [`docs/设计/数据模型ER图与量级预估.md`](docs/设计/数据模型ER图与量级预估.md) | 查表结构 / 字段 / 量级时（以 V1 迁移为唯一事实源） |
| [`docs/开发/开发测试账号清单.md`](docs/开发/开发测试账号清单.md) | 需要开发测试账号时（45 个合成账号） |
| [`docs/需求/产品需求文档.md`](docs/需求/产品需求文档.md) | 需求基线（学籍核验 F-ACC-004 为 P0） |
| [`docs/评审/`](docs/评审/增量PRD评审纪要.md)（立项 / 需求 / 设计门纪要 · 技术方案追认纪要 · 增量PRD评审纪要 · 部署与机审选型决策材料） | 追溯评审结论与行动项（A1-*/A2-*/A3-*、F-1~F-7、R-1~R-3、七份增量 PRD 草案结论） |

## 常用命令

> **开发期依赖栈用本机原生服务，不用 Docker**（`docker-compose.dev.yml` 留给发布阶段）。
> Redis：`D:\Workspace\TechResources\Redis\Redis-8.6.2-Windows-x64-msys2-with-Service\redis-server.exe`（在自身目录下以 `redis.conf` 启动，127.0.0.1:6379，无密码）。
> MySQL：`127.0.0.1:3306`，库 `campuslink`（开发账号 `campuslink/campuslink`）。**表结构不手工导入**——Flyway 启动时自动迁移，仅首次需要先建库（见下）。
> ⚠️ **原生 CLI 核对时间口径前必须先 `SET SESSION time_zone='+00:00';`**——CLI 会话默认 SYSTEM 时区，`timestamp` 列按会话时区换算、`datetime` 按字面读，混看会把正确行为读成"+8 偏移缺陷"。

**backend/**（构建需 **JDK 21**：cmd.exe 下 `set "JAVA_HOME=D:\develop\Java\jdk-21"`；git-bash 下须改写为 `export JAVA_HOME="D:/develop/Java/jdk-21"`——`set` 不导出环境变量）：

```bash
# 仅首次：库必须先存在，Flyway 才能连上
mysql -h127.0.0.1 -uroot -p -e "CREATE DATABASE IF NOT EXISTS campuslink DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

mvn verify            # 编译 + 单测 + 架构守护 + jacoco 行覆盖 80% check + SpotBugs 静态扫描；合码前必须全绿
                      # 当前基线 537（默认 mvn verify 有 5 例 SKIPPED：CR-090 的 4 例 + CR-106 的契约快照真身比对 1 例；CI 与本机 SPRING_IT=true 时 0 SKIPPED 全跑）——改基线数只改本注释这一处
mvn spring-boot:run   # 启动：8088（SERVER_PORT 可覆盖）；健康检查 /actuator/health，OpenAPI /api/docs

# 生产 profile：5 个凭据变量一个都不能省（各 ≥32 字节，HASH / CRYPT 两把不得相同），
# 漏配任一 → 启动即失败（config/ProductionSafetyGuard 抢在数据源之前逐条点名）。
SPRING_PROFILES_ACTIVE=prod JWT_SECRET=… APP_HASH_KEY=… APP_CRYPT_KEY=… DB_USER=… DB_PASSWORD=… \
  mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8098"
```

**frontend/**：

```bash
npm install
npm run dev           # 5173，/api 代理到 8088（VITE_API_TARGET 可覆盖）
npm run build
npm run lint          # ESLint 静态扫描（flat config，见 eslint.config.js）；合码前必须 exit 0
```

**联调冒烟**：新链路合入前必须真实起栈（MySQL / Redis / 后端 / 前端）并**浏览器实测**，不能只依赖单测。主链路：注册（学籍核验 → 验证码）→ 登录 → 发帖（Markdown）→ 回帖 → 采纳 / 点赞 / 收藏 / 搜索 / 通知；**匿名 `POST /api/v1/posts` 必须 401**。

**开发测试数据**：

- **验证码**默认固定 `123456`（`campuslink.captcha.fixed-code`；留空恢复随机 6 位，`CODE_SENDER_MODE=log` 下只打日志，行格式 `[DEV] captcha for <邮箱> => <6位码>`）；
- **测试名册**（仅 `campuslink.roster.bypass=true` 生效，学号 9 位数字）：`249971346/张三` ~ `249971349/赵六`；
- **45 个开发账号**（全合成身份、仅限本机开发库；清单见[开发测试账号清单](docs/开发/开发测试账号清单.md)）：学号 `888800001`~`888800045`、邮箱 `dev-stu-01@dev.campuslink.local`~、登录 = **邮箱 + 验证码 `123456`**（**本系统没有密码字段**；接口自 CR-093 起"邮箱或手机号"二合一，开发账号只有邮箱）；批量核验用 `VERIFY_IP_HOURLY_LIMIT=<n>` 临时抬高 IP 限流（默认 10 次/小时/IP，对外行为不变）；
- **管理员**（dev-only 种子）：`admin@campuslink.local` + 验证码 `123456`，角色 `SUPERADMIN`，无学号、不走学籍核验；
- **入参格式**：学号 `^\d{9}$`；姓名限中文名（2~16 汉字可含 `·`）或外文名（字母起头可含空格 / `-` / `'` / `.`）；`nickname` 2~32 自由文本。

**机审**（F-SAFE-001）：`campuslink.moderation.mode` 缺省 `log`（只记录放行，开发用）；`local`＝本地词表，命中即拦 `5001 / 422`，**词表为空则启动失败**（`MODERATION_WORDS` 逗号分隔），**不允许"配了 local 却没有词"变成静默放行**；真实服务商适配待 A3-8。

**热榜**：`posts.hot_score` 由 `@Scheduled` 定时刷新（公式与权重唯一事实源在 domain 的 `HotScorePolicy`），读取走 `GET /api/v1/posts?sort=hot`（缺省 `latest`，非法值 `400 / 1001`）。**调参只改 `application.yml` 的 `campuslink.hot.*`，不改代码**；⚠️ `lock-ttl-seconds`（默认 300）须 ≤ 刷新周期——锁靠 TTL 兜底不显式释放，TTL 比周期长会把实际周期变成 TTL；验证时用 `HOT_REFRESH_INTERVAL_MS` / `HOT_INITIAL_DELAY_MS` 临时缩短周期（这两项只被 `@Scheduled` 占位符消费，刻意不绑定 `AppProperties`）。

## 架构与编码约定

### 后端分层（ADR-012，强制）

- 限界上下文 `module/{account,forum,...}`，上下文内四层：`domain`（聚合根 / 值对象 / 领域服务 / **端口 gateway** / 领域事件）→ `application`（用例编排 + Command）→ `infrastructure`（MyBatis-Plus 仓储、Redis、定时任务触发器等适配器）→ `web`（Controller + VO）；
- 依赖方向：**web / infrastructure → application → domain**；端口定义在 domain、实现在 infrastructure（DIP）；**跨上下文只允许调用对方 application 服务**；
- **`@Scheduled` 定时任务触发器放 infrastructure**、调用同上下文 application 服务（该方向合法）；触发器里**不写业务规则**（只管"何时跑 + 取锁 + 记日志"），口径在 domain 领域服务；`@EnableScheduling` 与调度线程池在 `config/SchedulingConfig`（**不在** `CampusLinkApplication`）；
- MyBatis-Plus Mapper 统一放 `*.mapper` 包；`@MapperScan` 的值**恰为单值** `{"com.campuslink.**.mapper"}`（守护测试断言，加特例即失败）；
- 以上全部由 ArchUnit `ArchitectureGuardTest`（**12 用例**）机器强制：分层依赖方向 / domain 不依赖框架（lombok 与 `domain → common` 放行）/ **web 不得注入 `domain.gateway` 端口、不得调 `domain.service`**（允许对 `domain.model` 与不可变载体做只读类型引用）/ 跨上下文只调对方 application / 端口实现必须在同上下文 infrastructure / Mapper 归位 / N-4 声明一致性 / **G8**（`SecurityConfig` 必须依赖 `EndpointAuthorizationManager` 且不得 `permitAll()`）/ **G9**（`/api/v1/admin/**` 映射方法必须调 `CurrentUser.requireRole`）。**改依赖结构前先读[技术方案 §2.4](docs/设计/技术方案.md)**（规则与该节双向绑定）；新增上下文**照抄 `module/{account,forum}` 结构**。

### 错误响应与契约声明

- 错误外壳 `common/result/ApiError{code,message,traceId}`（**无 `data`**）；**所有 ≥400 的响应只能由 `GlobalExceptionHandler` 产出**，Controller 只 `throw new ApiException(ResultCode.X)`，不自己拼错误体；
- **HTTP 状态码不另行约定**——每个 `ResultCode` 自带 `httpStatus`，改状态码只改枚举一处；
- 契约错误响应由 `OpenApiErrorResponseCustomizer` 从 `ResultCode` **单点派生**：端点用项目自有注解 `@ErrorCodes({ResultCode.X, ...})` 声明业务错误码（通用 400/500 自动补，不必声明；**显式声明通用码会与自动补撞车出重复 description**）；**禁止用 swagger `@ApiResponses` 手写错误响应**（事实存两份必漂移）；
- **鉴权声明（同时是运行时拦截依据）**：`module/*/web` 下**每个 HTTP 映射方法必须显式二选一**——公开标 `@PublicEndpoint`；受保护标 `@SecurityRequirement(name = ApiDocs.BEARER_AUTH)` **且方法体真的调用 `common/web/CurrentUser`**（`requireId` / `requireRole`）——漏写即守护测试失败。`security/EndpointAuthorizationManager` 在过滤器链按同一组注解裁决（module 端点未声明即 fail-closed）；**框架只区分"登录 / 未登录"，角色与资源级授权归业务代码**；**不要加全局 security**（会把公开端点错标为需鉴权）；
- **日志分档**：业务错误不记日志、框架级客户端错误记 1 行 WARN 不打全栈、只有兜底 `Exception` 记 ERROR + 全栈（客户端错误进 ERROR 会污染 5xx 监控）。

### 数据与迁移

- 统一响应 `ApiResponse{code,message,data,traceId}`；错误码分段（1xxx 通用 / 2xxx 账号 / 21xx 学籍 / 3xxx 帖子 / 4xxx 权限 / 5xxx 安全机审），见技术方案 §5；分页口径 **`page` / `size`**（短名，从 1 起）；
- **结构变更一律新增 Flyway 迁移** `src/main/resources/db/migration/V<n>__<描述>.sql`（**已执行的迁移不可修改**，回滚靠新增前向迁移）；**纯数据增删走** `backend/scripts/data/D<序号>__<描述>.py`（PyMySQL，默认 dry-run）；**禁止 Hibernate 自动建表**。

### 安全

- **敏感信息**（邮箱 / 手机号 / 学号）：明文一律 AES-GCM 加密存 `*_enc`，等值查询用 HMAC 哈希 `*_hash`；任何接口不得返回 `*_enc` / `*_hash`；密钥只从环境变量读取（`APP_HASH_KEY` / `APP_CRYPT_KEY`），**源码、示例、测试不得写入可用凭据字面量**；
- **学籍核验**三种失败（学号不存在 / 姓名不匹配 / 已注册）统一提示，防名册枚举；`campuslink.roster.bypass` 仅限开发联调，**生产必须 false**（`ProductionSafetyGuard` 强制）；
- **Markdown 渲染唯一出口** `common/markdown/MarkdownRenderer`（flexmark + jsoup 白名单）；flexmark 扩展须**同时注册到 Parser 与 HtmlRenderer**，否则解析成功渲染为空；任何渲染 / 白名单改动**先补用例再改实现**，保持 `MarkdownRendererTest` 全绿；
- **Redis 键命名**：所有键必须带 `campuslink:` 前缀（走 `common/redis/RedisKeys.of(...)`）；**前缀只在 infrastructure 适配器补**，应用层只传逻辑键。

### 前端

- 页面按 PRD 5.1 清单实现；**Element Plus 按需自动引入**（unplugin-auto-import / unplugin-vue-components，勿回退全量引入）；
- **`src/styles/tokens.css` 是设计令牌唯一来源**：主色 `#409eff`、**正文链接色 `#1a6fc4` 独立于主色**（不派生、不随动）、字号阶梯、间距为 4 的倍数、圆角三档、0.5px 细边框、去默认阴影 / 渐变；以 `:root:root` 覆写 EP 的 `--el-*`；**改主色只改 `--cl-color-primary`，但 `--el-color-primary-rgb` 须手工同步**；`src/styles/tailwind.css` 提供工具类（`@theme inline` 映射令牌），两者在 `main.ts` 一次性引入；
- **层叠边界（强制约定，无 lint 强制）**：Tailwind v4 输出在 `@layer` 内、EP 样式不带 layer，**无层恒胜有层——EP 组件一律通过 CSS 变量调样式，自有元素才用工具类**；改页面前先查[界面规范](docs/设计/UI规范/界面规范.md)；
- **图标统一走 `components/SvgIcon.vue`**（Tabler 线性图标，构建期内联静态 SVG；自带 `.cl-icon` 容器防 EP `fill: currentColor` 覆写描边）；**不复用 `el-icon`**；
- **代码高亮**：`src/utils/highlight.ts`（highlight.js/lib/core 按需注册语言）**只对服务端已渲染的 `pre code` 的 `textContent` 着色**，不二次渲染 Markdown、不拼接 HTML、不做前端净化兜底；`src/styles/hljs.css` 只提供 `--cl-code-*` 色板（**不整份引入主题 CSS**——那含字面 hex，会破"颜色只来自令牌"）；
- API 统一走 `src/api/client.ts`（`ApiError` + JWT 注入 + 后端错误消息直接透出 UI）；可复用逻辑放 `src/composables/`，共享常量放 `src/constants/`。

## 测试约定

- **`mvn verify` 必须全绿**（基线数见「常用命令」`mvn verify` 注释，**改基线数只改那一处**）；含 **jacoco 覆盖率门禁**（CR-111：全局行覆盖 ≥0.80；⚠️ rule 级 `includes` 对嵌套包会**空匹配并静默通过**，别再加「module 包」这类规则，护栏说明在 pom 注释里）；含**契约快照机器比对两层**（CR-106）：`contract/OpenApiSnapshotOperationsTest` 不连库、默认就跑，拦"改 Controller 忘重抓快照"；`contract/OpenApiSnapshotEqualityTest` 需真上下文（`SPRING_IT=true`，CI 已带 MySQL/Redis 服务），拦出参字段级漂移——**两类探针实测各自变红**，所以"快照等于代码"不再只靠约定；含 `ArchitectureGuardTest` 与 `EndpointAuthorizationManagerTest` 鉴权矩阵；`MarkdownRendererTest` 的 6 个 XSS 回归用例是论坛安全生命线；
- **新增需登录的端点必须补一个「匿名 → 401」单测**（真机 401 也要验）；**新增管理端点必须自带一个「角色不足 → 403」用例**——G9 只守"有没有判角色"、不守"角色取值得当不当"，两条互补缺一不可；
- **生产配置红线由 `ProductionSafetyGuardTest` 防漂移守护**：guard 内 `DEV_VALUES` 清单必须与 `application.yml` 占位符默认值**逐字相等**；`application-prod.yml` 凭据声明必须**不含 `:default`**、三个开发开关写死合规值——**改 yml 默认值而不同步 guard，测试即红**；
- **结构变更后真实启动一次**，确认 Flyway 迁移成功（`mvn verify` 不校验迁移可执行性，也不校验 `@MapperScan` 通配的实际扫描结果）；
- **接口契约变更必须重生成 OpenAPI 快照** `docs/设计/接口契约/openapi.json`——凡改 Controller 路径 / 方法 / 入参出参 / 校验注解、新增端点（公开 / 受保护声明）、`ApiResponse` / `ApiError` 外壳、`OpenApiConfig`、`CurrentUser` / `PublicEndpoint`、`ResultCode` 的错误码 / 提示语 / `getHttpStatus()` 映射、或 `EndpointAuthorizationManager` / `SecurityConfig` 授权口径：起后端按[契约 README §2](docs/设计/接口契约/README.md) 命令重抓并格式化，看 `git diff`——**非空即契约已变**，同步技术方案 §5 与该 README。**快照自此有机器校验（CR-106 两层比对）**，但要说清它守的是什么：**「归档快照 == 当前代码」有测试拦，「快照本身对不对」仍需人判断**（重抓命令见契约 README §2）。两条表达极限：`1002` / `1003` / `1004` 三类响应进不了快照（以契约 README §6.3 文字约定为准）；**可重复的 `@Pattern` 不会输出到契约**，纯代码侧校验规则须靠单测证明。
- **自由文本字段进任何 HTML 形态前，必须有服务端净化断言**（富文本 / 导出 / 分享卡片 / 通知摘要均算）——昵称 / 签名 / `bio` 一类自由文本的**唯一**防线是服务端白名单，**不得依赖"渲染路径恰好没走 HTML"**（问题清单 **E-013** 候选③）。
- **桌面前端功能必须真实浏览器验收**：从用户点击入口走完整链路（入口可见 → 表单预填 → 提交 → 回跳 → 详情 / 搜索反映结果），**不能以「后端 200」「`v-if` 已写」「编译通过」代替**；只有不适合浏览器验证的后端逻辑才归单测。验收发现的交互 / 渲染缺陷当轮修复并登记。
- **全链路取证要写进文档**：每次功能验收给出真实数据证据（必要时用 SQL 造历史数据并在测后清理），例如标签替换必须同时证明「新标签搜索命中、旧标签不再命中」，**不能只验「写入成功」**。

## 汇报约定（每次 AI 会话执行）★

- **开工先报计划**：实质工作开始前，先用 ≤3 行说明"这轮做什么、改哪些文件"；**若属「实施方案约定」门槛范围，先写方案、停下等确认，不要先动代码**；
- **小步汇报**：长任务拆成小步，每完成一步一两句话报告进展；**不要一口气跑完多个批次才汇报**；
- **收尾摘要 ≤10 行**：改了什么 / 为什么 / 验证结果，并指向驾驶舱与相关文档；台账登记只报"已登记 CR-xxx"，不复述台账内容；
- **一次会话只做一个逻辑任务**，除非用户明确要求连做；
- **并行会话敏感性**：改公共文档（台账 / 本文件 / 驾驶舱 / docs/README）前先看当前内容是否已被其他会话更新，**用追加与加注，不做整段覆写**（本文件整篇重写须用户明确指令）。

## 实施方案约定（较大改动 / Bug 修复先写方案）★

**规则一句话**：够大就先写「可实施方案」→ **停下等用户确认** → 才动代码。

### 门槛（中等口径，满足任一即须先写方案）

- **跨模块**：改动的 `module/*` 超过一个，或 `common/` 与业务模块同时改；
- **接口契约**：路径 / 方法 / 入参出参 / 校验注解 / `ApiResponse`·`ApiError` 外壳 / 错误码；
- **数据库结构**：新增 `V*.sql` 迁移、改表 / 索引 / 约束；
- **新依赖**：`pom.xml` / `package.json`（含大版本升级）；
- **安全与鉴权**：`security/`、`SecurityConfig`、`CurrentUser`、`PublicEndpoint`、审计链路；
- **Bug 修复**：根因跨层或跨模块，**或修复会改变既有对外行为**（状态码 / 错误码 / 响应形状）。

### 可直接改（不写方案，仍须轻量登记 CR）

单文件局部修改、纯样式 / 文案、文档修正、测试补充、根因单一且对外行为不变的缺陷修复。

### 形态与边界

- 目录 **`docs/开发/实施方案/`**，命名 **`CR-xxx-主题.md`**；格式照[说明与模板](docs/开发/实施方案/说明与模板.md)，**一页以内**，六节：目标 / 范围（做与不做）/ 改动点（文件级）/ 步骤 / 验证 / 回滚；
- **CR 编号登记在[台账](docs/变更日志/变更台账.md) §1**（一行式）；方案目录与模板在 `docs/README.md` §3「模板」表登记，**各 CR 实例按命名规则存放、不逐项登记**；
- **实施完成后不回改原方案**：实际与计划的偏差追加到该方案文末「实施结果」节，保留"原计划 vs 实际"对照；
- **同一 CR 中途改口径，先改方案再改代码**；
- **边界**：用户当面明确说「直接改」时从命；纯文档 / 流程类改动（如本文件、模板、README）**不写实施方案**，走 change-log 登记即可。

## 工作收尾约定（每次任务完成后执行）★

1. **更新进度驾驶舱**——触发条件：本次工作改变了进度事实（新 CR / 欠账状态变化（A3-*、W-*、B-*、N-*）/ Sprint 里程碑 / 新问题 / 新业务需求）。更新：页头「数据快照」与 change-log 版本 chip、总览统计、时间线新增 CR 行（**从台账 §1 抄录**）、问题台账、待办。**驾驶舱只做聚合，不得登记事实源文档中没有的新事实；只改数据，不改结构与样式**；更新前先核对台账最新条目，防止静默过时；
2. **基线级变更确认已登记**：范围 / 排期 / 技术选型 / 架构 / 工程结构的改动，实施前在[台账](docs/变更日志/变更台账.md) §1 **一行一条**登记（改了什么 / 为什么 / 凭证）；CR 编号写入 commit message 即完成凭证对齐，**不做台账回填仪式**；
3. **展示变更摘要**：列出本次改动的文件与要点，**不自动 git commit / push**，等用户确认。

## 文档与流程约定

- 阶段门未通过不得进入下一阶段；**版本号只在 `docs/README.md` §2 登记**，其他文档交叉引用只写链接、不写版本号；
- **状态两处收口**：项目当前状态只维护**台账 §1 + 进度驾驶舱**两处；next-steps / gate-* / waivers 等文档中的既有状态保留为历史，不再新增同步义务；
- **流程偏离登记在 `docs/流程偏离记录.md`**（不得只在对话里说明）；评审门纪要归档 `docs/评审/<阶段名>门纪要.md`；
- **文档命名用中文**（唯一例外 `README.md`——GitHub 目录入口约定）；目标结构 `docs/{立项,需求,设计,开发,评审,模板,变更日志}/`；
- **简洁原则**：流程文档只保留"当前结论 + 指针"，**日志式过程记录（实施补记、状态回填、凭证追踪、版本沿革）一律写入 `docs/变更日志/`**，不在流程文档里累积；
- 新文档放入 `docs/` 对应位置并在 `docs/README.md` §2 + §3 **两处登记**；文档头部必须含：**版本、状态、维护人、最后更新**。

## 红线（与用户全局规则一致）

- **不自动 git commit / push**；提交前先展示变更摘要；**commit message 用中文**（保留 `feat` / `docs` / `fix` 等类型前缀，作用域与描述用中文；标题末尾写 CR 号；正文写清**「做了什么、解决了什么」**）；monorepo 统一根目录操作；
- 删除文件 / 目录、修改 `.env` / 密钥 / 证书、`git push` / `rebase` / `reset --hard`、公开发布（npm publish、生产部署等）：**必须先征得用户同意**；
- **生产环境红线**（前三项由 `ProductionSafetyGuard` 在 `prod` profile 启动即机器强制）：
  1. `campuslink.roster.bypass=false`；
  2. `campuslink.captcha.fixed-code` 必须留空（固定验证码等同取消验证码防线）；
  3. JWT 与加密密钥全部覆盖默认值（各 ≥32 字节，HASH / CRYPT 两把不得相同）；
  4. 名册导入与内容处置必须写审计日志（**尚无切面强制**——新增写路径须人工补 `auditService.record(...)`，幂等重放不记审计）；
  5. 机审降级开关（fail-closed）**不得改为跳过审核**。
