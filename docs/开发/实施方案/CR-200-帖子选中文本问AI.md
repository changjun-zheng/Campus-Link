# CR-200 · 帖子选中文本问 AI（私密侧栏）

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.0 |
| 状态 | 待确认 |
| 维护人 | AI 会话（2026-11-05） |
| 最后更新 | 2026-11-05 |
| 关联 CR | CR-200（台账下一自由编号；批准后登记） |

## 1. 目标

帖子详情页选中正文后浮出「问 AI」，在**仅提问者可见**的侧栏中调用 DeepSeek（OpenAI 兼容接口）流式回答；支持当前会话内多轮追问，回答以 Markdown 展示，不写入楼层、不进搜索 / 热榜、不落 MySQL。

已确认口径：

- 服务商：DeepSeek，OpenAI 兼容 `/chat/completions`，模型默认 `deepseek-chat`；密钥只从 `DEEPSEEK_API_KEY` 读取。
- 配额：每人每天 20 次（按 `Asia/Shanghai` 自然日），另设每分钟 5 次防抖、单用户同时 1 条生成。
- 会话：Redis 30 分钟 TTL，不落库；过期即结束，刷新页面不恢复历史。
- 回答：流式期间显示原始文本，完成帧由服务端 `MarkdownRenderer` 渲染为净化 HTML 后替换；前端不对模型原文做 Markdown 解析。
- 隐私：首次使用弹窗一次（按登录用户记 localStorage），之后面板底部常驻说明。

## 2. 范围

- **做**：选区工具条；桌面右侧抽屉 / 移动端底部面板；首用隐私弹窗与常驻说明；登录校验；20 次/日、5 次/分、单用户并发 1；选中 ≤2000 字、问题 ≤500 字、最多 5 个用户轮次；DeepSeek 流式调用；停止、复制、失败重试、会话过期提示；输入机审、输出完成后复核；配置开关、生产启动校验、7xxx 错误码、SSE 契约。
- **不做**：公开 AI 楼层、回复框 `@AI`、历史会话列表、MySQL 会话表 / 迁移、文件 / 图片 / 语音输入、匿名使用、模型切换 UI、跨帖子 / 全站 RAG、代码执行或工具调用、后台用量看板、重新生成（先只做失败重试）。

## 3. 改动点（文件级）

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/.../module/assistant/domain/model/AssistantConversation.java`、`AssistantMessage.java` | 新增 | 会话聚合与消息载体；含轮次、截断、归属校验 |
| `backend/.../module/assistant/domain/gateway/ChatModelGateway.java`、`ConversationStore.java`、`AssistantUsageGateway.java` | 新增 | 模型 / 会话 / 配额三个端口 |
| `backend/.../module/assistant/domain/service/AssistantPromptBuilder.java` | 新增 | 系统提示、选中文本分隔、历史消息组装；纯 Java |
| `backend/.../module/assistant/application/AssistantApplicationService.java`、`AssistantStreamHandle.java`、`AssistantStreamObserver.java` | 新增 | 预检、配额、机审、跨上下文取帖子上下文、流式编排、取消 |
| `backend/.../module/assistant/infrastructure/model/DeepSeekChatModelAdapter.java` | 新增 | `HttpClient` 读取 OpenAI 兼容 SSE；不新增依赖；可取消、不记录正文 |
| `backend/.../module/assistant/infrastructure/redis/RedisConversationStore.java`、`RedisAssistantUsageGateway.java` | 新增 | Redis 30 分钟会话；Lua 原子计数（日配额 / 分钟限流 / 并发锁） |
| `backend/.../module/assistant/web/AssistantController.java`、`web/vo/*` | 新增 | 配置、开新会话、追问；SSE；`@PublicEndpoint` / `@SecurityRequirement` |
| `backend/.../config/AppProperties.java` | 改 | 增 `campuslink.assistant.*` 绑定 |
| `backend/.../config/ProductionSafetyGuard.java` | 改 | `assistant.enabled=true` 时密钥必填、base-url 必须 https；同步测试 |
| `backend/.../config/AssistantStreamingConfig.java` | 新增 | 专用有界线程池，模型调用不占 Tomcat 线程 |
| `backend/.../common/result/ResultCode.java` | 改 | 新增 7xxx 助手错误码；6xxx 已属组队，不复用 |
| `backend/.../common/markdown/MarkdownRenderer.java` | 改 | 增 `renderForAssistant`：去 `img`、仅留 http(s) 链接并加 `rel`；先补测试 |
| `backend/src/main/resources/application.yml` | 改 | `campuslink.assistant` 默认关闭，密钥走环境变量 |
| `docs/设计/技术方案.md` | 改 | §2 模块与跨上下文调用、§5 接口与 7xxx 错误码 |
| `docs/设计/接口契约/openapi.json`、`README.md` | 重生成 / 改 | 新端点契约；SSE 运行时错误事件在 §6.3 文字约定 |
| `docs/设计/UI规范/界面规范.md` | 改 | 侧栏尺寸、选区工具条、流式 / Markdown 态、隐私弹窗 |
| `docs/需求/隐私政策与用户协议.md` | 改 | 补第三方模型处理说明，与弹窗文案一致 |
| `frontend/src/api/assistant.ts` | 新增 | 配置读取 + `fetch` SSE 解析；与 401 全局退出共用 |
| `frontend/src/composables/useAssistant.ts` | 新增 | 面板状态、隐私确认、开新会话 / 追问 / 停止 / 重试 |
| `frontend/src/components/AssistantPanel.vue`、`SelectionToolbar.vue` | 新增 | 侧栏与浮出按钮；Markdown 完成帧用 `v-html` + `v-highlight` |
| `frontend/src/views/PostDetailView.vue` | 改 | 正文选区检测、工具栏定位、面板挂载、路由切换重置 |
| `frontend/src/assets/icons/sparkles.svg`、`index.ts` | 新增 / 改 | AI 入口图标；同步图标计数注释 |
| `frontend/src/api/client.ts` | 改 | 导出 401 触发入口，SSE 与既有 JSON 请求共用退出逻辑 |
| `backend/src/test/...`、`frontend` 现有校验 | 新增 / 改 | 单测、契约、浏览器验收见 §5 |

接口（新增）：

- `GET /api/v1/assistant/config`：公开，返回 `enabled / dailyLimit / maxSelectedChars / maxQuestionChars / quickPrompts / privacyNoticeVersion`。
- `POST /api/v1/assistant/conversations`：需登录，入参 `{postId, selectedText, question}`，返回 SSE。
- `POST /api/v1/assistant/conversations/{id}/messages`：需登录，入参 `{question}`，返回 SSE；会话必须属于当前用户。
- SSE 事件：`meta`（conversationId、剩余次数）、`delta`（增量文本）、`done`（净化 HTML、finishReason、blocked）、`error`（code / message）。流开始前的错误仍走 `GlobalExceptionHandler` 的 `ApiError` 外壳。

## 4. 步骤

- [ ] 1. 先写 `MarkdownRenderer` 助手净化用例，再实现 `renderForAssistant`；保持原 6 个 XSS 用例全绿。
- [ ] 2. 加 7xxx 错误码、`AppProperties.Assistant`、`application.yml`、生产守卫与对应单测。
- [ ] 3. 实现 domain 模型 / 端口 / prompt builder，以及 Redis 会话、配额、并发锁适配器。
- [ ] 4. 实现 DeepSeek 适配器（SSE 解析、超时、取消、错误脱敏），用本地 mock HTTP 服务做单测。
- [ ] 5. 实现 application 编排与 SSE controller：预检同步抛错，流启动后错误走 `error` 事件；补匿名 401、配额 429、会话过期、输出复核用例。
- [ ] 6. 前端：API / composable / 面板 / 选区工具条；完成帧渲染、代码高亮、复制、停止、重试、移动端。
- [ ] 7. 重生成 OpenAPI 快照，更新技术方案 §5、契约 README、UI 规范、隐私政策；跑文档一致性脚本。
- [ ] 8. 真机验收：匿名 401、登录后完整链路、20 次配额、停止 / 重试、Markdown / XSS、移动端；真实 DeepSeek 需本机注入 key，无 key 时用 mock 服务验 UI。

## 5. 验证

- **单测**：prompt 分隔与轮次截断；配额 / 限流 / 并发；SSE 分片解析、`[DONE]`、上游错误脱敏；会话归属与过期；输入机审；输出命中后替换；`renderForAssistant` 去 `img`、`javascript:` 链接、加 `rel`、保留代码块；`ProductionSafetyGuardTest` 密钥缺失即红。
- **集成**：`SPRING_IT=true` 跑 Redis 会话 / 计数；`MockMvc` 验匿名 401、配额 429、禁用 503、参数 400、SSE `text/event-stream`；`ArchitectureGuardTest` 新模块无违规；覆盖率门禁。
- **真机**：登录开发账号 → 打开帖子 → 划选正文 → 隐私弹窗（仅首次）→ 提问 → 流式 → Markdown 渲染 / 代码高亮 → 追问 → 停止 / 重试 → 第 21 次 429；匿名点击跳登录；刷新后会话不恢复；不产生楼层、搜索 / 热榜不变。
- **契约**：重抓 `openapi.json`，`git diff` 仅应出现新端点 / schema / 7xxx 错误码；`python backend/scripts/check_docs_consistency.py` exit 0。
- **回归**：帖子详情、回复、通知 SSE、既有 Markdown 渲染不受影响；`mvn verify` 全绿；`npm run typecheck` / `lint` / `build` exit 0。

## 6. 回滚

- 运行期：`ASSISTANT_ENABLED=false` 即隐藏前端入口并让端点返回 7001；无数据库迁移，Redis 会话 / 配额键分别 30 分钟、1 天后自然过期。
- 代码：整体 revert 本 CR 提交即可；不涉及既有表结构与帖子数据，无需回补。
- 密钥：只经环境变量注入，泄露时轮换 `DEEPSEEK_API_KEY`，服务端不存正文、不落库。

## 7. 实施结果（实施后追加，不回改上文）

- **交付**：8 步全部落地。后端新增 `module/assistant`（domain/application/infrastructure/web）、7xxx 错误码、`renderForAssistant`、启动守卫；前端新增 `api/assistant.ts`、`useAssistant`、`SelectionToolbar`、`AssistantPanel`、`sparkles` 图标。
- **验证**：`mvn verify` **914 例 / 0 失败 / 17 SKIPPED**（默认口径；jacoco、ArchUnit、Checkstyle、SpotBugs、module 覆盖率全过）；`npm run typecheck` / `lint` / `build` exit 0；契约快照 v1.49（79 操作 / 69 路径 / 131 schema / 22 tag / 61 受保护，diff +358 / −0）；文档一致性脚本 exit 0。
- **真机（mock）**：本机 mock DeepSeek SSE（127.0.0.1:9999）下，Playwright 走通隐私弹窗 → 流式回答 → Markdown / 代码高亮 / 代码复制 / 整段复制 → 多轮追问 → 停止 → 匿名跳登录；console errors 为空。截图在 `C:\Users\24839\AppData\Local\Temp\campuslink-assistant-ui\`。
- **真机（真实模型，2026-11-05 补测）**：改接本机 OpenAI 兼容网关 `http://127.0.0.1:3425/v1`（key `magpie`、模型 `workbuddy/deepseek-v4.1-flash`），`ASSISTANT_ENABLED=true` 启动成功。SSE curl 走通新会话（53 个 delta）+ 同会话追问（183 个 delta），`done` 含 `raw` / 净化 `html` / token；Playwright 单问验证真实中文回答、2 个 SQL 代码块均着色且复制按钮注入，控制台零错误，配额从 4 → 1 正常递减。截图：`C:\Users\24839\AppData\Local\Temp\campuslink-assistant-real\real-answer.png`。
- **未测**：上游限流 / 超时 / 5xx 的真实表现（mock 已覆盖错误分支）；移动端 82svh 底部面板只做了窄屏截图，未做真机触控。
- **遗留**：无数据库迁移；`ASSISTANT_ENABLED=false` 可即时回滚入口，Redis 键自然过期。生产开启须注入 `DEEPSEEK_API_KEY` 并由发起人决定内容外发口径。

---

**实施前需你确认**：① 本方案是否批准；② 本机是否已有 `DEEPSEEK_API_KEY`（不要贴到对话或仓库，配到环境变量即可），用于真实模型验收；没有则先以 mock SSE 服务验前端与链路，真实调用留到 key 就绪后补测。
