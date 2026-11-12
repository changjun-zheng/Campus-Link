# CR-218 · AMQP 客户端安全升级（amqp-client 5.30.0 → 5.37.1）

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.0 |
| 状态 | 已确认（用户授权自主推进） |
| 维护人 | AI 会话（CR-218） |
| 最后更新 | 2026-11-12 |
| 关联 CR | [CR-218](../../变更日志/变更台账.md#cr-218) |

## 1. 目标

修复依赖扫描脚本后首次跑通 OSV（CR-148 工具），发现 Spring Boot 4.1.1 托管的 `com.rabbitmq:amqp-client@5.30.0` 有 **10 条已知公告（4 HIGH / 5 MODERATE / 1 LOW）**，其中包括未校验长度导致的 OOM、JSON-RPC `Class.forName` 任意类加载、`useSslProtocol()` 默认信任所有证书（MITM）。RabbitMQ 通知传输默认关闭（`campuslink.notifications.rabbitmq.enabled=false`），但一旦生产开启即暴露。升级到该库最新版 **5.37.1**（公告修复版最高 5.36.1，取当前最新稳定版）。

## 2. 范围

- **做**：
  - `backend/pom.xml` `<properties>` 新增 `rabbit-amqp-client.version=5.37.1`——与 CR-148 对 tomcat / Jackson 的处理同法：Boot 用属性托管该依赖，覆盖属性即官方支持的升级方式，**不新增依赖、不改代码**。
  - 跑 `mvn verify` 全量回归 + `SPRING_IT=true` 真库集成，确认通知 / 出站链路测试不受影响。
  - 复跑 OSV 扫描，确认 Maven 侧公告归零；台账 §1 与驾驶舱同步。
- **不做**：
  - 不改通知链路代码与 RabbitMQ 开关默认值；不启真实 RabbitMQ 联调（本机未部署，`enabled=false` 是现行口径）。
  - 不处理 npm 侧 `minimatch@9.0.9 / 10.2.6` 的 1 条 MODERATE（dev-only、修复版 11.0.1 是跨大版本，需另开任务评估构建链）。
  - 不放宽扫描阈值（仍只有 CRITICAL 才 exit 1）。

## 3. 改动点（文件级）

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/pom.xml` | 改 | `<properties>` 加 `rabbit-amqp-client.version`，注释写明公告范围与移除时机（Boot 托管 ≥5.37.1 时删掉本行） |
| `docs/开发/实施方案/CR-218-AMQP客户端安全升级.md` | 新增 | 本方案 |
| `docs/变更日志/变更台账.md` | 改 | §1 登记 CR-218 + 汇总计数 |
| `docs/进度驾驶舱.html` | 改 | `CRS` 新增 CR-218、快照日期与汇总数 |

## 4. 步骤

- [ ] 1. 台账登记 CR-218 与本方案。
- [ ] 2. `pom.xml` 覆盖属性。
- [ ] 3. `mvn verify`（在线解析新版本）全绿；`SPRING_IT=true mvn verify` 全跑。
- [ ] 4. `python backend/scripts/scan_deps_osv.py` 复测：Maven 零公告。
- [ ] 5. 台账 / 驾驶舱同步，文档一致性脚本 exit 0。
- [ ] 6. 提交（不推送）。

## 5. 验证

- `mvn dependency:tree -Dincludes=com.rabbitmq:amqp-client` 输出 `5.37.1`。
- `mvn verify`：1004 例 / 0 失败 / 21 SKIPPED，jacoco / module 门禁 / SpotBugs / Checkstyle 全绿（基线不变）。
- `SPRING_IT=true mvn -o verify`：1004 例 / 0 失败 / 0 SKIPPED（含真库上下文与通知出站集成测试）。
- OSV 扫描前后对照：Maven 10 条 → 0 条；npm 侧仍可见 `minimatch` 1 条 MODERATE（如实保留）。

## 6. 回滚

revert 本 CR 提交即可（单行属性 + 文档）；不涉及迁移、配置项与代码。

## 7. 实施结果

- **原计划 vs 实际**：按 §2/§3 落地——`pom.xml` 单行属性覆盖 + 文档同步，零代码、零迁移、零契约变更。
- **验证**：`mvn dependency:tree -Dincludes=com.rabbitmq:amqp-client` → `5.37.1`；`mvn verify` **1004 例 / 0 失败 / 21 SKIPPED**（jacoco / module 门禁 / SpotBugs 0 / Checkstyle 全绿）；`SPRING_IT=true mvn -o verify` **1004 例 / 0 失败 / 0 SKIPPED**；OSV 复测 Maven 侧公告 **10 → 0**，npm `minimatch` 1 条 MODERATE（dev-only）如实保留。
- **偏差**：首次 `mvn -o verify` 因 5.37.1 的 jar 尚未下载而失败（`dependency:tree` 只取了 pom）；改在线跑一次完成解析后恢复离线可用，非缺陷。
- **遗留**：npm `minimatch` 修复版 11.0.1 属跨大版本、影响构建链，另开任务评估；Boot 托管版本 ≥5.37.1 时删除本覆盖行。
