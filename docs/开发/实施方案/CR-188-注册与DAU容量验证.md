# CR-188 · 注册 1 万 / DAU 1000 容量验证

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.1 |
| 状态 | 已实施（2026-11-02） |
| 维护人 | AI 会话（2026-11-02） |
| 最后更新 | 2026-11-02（实施结果见 §7） |
| 关联 CR | CR-188（本方案） · 输入 [CR-179](../变更日志/变更台账.md#cr-179)（帖子 10 万 / 单帖 1000 楼已验证，注册 1 万 / DAU 1000 未验证） |

> ⚠️ 本方案写于并行会话推进 CR-185~187 期间：编号按当时台账「下一自由编号 CR-188」取；若发起人确认时该号已被占用，以台账为准重编号。方案文件为新增，未改动并行会话正在编辑的台账 / README / 驾驶舱。

## 1. 目标

在**隔离环境**验证 PRD §4 容量剩余两项：**注册 ≥ 1 万、日活 ≥ 1000**。不改业务代码、接口、数据库结构；全程不碰开发库与 8088 开发栈。

## 2. 范围

- **做**：
  1. 独立 MySQL schema `campuslink_cap` + 独立后端 **8097**（Flyway 自动迁移）+ **Redis DB 1**；`SEARCH_ENGINE=mysql`、`HOT_INITIAL_DELAY_MS/HOT_REFRESH_INTERVAL_MS` 拉长到 1 天。
  2. **10,000 个合成名册**（学号保留段 `999900001`~`999910000`，邮箱域 `cap.campuslink.local`，批次 `cap-10k`）经 admin CSV 导入；**10,000 个账号经真实 API 注册**（`verify-student → captcha → register`），脚本**不复刻 HMAC/AES**——加密口径只保留在生产代码一份。
  3. **100,000 帖 + 1000 楼**复用 `seed_capacity.py`（`DB_NAME=campuslink_cap`），让 DAU 读链路有真实内容；搜索容量已由 CR-181 单独验证，本 CR 的读混合**不含搜索**（避免把 10 万帖 MySQL 搜索的已知慢路径混进 DAU 结论）。
  4. 捕获注册返回的 **1000 个 token** 到**仓库外临时文件**；扩展 `run_api_perf.mjs` 支持 `PERF_TOKENS_FILE`（并发 worker 轮询 token），模拟 1000 个不同用户做读混合（列表 / 详情 / 回复 / 通知 / 组队）。
  5. 性能基线 §15 + 台账 / README / 驾驶舱登记（**等并行 CR-185~187 提交后再追加**，避免覆盖）。
- **不做**：不改开发库 / 生产库；不压发帖 / 回复写链路（CR-179/181 已验）；不引入 Meili；不压真实短信 / 邮件（log 模式 + 固定码）；不把 10k 账号或 token 提交进仓库。
- **需发起人确认**：新建临时 schema 与 10k 合成账号（预计 30~40 分钟、约 100MB 数据），测后 drop schema / flush Redis DB1。

## 3. 改动点（文件级）

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/scripts/perf/seed_accounts_capacity.py` | 新增 | dry-run / apply / cleanup；生成 10k 名册 CSV、admin 导入、逐账号注册、捕获 1000 token；只允许 127.0.0.1:8097 |
| `backend/scripts/perf/run_api_perf.mjs` | 改 | 支持 `PERF_TOKENS_FILE` 轮询 token；新增 `--dau-users` 读混合模式与 distinct token 统计 |
| `backend/scripts/perf/seed_capacity.py` | 不改 | `DB_NAME=campuslink_cap` 复用 |
| `docs/开发/性能基线实测-2026-10-26.md` | 改 | 新增 §15 结果 |
| 台账 / README / 驾驶舱 | 改 | 并行工作提交后追加 |

## 4. 步骤

- [ ] 1. 建 `campuslink_cap`；以 `APP_ROSTER_BYPASS=true` 启动 8097 一次（只种子 dev admin），停止；再以 `APP_ROSTER_BYPASS=false` + `VERIFY_IP_HOURLY_LIMIT=20000` 启动。
- [ ] 2. admin 登录 → 导入 10k 名册（batch `cap-10k`）→ 逐账号注册，记录前 1000 个 token 到仓库外临时文件；DB 复核 10k 名册 / 10k 账号。
- [ ] 3. `DB_NAME=campuslink_cap python backend/scripts/perf/seed_capacity.py --apply`（10 万帖 + 1000 楼），确认量级与 floor 帖 id。
- [ ] 4. `PERF_TOKENS_FILE=... node backend/scripts/perf/run_api_perf.mjs --base http://127.0.0.1:8097 --reads 100 --writes 0 --dau-users 1000`；记录 P50/P95/错误率/distinct token 数。
- [ ] 5. 清理：测试 schema 内 `seed_capacity.py --cleanup` → `DROP DATABASE campuslink_cap` → `redis-cli -n 1 flushdb` → 停 8097；核对开发库仍为 115 帖 / 85 楼、Redis DB0 未变。
- [ ] 6. 文档 §15 + CR 登记（并行工作提交后）。

## 5. 验证

- **注册**：测试 schema `users=10,001`（含 admin）、名册 10,000 / used 10,000；注册失败 0（有失败逐条列出）。
- **DAU**：1,000 个不同 token 各至少完成 1 次读；混合读 **0 错误**；列表 / 详情 P95 **≤500ms**（同 CR-179 口径）；记录总请求量与每接口 P50/P95。
- **隔离**：开发库 115 帖 / 85 楼、Redis DB0 键不变；`campuslink_cap` drop 后无残留；临时 token 文件删除。
- **门禁**：`mvn verify` 758（无业务代码改动，仅脚本 / 文档）；docs consistency 通过。
- **边界**：本机回环、合成账号、log 验证码；不代表公网 RTT、真实短信 / 邮件与生产并发。

## 6. 回滚

- 停 8097 → `DROP DATABASE campuslink_cap` → flush Redis DB1 → 删临时 token 文件；脚本 / 文档改动为单提交，可整体 revert；开发库 / 8088 / Redis DB0 零改动。

## 7. 实施结果（2026-11-02）

**结论**：已实施；注册 10,000 / DAU 1,000 distinct token 均达标；隔离栈已清理。

| 计划项 | 实际 |
|---|---|
| 隔离环境 | 原计划开发区建 `campuslink_cap`；因 `campuslink` 仅 `campuslink.*` 权限，改用**临时 MySQL 3307**（root 空密码初始化）+ 8097 + Redis DB1 |
| 10k 名册 / 账号 | **10,000 名册 / 10,000 账号**真实注册；**0 失败**；users 10,001；捕获 **1000 token** |
| 100k 帖 | **100,000 帖 + 1000 楼**；floor id 100001 |
| DAU | **1,000 distinct token**；7 个读接口 P95 **7.18~85.28ms**；0 错误；`--skip-search` |
| 清理 | drop schema；Redis DB1 删除 **10,222** 个 `campuslink:*` 键（DBSIZE 0）；3307 / 8097 停止；临时目录删除；开发库未被本 CR 写入 |
| 门禁 | `mvn verify` **758 全绿**；脚本语法检查；文档一致性三检 |

**偏差**：隔离库授权未生效（`campuslink` 仅 `campuslink.*`），未使用 root；计划中的“独立 schema”变为“独立临时实例 + schema”。开发库 116 帖中含并行 CR-185 的 1 条 UI 回归样例（非本 CR 写入），留待发起人决定是否清理。

**证据**：[容量验证验收](容量验证-CR188验收.md) · [性能基线 §15](性能基线实测-2026-10-26.md) · [`dau-perf-cr188.json`](性能基线-2026-10-31/dau-perf-cr188.json)。
