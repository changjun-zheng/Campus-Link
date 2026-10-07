# Campus-Link

[![backend-ci](https://github.com/ChangjunZheng/Campus-Link/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/ChangjunZheng/Campus-Link/actions/workflows/backend-ci.yml)
[![frontend-ci](https://github.com/ChangjunZheng/Campus-Link/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/ChangjunZheng/Campus-Link/actions/workflows/frontend-ci.yml)

面向**重庆工程学院计算机专业学生**的垂直交流论坛：**问有所答、学有同伴、求职有路**。

这是一个按企业研发流程（BRD → PRD → 设计 → 开发 → 测试 → 发布 → 运维）推进的课程实践项目：单人开发 + AI 协作，全流程文档化——从商业需求到接口契约、从架构决策记录到逐条变更台账，都在 [`docs/`](docs/README.md) 里可追溯。

## 功能特性

**社区核心**

- 固定 6 版块：技术问答 / 学习资源 / 面经求职 / 竞赛交流 / 课程交流 / 闲聊灌水
- Markdown 发帖与回帖：服务端单点渲染（flexmark + jsoup 白名单防 XSS）+ 前端代码高亮，标签 0~5 个（可搜索可自建）
- 楼层回复 + **引用回复**（被引用者收站内通知）、**问答最佳答案采纳**（可更换，最佳答案置顶）
- 帖子删除（作者软删）、发帖页相似帖推荐（ngram 全文检索）

**互动与发现**

- 点赞 / 收藏（toggle + 计数回显）、热榜（定时任务按互动分 × 时间衰减公式刷新）
- 站内搜索（MySQL ngram 中文全文 + 标签兜底，按相关度排序）、首页「最新 / 热门 / 关注」三种排序
- 关注关系与「关注」Feed、他人主页（公开资料 + 帖子时间线）
- 通知中心（回复 / 引用 / 采纳 / 点赞 / 收藏五类，未读角标、单条与全部已读）
- 阅读数（同账号每日去重计数）与封面图（自动提取正文首图）

**账号与个人中心**

- **学籍核验注册**：学号 + 姓名与预置名册比对（限本校），防名册枚举；邮箱验证码登录（无密码）
- 个人资料编辑、我的帖子 / 我的回帖、账号注销（7 天冷静期，期内可撤销，期满匿名化定时任务）

**安全与治理**

- 内容机审网关（fail-closed：网关不可用即拒绝发布，真实服务商可插拔）
- 举报（24h 去重）→ 工单闭环（联动下架 / 驳回）、内容下架 / 恢复（全程审计日志）
- 用户封禁 / 解封（即时生效）、管理后台（仅超级管理员，角色判定由守护测试机器强制）

## 技术栈

| 层 | 选型 |
|---|------|
| 后端 | Spring Boot 4.1 + Java 21 + MyBatis-Plus 3.5.17 + Flyway + springdoc-openapi |
| 数据库 | MySQL 8.0（ngram 全文索引支持中文检索） |
| 缓存 | Redis 7（限流 / 热榜 / 分布式锁） |
| 前端 | Vue 3 + Vite + TypeScript + Pinia + Element Plus（按需自动引入）+ Tailwind CSS v4（设计令牌 + 工具层） |
| 渲染 | flexmark-java + jsoup 白名单（服务端单点渲染防 XSS）+ highlight.js |
| 架构 | 模块化单体 + DDD 四层（`module/account` / `module/forum` / `module/notification` …，ADR-012） |
| 部署 | Docker Compose 保留至发布阶段；部署形态待定（公网 + ICP 备案 或 校园内网） |

## 架构与质量约束

- **DDD 四层 + 依赖方向机器强制**：domain / application / infrastructure / web 四层与跨上下文调用规则由 ArchUnit 守护测试（`ArchitectureGuardTest`，G1~G9）持续校验——分层被破坏、鉴权声明被漏写、管理端角色判定被摘除，测试即红；
- **安全基线**：敏感信息明文一律 AES-GCM 加密存储 + HMAC 哈希等值查询；Markdown 渲染唯一出口 + 6 个 XSS 回归用例；生产 profile 启动即校验（`ProductionSafetyGuard`：密钥全覆盖、开发开关写死合规值，漏配进程起不来）；
- **API 契约**：统一 `ApiResponse` / `ApiError` 外壳、错误码分段（1xxx~5xxx），OpenAPI 快照逐轮归档（当前 40 端点 / 35 路径，见 [接口契约](docs/设计/接口契约/README.md)）。

## 快速开始

环境要求：**JDK 21**、**Node.js 18+**、**MySQL 8**、**Redis 7**。

**后端**（默认端口 8088）：

```bash
# 首次：先建库（表结构由 Flyway 在启动时自动迁移，无需手工导入）
mysql -h127.0.0.1 -uroot -p -e "CREATE DATABASE IF NOT EXISTS campuslink DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

cd backend
mvn spring-boot:run          # 健康检查 /actuator/health · API 文档 /api/docs
```

**前端**（默认端口 5173，`/api` 代理到 8088）：

```bash
cd frontend
npm install
npm run dev
```

**本地开发便利**（仅限开发环境，生产由启动守卫强制关闭）：

- 邮箱验证码固定为 `123456`（`campuslink.captcha.fixed-code`，启动时打 WARN）；
- 学籍核验可开旁路 `campuslink.roster.bypass=true`（默认开）配合内置测试名册（学号 9 位数字）；45 个合成测试账号见 [开发测试账号清单](docs/开发/开发测试账号清单.md)；
- 以 `prod` profile 启动时上述开关被强制为合规值、五个凭据环境变量缺一不可，漏配即启动失败。

## 测试

```bash
cd backend && mvn verify      # 354 个单测全绿：含 ArchUnit 架构守护（G1~G9）与 Markdown XSS 回归
cd frontend && npm run build
```

联调冒烟路径：注册（学籍核验 → 邮箱验证码）→ 登录 → 首页 → 进版块 → 发帖（Markdown + 代码块）→ 详情页回帖 / 引用 / 采纳 → 点赞收藏 → 搜索 → 通知中心。

## 文档导航

全部文档在 [`docs/`](docs/README.md)（[文档中心](docs/README.md)为索引与版本登记处），重点入口：

| 类别 | 文档 |
|------|------|
| 进度总览 | [进度驾驶舱](docs/进度驾驶舱.html)（浏览器打开）· [下一步行动](docs/下一步行动.md) · [变更台账](docs/变更日志/变更台账.md)（CR-001 起逐条登记） |
| 需求与流程 | [产品需求文档](docs/需求/产品需求文档.md) · [研发流程手册](docs/流程手册.md) · [流程偏离记录](docs/流程偏离记录.md) |
| 设计 | [技术方案](docs/设计/技术方案.md)（ADR-001~012）· [接口契约快照](docs/设计/接口契约/README.md) · [UI 规范](docs/设计/UI规范/界面规范.md) |
| 开发 | Sprint 计划与增量设计（`docs/开发/`）· 各变更实施方案（`docs/开发/实施方案/`）· [AGENTS.md](AGENTS.md)（AI 协作约定） |

## 项目进展

当前处于**阶段四 · 开发**：

- ✅ Sprint 1（账号链路：注册 / 登录 / 学籍核验）· Sprint 2（论坛最小闭环）· Sprint 3（采纳 / 点赞收藏 / 搜索 / 通知 / 热榜）
- ✅ 治理主线：机审网关、举报与工单闭环、内容处置、封禁、注销冷静期、管理后台、标签闭环、引用回复、关注 Feed、阅读数与封面图
- ⏳ 进行中 / 待办：真实机审服务商接入、真实学籍名册导入与关旁路回归、提测门工程项（JaCoCo 覆盖率 / CI 静态扫描 / 集成测试）、隐私政策与部署形态决策

逐条状态以[变更台账](docs/变更日志/变更台账.md) §1 与[进度驾驶舱](docs/进度驾驶舱.html)为准，本节不重复。

## 仓库结构

```
Campus-Link/                  # 单一 git 仓库（monorepo），默认分支 main
├── backend/                  # Spring Boot 服务端（Flyway 迁移、module/* DDD 四层、架构守护测试）
├── frontend/                 # Vue 3 Web 前端（设计令牌层 + Tailwind v4 工具层）
├── docs/                     # 全流程项目文档（需求 / 设计 / 开发 / 评审 / 变更日志）
└── .github/workflows/        # CI：backend-ci（mvn verify）/ frontend-ci（npm run build）
```
