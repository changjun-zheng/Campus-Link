# 数据模型 ER 图与量级预估

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.3 |
| 状态 | 已归档（设计门行动项 **A3-5** 交付物，[CR-035](../变更日志/变更台账.md)） |
| 维护人 | 技术负责人（发起人兼任） |
| 最后更新 | 2026-10-24 |

> **变更记录**
>
> - **v1.3（2026-10-24）**——同步 `V7__post_content_fulltext.sql`（[CR-136](../变更日志/变更台账.md#cr-136) 正文全文检索）：`posts` 新增 `ft_posts_content (content_md) WITH PARSER ngram`。§4 索引清单 +1 行、§5 扩容触发点改写；**表数仍 16 张、不改任何表结构**。
> - **v1.2（2026-10-24）**——同步 `V6__direct_messages.sql`（[CR-135](../变更日志/变更台账.md#cr-135) 私信）：新建 `conversations` / `messages` 两表。§2 / §3 表数 **14 → 16 张**、§4 索引清单补 `uk_conversations_pair` / 两个会话侧索引 / 两个消息侧索引、§5 补两表量级；**本文不引入任何结构变更**，事实源扩为 V1~V6。
> - **v1.1（2026-10-24）**——同步 V3~V5 结构变更（[CR-134](../变更日志/变更台账.md#cr-134)）：`V4` 给 `posts` 加 `view_count` / `cover_url` 两列并新建 `follows`（[CR-074](../变更日志/变更台账.md)），`V5` 新建 `user_blocks`（[CR-129](../变更日志/变更台账.md)），`V3` 给 `posts` / `replies` 各加一个作者维度复合索引（[CR-071](../变更日志/变更台账.md)）。§2 / §3 表数 **12 → 14 张**、§4 索引清单补 4 个索引、§5 补两表量级；**本文不引入任何结构变更**，事实源扩为 V1~V5。
> - **v1.0（2026-09-13）**——初版（[CR-035](../变更日志/变更台账.md)，设计门 A3-5）。§2 ER 图以 `V1__init_schema.sql` 实际 12 张表为唯一事实源；§5 量级预估以 [PRD](../需求/产品需求文档.md) §4 容量基线为输入。

## 1. 目的、事实源与边界

- **目的**：为设计门行动项 A3-5 提供两件东西——① 现有数据模型的**实体关系可视化**（文字定义在[技术方案 §4](技术方案.md)，本图为可视化对照）；② 按 PRD 容量基线的**数据量级与索引水位预估**，作为测试阶段造数规模与发布阶段容量验收的参考。
- **事实源**：`backend/src/main/resources/db/migration/` 下的**全部迁移**（V1 基线 12 张表 + `V2__seed_boards.sql` 仅种入 6 个版块数据、无结构 + `V3__my_content_indexes.sql` 两个作者维度索引 + `V4__post_views_and_follows.sql` 给 `posts` 加两列并新建 `follows` + `V5__user_blocks.sql` 新建 `user_blocks` + `V6__direct_messages.sql` 新建 `conversations` / `messages` + `V7__post_content_fulltext.sql` 给 `posts` 加正文 FULLTEXT 索引；**已执行迁移不可改**）。**表结构、字段、索引以迁移文件为准**——本文与其冲突时，以迁移文件为准并须修正本文。
- **边界**：本文**不引入任何表结构 / 索引变更**；量级数字均为工程估算（见 §5 口径），**非性能承诺**——性能承诺以 PRD §4 的 P90 / P95 指标为准，由测试阶段实测验收。
- **维护约定**：后续新增 `V<n>__*.sql` 结构变更时，**该 CR 必须同步更新本文**（§2 图、§3 表清单、§4 索引清单、§5 水位），否则视为该 CR 的文档同步遗漏。

## 2. ER 图（16 张表）

```mermaid
erDiagram
    USERS {
        bigint id PK
        varchar_512 email_enc "AES-GCM 加密，可空"
        char_64 email_hash UK "HMAC，唯一"
        varchar_64 nickname "NOT NULL"
        varchar_16 role "USER/OPS/SUPERADMIN"
        varchar_16 status "ACTIVE/BANNED/DEACTIVATED"
        varchar_512 student_id_enc "AES-GCM，运营账号可空"
        char_64 student_id_hash UK "HMAC，一号一账号"
        tinyint verified "是否通过学籍核验"
        tinyint anonymized "注销匿名化标记"
    }
    STUDENT_ROSTER {
        bigint id PK
        char_64 student_id_hash UK "只存哈希，不落明文"
        varchar_64 name "明文姓名，用于比对"
        bigint used_user_id FK "被注册则回填 users.id"
        varchar_64 source_batch "导入批次"
    }
    BOARDS {
        bigint id PK
        varchar_32 code UK "路由标识，如 qna"
        varchar_16 type "QUESTION/DISCUSSION"
        tinyint enabled
    }
    POSTS {
        bigint id PK
        bigint board_id FK
        bigint author_id FK
        varchar_100 title
        mediumtext content_md "Markdown 原文"
        mediumtext content_html "服务端渲染落库（ADR-005）"
        int reply_count "冗余计数"
        int view_count "阅读数（去重计数，V4 / CR-074）"
        varchar_512 cover_url "封面图（正文首张 https 图片，V4 / CR-074）"
        tinyint is_accepted "是否有最佳答案"
        bigint accepted_reply_id "指向 replies.id，无外键"
        double hot_score "定时刷新（ADR-006）"
    }
    TAGS {
        bigint id PK
        varchar_64 name UK
        int use_count
    }
    POST_TAGS {
        bigint post_id PK "同时为指向 posts.id 的外键"
        bigint tag_id FK "指向 tags.id"
    }
    REPLIES {
        bigint id PK
        bigint post_id FK
        bigint author_id FK
        int floor_no "帖内自增楼层号"
        mediumtext content_md
        mediumtext content_html
        bigint quoted_reply_id "引用楼层，无外键"
        tinyint is_accepted
    }
    LIKES {
        bigint user_id PK "无外键"
        varchar_16 target_type PK "POST/REPLY（多态，无外键）"
        bigint target_id PK
    }
    FAVORITES {
        bigint user_id PK "无外键（user 侧）"
        bigint post_id FK
    }
    NOTIFICATIONS {
        bigint id PK
        bigint user_id "接收人，无外键"
        bigint actor_id "触发人，无外键"
        varchar_16 type "reply/like/favorite/accept/quote"
        varchar_16 target_type "多态，无外键"
        bigint target_id
        tinyint is_read
    }
    REPORTS {
        bigint id PK
        bigint reporter_id "举报人，无外键"
        varchar_16 target_type "POST/REPLY/USER（多态）"
        bigint target_id
        varchar_16 status "PENDING/PROCESSING/CLOSED"
        bigint handler_id "处理人，无外键"
    }
    AUDIT_LOGS {
        bigint id PK
        bigint actor_id "可空，无外键"
        varchar_64 action
        varchar_32 target_type
        bigint target_id
    }
    FOLLOWS {
        bigint follower_id PK "联合主键（V4 / CR-074）"
        bigint followee_id PK
        datetime created_at
    }
    USER_BLOCKS {
        bigint blocker_id PK "联合主键（V5 / CR-129）"
        bigint blocked_id PK
        datetime created_at
    }
    CONVERSATIONS {
        bigint id PK
        bigint pair_low_id FK "双方较小 id（V6 / CR-135）"
        bigint pair_high_id FK "双方较大 id，联合唯一"
        bigint last_message_id "最后一条 messages.id，无外键"
        datetime last_message_at "会话列表排序键"
    }
    MESSAGES {
        bigint id PK
        bigint conversation_id FK
        bigint sender_id FK
        bigint receiver_id FK
        varchar_1000 content "纯文本，不落 HTML"
        datetime read_at "NULL 即未读"
    }

    STUDENT_ROSTER |o--o| USERS : "used_user_id 占用名额"
    BOARDS ||--o{ POSTS : "board_id"
    USERS ||--o{ POSTS : "author_id"
    POSTS ||--o{ REPLIES : "post_id"
    USERS ||--o{ REPLIES : "author_id"
    POSTS }o--o{ TAGS : "post_tags 关联表"
    USERS ||--o{ LIKES : "user_id"
    USERS ||--o{ FAVORITES : "user_id"
    POSTS ||--o{ FAVORITES : "post_id"
    USERS ||--o{ NOTIFICATIONS : "user_id 接收"
    USERS ||--o{ REPORTS : "reporter_id"
    USERS ||--o{ AUDIT_LOGS : "actor_id 可空"
    USERS ||--o{ FOLLOWS : "follower_id 关注发起"
    USERS ||--o{ FOLLOWS : "followee_id 被关注"
    USERS ||--o{ USER_BLOCKS : "blocker_id 拉黑发起"
    USERS ||--o{ USER_BLOCKS : "blocked_id 被拉黑"
    USERS ||--o{ CONVERSATIONS : "pair_low_id / pair_high_id 会话双方"
    CONVERSATIONS ||--o{ MESSAGES : "conversation_id"
    USERS ||--o{ MESSAGES : "sender_id 发送"
    USERS ||--o{ MESSAGES : "receiver_id 接收"
    CONVERSATIONS |o--o| MESSAGES : "last_message_id 摘要指针"
```

**图上画不出的三处关系（如实登记）**：

1. **多态目标无外键**：`likes` / `notifications` / `reports` 的 `target_type + target_id` 指向帖子或楼层（reports 另含 USER），数据库层**不建外键**——参照完整性由应用层保证，删除内容时须同步处置相关行（对 PRD F-FORUM-005 删除语义的隐含约束）；
2. **`posts.accepted_reply_id` 与 `replies.quoted_reply_id` 无外键**：采纳与引用为弱关联（楼层被删时置空语义由应用层处理），避免外键在热路径上的级联锁；
3. **`favorites.user_id` 无外键**（V1 原样）：与 `post_tags` 双外键风格不一致，属已知取舍——单校规模下影响可忽略，若后续加外键须新增前向迁移。

## 3. 表清单与职责（16 张）

| 表 | 职责 | 敏感字段处理 | 增长特征 |
|----|------|-------------|---------|
| `users` | 账号 | 邮箱 / 手机 / 学号 AES-GCM 存 `*_enc`，等值查询走 `*_hash`（HMAC），三者各带唯一键 | 注册驱动，试点期 ≤ 1 万，线性且缓慢 |
| `student_roster` | 学籍名册（核验比对源） | 学号**只存 HMAC 哈希**、不落明文；姓名明文仅用于容错比对 | 名册导入驱动（B1/B2 到位后批量），基本不增长 |
| `boards` | 版块（MVP 固定 6 个） | — | 常量级 |
| `posts` | 帖子（`content_html` 发布时服务端渲染落库，请求期零渲染；`view_count` 阅读数去重计数、`cover_url` 正文首图封面，均为 V4 冗余列，读时零成本） | 无敏感字段（正文为 UGC，交内容机审） | **主增长表**，线性，试点期 ≤ 10 万 |
| `tags` / `post_tags` | 标签与关联 | — | 随帖子缓慢线性 |
| `replies` | 楼层回复（`floor_no` 帖内自增，发帖服务内分配保证连续） | 无 | 主增长表，试点期 ≤ 40 万（估算见 §5） |
| `likes` | 点赞去重（PK 即唯一约束） | — | 事件驱动，量最大但行极窄 |
| `favorites` | 收藏 | — | 事件驱动 |
| `notifications` | 站内通知（reply / like / favorite / accept / quote） | — | 事件驱动、只追加，读多写多 |
| `reports` | 举报工单（24h 同对象去重靠应用层按索引查询） | detail 可能含举报描述 | 小表 |
| `audit_logs` | 后台全操作留痕（名册导入、内容处置——生产红线要求） | — | 小表、只追加 |
| `follows` | 关注关系（V4）——联合主键即"一人对一人只有一行"，重复关注天然幂等 | 无 | 事件驱动，约 1 万用户 × 平均关注数，估 ≤ 10 万 |
| `user_blocks` | 用户拉黑（V5，单向）——联合主键即"一人对一人只有一行"，重复拉黑天然幂等 | 无 | 小表，估 < 1 万 |
| `conversations` | 私信会话（V6）——双方 id 按 `(min, max)` 归一，`uk_conversations_pair` 即"一人对一人一个会话"，并发首发天然幂等 | 无（消息正文在 `messages`） | 事件驱动，约 1 万用户 × 活跃会话数，估 < 10 万 |
| `messages` | 私信消息（V6）——纯文本 1~1000 字、只追加；`read_at` 为 NULL 即未读 | 无敏感字段（正文为 UGC，交内容机审） | 私信主增长表，估 < 100 万 |

## 4. 索引清单（照录 V1~V6，新增结构时随 CR 更新）

| 表 | 索引 | 类型 | 支撑的查询路径 |
|----|------|------|---------------|
| `users` | `uk_users_email_hash` / `uk_users_phone_hash` / `uk_users_student_id_hash` | UNIQUE | 登录 / 核验的等值查询（哈希列） |
| `student_roster` | `uk_roster_student_id_hash` | UNIQUE | 核验等值查询（哈希列） |
| `student_roster` | `idx_roster_used_user` | 普通 | 占用反查（学号是否已被注册） |
| `boards` | `uk_boards_code` | UNIQUE | 路由标识定位版块 |
| `posts` | `idx_posts_board_list (board_id, status, is_deleted, created_at)` | 复合 | 版块列表页（最常用路径） |
| `posts` | `idx_posts_latest (status, is_deleted, created_at)` | 复合 | 首页"最新"流 |
| `posts` | `idx_posts_hot (hot_score)` | 普通 | 首页"热门"流（ADR-006 定时刷新分数） |
| `posts` | `ft_posts_title (title) WITH PARSER ngram` | FULLTEXT | 中文标题检索（ngram 分词） |
| `posts` | `ft_posts_content (content_md) WITH PARSER ngram` | FULLTEXT | 正文全文检索（V7 / [CR-136](../变更日志/变更台账.md#cr-136)；与标题索引分开建，相关度才能按「标题 × 2 + 正文 × 1」分别计分） |
| `posts` | `idx_posts_author_list (author_id, is_deleted, created_at)` | 复合 | "我的帖子"列表（V3 / [CR-071](../变更日志/变更台账.md)） |
| `replies` | `idx_replies_author_list (author_id, is_deleted, created_at)` | 复合 | "我的回复"列表（V3 / [CR-071](../变更日志/变更台账.md)，父帖可见性在 JOIN 上判） |
| `tags` | `uk_tags_name` | UNIQUE | 标签归一 |
| `post_tags` | PK `(post_id, tag_id)` | 复合主键 | 关联去重 + 按 post 反查 |
| `replies` | `idx_replies_post_floor (post_id, floor_no)` | 复合 | 楼层分页（单帖 1000 楼的分页加载即靠它） |
| `likes` | PK `(user_id, target_type, target_id)` | 复合主键 | 去重 + "我赞过"；聚合计数走 `posts.like_count` 冗余列，不回表 |
| `favorites` | PK `(user_id, post_id)` | 复合主键 | 收藏去重 + "我的收藏" |
| `notifications` | `idx_notifications_user (user_id, is_read, created_at)` | 复合 | 通知列表 / 未读数（未读计数可上 Redis 缓存，见技术方案 §7.2） |
| `reports` | `idx_reports_target (target_type, target_id, created_at)`；`idx_reports_status (status, created_at)` | 复合 ×2 | 24h 去重查询；处置台队列 |
| `audit_logs` | `idx_audit_actor (actor_id, created_at)` | 复合 | 按操作人审计回溯 |
| `follows` | PK `(follower_id, followee_id)`；`idx_followee (followee_id)` | 复合主键 + 普通 | 关注去重 / "我关注的人"；`idx_followee` 支撑粉丝数与粉丝列表（V4 / [CR-074](../变更日志/变更台账.md)） |
| `user_blocks` | PK `(blocker_id, blocked_id)`；`idx_blocked (blocked_id)` | 复合主键 + 普通 | 拉黑去重 / 屏蔽过滤；`idx_blocked` 支撑反向查询（V5 / [CR-129](../变更日志/变更台账.md)，本期未消费） |
| `conversations` | `uk_conversations_pair (pair_low_id, pair_high_id)`；`idx_conversations_low (pair_low_id, last_message_at)`；`idx_conversations_high (pair_high_id, last_message_at)` | UNIQUE + 复合 ×2 | 会话去重 / 会话列表按最后消息时间倒序（两侧各一条，V6 / [CR-135](../变更日志/变更台账.md#cr-135)） |
| `messages` | `idx_messages_conversation (conversation_id, created_at, id)`；`idx_messages_unread (receiver_id, read_at, conversation_id)` | 复合 ×2 | 会话内倒序分页；未读总数与每会话未读数（V6 / [CR-135](../变更日志/变更台账.md#cr-135)） |

## 5. 量级预估（试点期 1 年，工程估算）

**输入（[PRD](../需求/产品需求文档.md) §4 容量基线，均为"≥"，即验收上限）**：注册 ≥ **1 万**、日活 ≥ **1000**、帖子 ≥ **10 万**、单帖回复 ≥ **1000 楼**（分页加载）。**推导假设（本节自设，非 PRD 基线）**：单校计算机类在校生名册约 5000~8000 人；平均每帖回复 **4 楼**（10 万帖 → 40 万楼；单帖 1000 楼为需分页的极端值）；帖子平均正文 **15 KB**（md + html 双份）；互动率按日活 1000 × 日均 5 次点赞 / 收藏 / 通知类事件估算。

| 表 | 试点期 1 年行数（估） | 单行大小（估） | 数据量（估） | 说明 |
|----|---------------------|---------------|-------------|------|
| `posts` | 10 万 | ~15 KB（md + html 双份为主） | **~1.5 GB** | 最大表；`MEDIUMTEXT` 单列上限 16 MB，充裕 |
| `replies` | 40 万 | ~6 KB | **~2.4 GB** | 40 万楼 × 双文本列 |
| `likes` | 50 万~100 万 | ~50 B | < 100 MB | 行极窄，复合主键聚簇 |
| `notifications` | 100 万~150 万 | ~150 B | < 250 MB | 事件驱动只追加；`(user_id, is_read, created_at)` 索引与数据近 1:1 |
| `favorites` | ~20 万 | ~30 B | < 20 MB | — |
| `follows` | ~10 万 | ~30 B | < 10 MB | 1 万用户 × 平均关注数；联合主键聚簇（V4） |
| `user_blocks` | < 1 万 | ~30 B | < 5 MB | 单向拉黑，小表（V5） |
| `conversations` | < 10 万 | ~60 B | < 10 MB | 1 万用户 × 活跃会话数；`(min,max)` 联合唯一聚簇（V6） |
| `messages` | < 100 万 | ~1 KB（正文为主） | < 1 GB | 私信纯文本，只追加（V6 / [CR-135](../变更日志/变更台账.md#cr-135)） |
| `post_tags` / `tags` | 15 万 / ~200 | 极窄 | < 20 MB | — |
| `users` | 1 万 | ~1 KB（三列 `*_enc` 为主） | ~10 MB | — |
| `student_roster` | 0.5 万~0.8 万 | ~0.2 KB | < 5 MB | 一次性导入 |
| `reports` / `audit_logs` / `boards` | < 0.5 万 / < 2 万 / 6 | — | < 50 MB | 小表 |
| **合计** | — | — | **约 4~5 GB / 年** | 远低于单实例 MySQL 8 舒适区（数十 GB 级） |

**结论**：**单校规模上限内，单实例 MySQL 8 + 现有索引设计即可承载，无需分库分表 / 归档**；试点期一年的真实瓶颈更可能出现在**全文检索的内存占用**与**热榜定时任务（ADR-006）的扫描量**，而非容量本身。

**扩容触发点（达到任一即须评估，提前于问题发生）**：

| 触发点 | 建议动作 |
|--------|---------|
| 库总量 > **20 GB** 或 `posts` > **50 万行** | 冷帖归档（`is_deleted` + 老帖迁历史表）或按 `created_at` 做 RANGE 分区 |
| `posts` / `replies` 平均正文显著超 15 KB（如长代码块） | 评估正文外置对象存储、库内只留摘要指针（改动较大，须先按 CR-034 写实施方案） |
| 全文检索 QPS / 内存成为瓶颈 | 引入 MeiliSearch（规模升级预案；正文当前已由 MySQL ngram 承担，[CR-136](../变更日志/变更台账.md#cr-136)），届时评估两条 FULLTEXT 索引下线 |
| `notifications` 未读数查询变慢 | 未读计数上 Redis（走 `RedisKeys` 前缀约定） |
| 单帖楼层接近 **1000 楼上限**常态化 | 楼层分页游标化（当前 `(post_id, floor_no)` 复合索引已支撑，仅需改查询形态） |
