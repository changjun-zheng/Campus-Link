# CR-116 · JWT 滑动续期（把文档承诺变成实现）

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.0 |
| 状态 | 待确认 |
| 维护人 | AI 会话（2026-10-21） |
| 最后更新 | 2026-10-21 |
| 关联 CR | [CR-116](../../变更日志/变更台账.md#cr-116) |

## 1. 目标

让「JWT 7 天滑动续期」从文档承诺（[技术方案](../../设计/技术方案.md) §5 / [PRD](../../需求/产品需求文档.md) §4）变成实现：**活跃用户到期前自动续签、连续 7 天不活跃才需重新登录**。现状是 `JwtService` 只写 `expiration = now + ttlDays`（[JwtService.java](../../../backend/src/main/java/com/campuslink/security/JwtService.java)），全仓无任何续期代码（问题清单 **E-012 ②** 已实测记录），用户第 7 天必然掉线。

## 2. 范围

- **做**：
  - 后端新增**受保护**端点 `POST /api/v1/auth/refresh`：凭当前有效 JWT 换取新 token（新 `exp = now + 7 天`），响应体**复用既有** `AuthResponse{token, expiresAtEpochSeconds, user}`；
  - 续签前**重读账号**：`BANNED → 2006/403`；`DEACTIVATED → 2013/403`（不续签，需重新登录以触发「期内登录即撤销注销」）；账号不存在 `→ 2007`；
  - 用例落在 `AccountApplicationService.refresh(long userId)`（复用 `toResult`，不新增领域逻辑）；
  - 前端 `api/auth.ts` + `stores/auth.ts` 新增**单飞**（并发只发一次）的 `refreshSession()`，成功后更新 `cl_token` / `cl_user` / `cl_exp`；
  - 路由守卫 `beforeEach`：已登录且剩余有效期 **< 2 天**时先续签再放行；**仅**在收到 `4001 / 2006 / 2013` 时清会话并跳登录，网络故障只静默跳过（token 未过期，下次导航重试）；
  - 契约快照再生成（**+1 operation、零新 schema**）；技术方案 §5 补实现口径。
- **不做**（明确排除，防边界漂移）：
  - 不做 refresh token / 令牌轮换 / 绝对上限（仍是单 token + 7 天窗口）；
  - 不做「每个请求都续签 + 响应头回传」（避免每请求重签与契约面扩张）；
  - 不做 Redis 黑名单 / 主动踢下线 / 多设备管理（401 口径不变）；
  - 不改登录 / 注册响应形状、不改 `campuslink.jwt.ttl-days` 默认值、不改 `EndpointAuthorizationManager` 授权语义；
  - 不处理「页面停留超过 7 天不导航」的极端场景（下次导航或刷新即续签）。

## 3. 改动点（文件级）

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/.../module/account/application/AccountApplicationService.java` | 改 | 新增 `refresh(long userId)`：查账号 → 状态门 → `toResult` |
| `backend/.../module/account/web/AccountController.java` | 改 | 新增 `POST /refresh`：`@SecurityRequirement` + `CurrentUser.requireId` + `@ErrorCodes({NOT_LOGGED_IN, USER_BANNED, ACCOUNT_DEACTIVATED, USER_NOT_FOUND})` |
| `backend/.../module/account/web/AccountControllerTest.java` | 改 | 匿名 401、续签成功、封禁 2006、注销中 2013 四例 |
| `frontend/src/api/auth.ts` | 改 | 新增 `refreshSession()` |
| `frontend/src/stores/auth.ts` | 改 | 新增 `refresh()`（单飞 + 更新会话） |
| `frontend/src/router/index.ts` | 改 | 守卫按「剩余 < 2 天」续签；失败分级处置 |
| `docs/设计/接口契约/openapi.json` | 重生成 | 新增 1 个 operation，schema 复用 |
| `docs/设计/技术方案.md` | 改 | §5「7 天滑动续期」补实现口径（与代码一致） |
| `AGENTS.md` | 改 | `mvn verify` 基线数（只改「常用命令」该一处） |

## 4. 步骤

- [ ] 1. 后端用例 `refresh` + Controller 端点（保护声明与 `CurrentUser` 齐备）
- [ ] 2. 后端四例单测：匿名 → 401 / 续签成功（新 exp 晚于旧） / BANNED → 2006 / DEACTIVATED → 2013
- [ ] 3. 前端 api + store + 守卫接线；`npm run lint`
- [ ] 4. 起后端按[契约 README](../../设计/接口契约/README.md) §2 重抓 `openapi.json`，核对 diff 只多 refresh 一个 operation
- [ ] 5. 文档同步：技术方案 §5 / AGENTS 基线 / 台账 / 问题清单 E-012 ② / README §2 / 驾驶舱

## 5. 验证

- **单测**：`mvn verify` 全绿（新增 4 例；基线数以实施前实测为准，改基线只改 `AGENTS.md` 一处）；
- **真机**：登录取 token → `POST /auth/refresh` 返回新 token 且 `expiresAtEpochSeconds` 比旧值晚 7 天 → 新 token 调 `GET /users/me` 200；匿名 refresh → `401 / 4001`；封禁账号 refresh → `403 / 2006`；
- **前端**：登录后把 `cl_exp` 改到 1.5 天后刷新页面 → 守卫触发 `/auth/refresh`、`cl_exp` 随之后移；把 refresh 打成 4001 → 清会话并跳 `/login?redirect=…`；
- **契约**：`git diff openapi.json` **仅新增 refresh operation**，无 schema 变化；技术方案 §5 同步；
- **回归面**：登录 / 注册 / 注销冷静期 / 后台角色 / 旧前端（不调 refresh，仍 7 天绝对过期）行为不变。

## 6. 回滚

revert 单个 commit 即可：**零迁移、零数据回补**；已签发的 token 继续有效（JWT 自包含），前端退化为现状（到期重登）；守卫失败路径本身等价于现状的过期清理，无额外副作用。

---

## 7. 实施结果（实施后追加，不回改上文）

<待实施>
