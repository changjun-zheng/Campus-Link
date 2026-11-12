# CR-208 · JWT 吊销与账号状态即时失效

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.0 |
| 状态 | 已实施 |
| 维护人 | AI 会话（CR-208） |
| 最后更新 | 2026-11-08 |
| 关联 CR | [CR-208](../../变更日志/变更台账.md#cr-208) |

## 1. 目标

封禁 / 注销 / 角色变更后，旧 JWT 立即失效，不再等到最长 7 天自然过期；为后续 OPS 降级与「封禁即时登出」补上缺失的会话失效机制。

## 2. 范围

- **做**：用户级吊销水位（Redis，按 `iat` 比较）；过滤器在解析 JWT 后检查水位；注销、封禁、授予 OPS 成功后立即写水位；补充单测与 Redis 键命名用例。
- **不做**：不新增退出登录端点（PRD 无此需求）；不实现 OPS 降级接口；不改 JWT 格式 / 有效期；不改任何 HTTP 接口与契约；不引入数据库迁移；不做多设备会话管理。

## 3. 改动点（文件级）

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/src/main/java/com/campuslink/common/security/TokenRevocationGateway.java` | 新增 | 吊销端口：写水位 / 判是否已撤销 |
| `backend/src/main/java/com/campuslink/security/RedisTokenRevocationGateway.java` | 新增 | Redis 实现，键 `campuslink:auth:revoked-before:<userId>`，TTL = JWT 有效期 + 1 小时 |
| `backend/src/main/java/com/campuslink/security/JwtAuthenticationFilter.java` | 改 | 解析成功后检查 `iat` 是否早于水位；Redis 异常时 fail-open 并记 WARN |
| `backend/src/main/java/com/campuslink/module/account/application/AccountApplicationService.java` | 改 | 注销 / 封禁 / 授予 OPS 成功后调用吊销 |
| `backend/src/test/java/com/campuslink/security/JwtAuthenticationFilterTest.java` | 改 | 已撤销 → 匿名；吊销查询异常 → 保持可用 |
| `backend/src/test/java/com/campuslink/security/RedisTokenRevocationGatewayTest.java` | 新增 | 前缀 / TTL / 水位的秒级比较 |
| `backend/src/test/java/com/campuslink/module/account/application/AccountApplicationServiceTest.java` | 改 | 三个触发点与幂等 / 竞态失败不误吊销 |

## 4. 步骤

- [x] 1. 端口 + Redis 适配器 + 过滤器检查。
- [x] 2. 注销 / 封禁 / 授予 OPS 接线（仅状态或角色真的变更后吊销）。
- [x] 3. 单测：过滤器、适配器、应用服务触发点。
- [x] 4. `mvn verify` + 真实起栈验证封禁后旧 token 立即 401。
- [x] 5. 台账 / 驾驶舱 / README 同步。

## 5. 验证

- **单测**：`JwtAuthenticationFilterTest`（已撤销 / 未撤销 / Redis 故障降级）、`RedisTokenRevocationGatewayTest`（键前缀、TTL、秒级水位）、`AccountApplicationServiceTest`（注销 / 封禁 / 授予 OPS 调用吊销；解封、幂等授予、竞态失败不调用）。
- **真机**：登录开发账号取 token → 管理员封禁 → 旧 token 调受保护接口立即 `401 / 1002`；解封后重新登录恢复 200。
- **契约**：无接口变更，`openapi.json` 零 diff。
- **回归**：正常 token、超管 / OPS 角色映射、刷新续签不受影响；Redis 不可用时登录态仍可用（fail-open，记 WARN）。

## 6. 回滚

整笔 revert 本 CR 提交即可；Redis 水位键 8 天后自然过期，无数据库与数据回补。若仅需关闭检查，可将过滤器对 `revocationGateway` 的调用短路，但保留写水位无副作用。

## 7. 实施结果（实施后追加，不回改上文）

- 已按方案落地，未新增迁移与接口；`mvn verify` 921 例全绿（17 SKIPPED），SpotBugs / Checkstyle / 覆盖率门禁通过。
- 真机（8099）：封禁前旧 token `/users/me` 200；封禁后同一 token `/users/me` 与 `/notifications` 均为 `401 / 4001`；解封不会复活旧 token；重新登录后的新 token 200。注销与授予 OPS 的写水位由单测覆盖（后者会改变开发账号角色，未做真机）。
- 口径偏差：水位比较由「严格早于」改为「早于或等于」——同一秒内无法用秒级 `iat` 区分先后，宁可误伤同秒新 token 也不放过旧 token；真机第一轮正因同秒比较未触发，改后复测通过。
- 未测：多实例 Redis 故障切换（单机开发环境无法覆盖）；fail-open 仅由单测证明。
- 遗留：OPS 降级接口、退出登录端点仍不在本期范围；后续可复用同一水位机制。
