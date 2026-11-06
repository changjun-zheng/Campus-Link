#!/usr/bin/env python3
"""开发库合成帖子样例（UI-08 / 2026-11-07）。

目的：让本机开发库的首页、列表、详情和搜索有接近真实社区的浏览效果，覆盖
六个版块、长短标题、零回复 / 多回复、已采纳 / 未采纳、代码块、图片、标签、
匿名作者与不同作者。全部内容为合成文本，不使用真实学号、手机号或私人信息。

与 D001~D005 的差异（有意）：本脚本**通过真实 HTTP 接口发帖**，而不是直接
INSERT。content_html 的唯一合法出口是服务端 MarkdownRenderer（ADR-005），
标签规范化、机审、计数与通知也只在应用服务里有一份；直接写表会让演示数据
与真实链路漂移。数据库只用于两件事：按标题判断是否已存在（幂等）和
`--cleanup` 的定向清理。

用法：
  python backend/scripts/data/D006__seed_dev_synthetic_posts.py            # dry-run
  python backend/scripts/data/D006__seed_dev_synthetic_posts.py --apply    # 写入
  python backend/scripts/data/D006__seed_dev_synthetic_posts.py --cleanup --apply

前置：后端已启动（默认 http://localhost:8088），开发固定验证码为 123456。
脚本使用 dev-stu-01 ~ dev-stu-12 共 12 个开发账号，各登录一次。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

import pymysql

API = os.environ.get("CAMPUSLINK_API", "http://localhost:8088/api/v1")
DB = dict(
    host=os.environ.get("CAMPUSLINK_DB_HOST", "127.0.0.1"),
    port=int(os.environ.get("CAMPUSLINK_DB_PORT", "3306")),
    user=os.environ.get("CAMPUSLINK_DB_USER", "campuslink"),
    password=os.environ.get("CAMPUSLINK_DB_PASSWORD", "campuslink"),
    database=os.environ.get("CAMPUSLINK_DB_NAME", "campuslink"),
    charset="utf8mb4",
)
CAPTCHA = "123456"


def email(n: int) -> str:
    return f"dev-stu-{n:02d}@dev.campuslink.local"


def call(method: str, path: str, body=None, token: str | None = None) -> dict:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "replace")
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {"code": exc.code, "message": raw}


def login(n: int) -> str:
    target = email(n)
    sent = call("POST", "/auth/captcha", {"target": target})
    if sent.get("code") == 2002:
        # 同一账号 60 秒内只能发一次验证码；重跑脚本时等过窗口再试一次
        print(f"  {target} 验证码冷却中，等待 61s ...")
        time.sleep(61)
        sent = call("POST", "/auth/captcha", {"target": target})
    if sent.get("code") != 0:
        raise RuntimeError(f"captcha failed for {target}: {sent}")
    result = call("POST", "/auth/login", {"account": target, "code": CAPTCHA})
    if result.get("code") != 0:
        raise RuntimeError(f"login failed for {target}: {result}")
    return result["data"]["token"]


# ---------------------------------------------------------------------------
# 样例内容：每个元组 = (版块, 作者号, 标题, Markdown, 标签, 匿名, 回复, 采纳回复序号, 点赞账号, 收藏账号)
# 回复元组 = (作者号, Markdown)
# ---------------------------------------------------------------------------

POSTS = [
    (
        "qna", 1,
        "Java 集合遍历时报 ConcurrentModificationException，怎么定位到具体哪一行？",
        """做课程作业时边遍历 `ArrayList` 边删除元素，运行时报了 `ConcurrentModificationException`。代码大概是这样：

```java
for (String s : list) {
    if (s.startsWith("test")) {
        list.remove(s);
    }
}
```

已经知道要改用 `Iterator.remove()` 或 `removeIf()`，但还想请教三个问题：

1. 异常栈里怎么快速定位到实际触发修改的那一行？
2. 为什么 `for-each` 会触发，而普通 `for` 循环不会？
3. 有没有 IDE 插件能在编码时就提示这种写法？

先谢谢各位。""",
        ("Java", "集合", "排错"), False,
        (
            (2, "`for-each` 底层是 Iterator，`list.remove` 改的是 modCount；下一次 `next()` 调 `checkForComodification` 就抛了。定位时看异常栈里 `AbstractList$Itr.checkForComodification` 的上一帧，基本就是 remove 的调用点。"),
            (3, "推荐直接 `list.removeIf(s -> s.startsWith(\"test\"))`，JDK 8+ 一行解决，也不会踩迭代器失效。"),
        ), 0, (2, 3, 4), (2,)),
    (
        "qna", 2,
        "数据库课程实验分组还差一人，有一起做图书管理系统的吗？",
        """我们组现在 3 个人，还差 1 位同学一起做数据库课程实验，题目是图书管理系统。

- 时间：本周内定题，周末前交第一阶段
- 分工：我负责表设计与后端，另外两位做前端和测试
- 要求：能参加周四晚上的线上讨论，愿意写 SQL

有意向的同学回帖或私信我，备注一下擅长部分。""",
        ("课程", "组队"), False, (), None, (3,), (),
    ),
    (
        "resources", 3,
        "整理了一份 Java 后端校招知识图谱：从集合到 Spring Boot 的 12 个主题",
        """最近把校招复习资料重新整理了一遍，按主题拆成 12 块，适合用来查漏补缺。

![学习环境](https://images.unsplash.com/photo-1519389950473-47ba0277781c?w=800)

**基础**
1. 集合：ArrayList / HashMap / ConcurrentHashMap
2. 并发：线程池、锁、AQS
3. JVM：内存区域、GC、类加载

**框架**
4. Spring IoC / AOP
5. Spring Boot 自动配置
6. MyBatis 与 SQL 调优

**工程**
7. MySQL 索引与事务
8. Redis 缓存与过期策略
9. 消息队列与幂等

**项目**
10. 分层与 DDD 边界
11. 单元测试与覆盖率
12. 部署与可观测性

建议先按主题过一遍目录，再挑不熟的两块做笔记。欢迎补充。""",
        ("Java", "校招", "知识图谱"), False,
        (
            (4, "集合和并发这两块建议配合源码看，HashMap 的扩容和线程池的拒绝策略是高频追问。"),
            (5, "补充一个：Redis 的缓存穿透和雪崩最好能结合项目讲，不要只背概念。"),
        ), None, (4, 5, 6), (4, 5),
    ),
    (
        "resources", 4,
        "操作系统期末复习：进程调度与死锁的思维导图",
        """把进程调度和死锁两章整理成了一张图，重点标了：

- FCFS / SJF / 优先级 / 时间片轮转的适用场景
- 死锁四个必要条件与银行家算法
- 常考的计算题：周转时间、带权周转时间

需要原图的同学回帖，我发到网盘。""",
        ("操作系统", "复习"), False, (), None, (), (),
    ),
    (
        "interview", 5,
        "小公司一面凉经：HashMap 从 put 追问到红黑树，最后卡在扩容",
        """面了一家做企业服务的小公司，Java 后端实习岗，一面 40 分钟基本都在问 HashMap。

- `put` 的流程，哈希扰动为什么右移 16 位
- 链表什么时候转红黑树，什么时候退化
- 扩容为什么是 2 倍，rehash 的过程
- 并发场景下 HashMap 会有什么问题

最后让我手写一个简化版 `put`，卡在扩容时的元素迁移上。面试官人很好，还提示了 `e.hash & oldCap` 的判断。复盘一下，还是源码看得不够细。

想问下大家，准备这类源码题是背流程还是跟着 debug 走一遍更有效？""",
        ("Java", "面试", "HashMap"), False,
        (
            (6, "跟着 debug 走一遍，自己画一次扩容前后的桶分布，比背流程稳。"),
            (7, "推荐看一下 JDK 8 的 `resize()`，重点理解 loHead / hiHead 两条链怎么拆。"),
            (8, "手写 put 太狠了……不过把 putVal 的主干记住就够应付大多数一面。"),
        ), None, (6, 7, 8, 9), (6,),
    ),
    (
        "interview", 6,
        "秋招简历求看：项目经历只写 CRUD 会不会太单薄？",
        """目前简历上只有一个课程项目，写的是“基于 Spring Boot 的学生管理系统”，功能就是增删改查。

想请教：

1. 这种项目怎么写出亮点，才不像流水账？
2. 需不需要补一个 Redis / 消息队列的模块再写上去？
3. 面试官会更看重功能数量还是技术深度？

附上我的项目描述（已隐去个人信息）：

> 负责后端接口开发，使用 Spring Boot + MyBatis 实现学生信息的增删改查，使用 MySQL 存储数据。

欢迎直接指出问题，感谢。""",
        ("求职", "简历"), False, (), None, (), (),
    ),
    (
        "contest", 7,
        "蓝桥杯省赛找前端队友：会 Vue 3 + 图表，目标省二冲省一",
        """准备参加明年的蓝桥杯省赛，目前有两个人：

- 我：负责前端，Vue 3 + ECharts，做过两个课程项目
- 另一位：负责后端，Java / Python 都可以

想再找 1~2 位队友，最好满足其中一项：

- 有算法竞赛经验（DP / 图论）
- 能稳定参加每周两次的线上训练

我们计划先把近五年的真题刷一遍，再按题型分工。有意向的同学回帖，简单介绍一下自己的情况就行。""",
        ("蓝桥杯", "组队", "前端"), False,
        (
            (8, "会一点图论和 DP，之前打过校赛，可以一起练。"),
            (9, "建议先把省赛真题的通过率刷上去，再考虑冲省一，节奏会比较稳。"),
        ), None, (8, 9), (8,),
    ),
    (
        "contest", 8,
        "分享我的蓝桥杯刷题计划：每天两道 DP，坚持 30 天",
        """给自己定了一个 30 天计划，每天两道动态规划，从线性 DP 到区间 DP，按题型推进。

第一周：背包、最长上升子序列
第二周：区间 DP、树形 DP
第三周：状态压缩、概率 DP
第四周：真题套卷 + 错题复盘

每天把题号和思路记在表格里，周末统一回看。有没有同学想一起打卡？可以在这条帖子下面互相监督。""",
        ("蓝桥杯", "算法", "打卡"), False, (),
        None, (10,), (),
    ),
    (
        "course", 9,
        "数据库原理实验三：事务隔离级别怎么用两个会话验证？",
        """实验要求验证 MySQL 的四种隔离级别，我开了两个命令行会话，想确认步骤对不对。

先确认当前隔离级别：

```sql
SELECT @@transaction_isolation;
```

然后把两个会话都设成 READ COMMITTED，会话 A 开启事务并更新一行，会话 B 再读：

```sql
-- 会话 A
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
UPDATE account SET balance = balance - 100 WHERE id = 1;

-- 会话 B
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT balance FROM account WHERE id = 1;
```

我的理解是 B 读不到 A 未提交的修改，A 提交后才能读到。想问：

- 验证不可重复读时，B 需要在 A 提交前后各读一次吗？
- 实验报告里要不要把 `SHOW ENGINE INNODB STATUS` 的输出也贴上？

谢谢。""",
        ("MySQL", "事务", "课程"), False,
        (
            (10, "对，不可重复读就是 B 在 A 提交前后各读一次，比较两次结果。RR 级别下第二次读应该还是旧值。"),
            (11, "`SHOW ENGINE INNODB STATUS` 可以贴，重点看 TRANSACTIONS 段里的锁等待，报告会更完整。"),
        ), None, (10, 11, 12), (10,),
    ),
    (
        "course", 10,
        "编译原理课程设计选题求建议：词法分析器还是小型解释器？",
        """课程设计可以二选一：

1. 词法分析器 + 语法分析器，输出语法树
2. 一个小型解释器，支持变量、四则运算和 if

我目前只学过递归下降，感觉第一个稳一点，第二个更有意思但怕做不完。老师给的周期是三周，每周还要上课。

有做过的同学能给点建议吗？主要是时间投入和答辩时的展示效果。""",
        ("编译原理", "课程设计"), False, (), None, (), (),
    ),
    (
        "chat", 11,
        "食堂三楼新窗口的牛肉面值得一试，13 块分量很足",
        """中午去试了三楼新开的窗口，牛肉面 13 块，牛肉给得不少，汤也比较清。

缺点是人有点多，12 点去排了差不多十分钟。建议错峰，11:40 左右去基本不用等。

有其他窗口推荐的同学也可以在这条下面补充，争取整理一个食堂攻略。""",
        ("校园生活", "食堂"), False,
        (
            (12, "二楼的面也不错，更清淡一点，价格便宜两块。"),
            (1, "三楼那家可以加面吗？上次没敢问。"),
            (11, "可以，加面不加钱，跟师傅说一声就行。"),
        ), None, (12, 1, 2), (12,),
    ),
    (
        "chat", 12,
        "匿名聊聊：大家最近在学什么？我有点找不到方向",
        """最近课不多，但每天东学一点西学一点，Java 看了几章又去摸前端，结果哪边都不深。

想匿名问问大家：

- 你们最近在学什么？
- 是怎么定下方向的？
- 有没有那种“先做一个小项目再回头补理论”的经验？

不想再漫无目的地刷视频了，想听听真实的经历。""",
        ("闲聊", "学习"), True,
        (
            (2, "我是先挑一个课程项目做完，再根据卡住的地方补理论，效率比从头看书高。"),
            (3, "建议给自己定一个可交付的小目标，比如两周内做一个能跑起来的小工具，方向会慢慢清晰。"),
        ), None, (2, 3), (2,),
    ),
]

TITLES = [p[2] for p in POSTS]


def db_connect():
    return pymysql.connect(**DB)


def existing_titles(conn) -> set[str]:
    with conn.cursor() as cur:
        placeholders = ",".join(["%s"] * len(TITLES))
        cur.execute(
            f"SELECT title FROM posts WHERE title IN ({placeholders}) AND is_deleted = 0",
            TITLES,
        )
        return {row[0] for row in cur.fetchall()}


def cleanup(conn, apply: bool) -> None:
    with conn.cursor() as cur:
        placeholders = ",".join(["%s"] * len(TITLES))
        cur.execute(f"SELECT id FROM posts WHERE title IN ({placeholders})", TITLES)
        post_ids = [row[0] for row in cur.fetchall()]
        if not post_ids:
            print("没有找到样例帖子，无需清理。")
            return
        p_marks = ",".join(["%s"] * len(post_ids))
        cur.execute(f"SELECT id FROM replies WHERE post_id IN ({p_marks})", post_ids)
        reply_ids = [row[0] for row in cur.fetchall()]
        print(f"将清理：posts={len(post_ids)} replies={len(reply_ids)}（关联通知 / 点赞 / 收藏 / 标签一并删除）")
        if not apply:
            print("dry-run：未删除。加 --apply 执行。")
            return
        if reply_ids:
            r_marks = ",".join(["%s"] * len(reply_ids))
            cur.execute(f"DELETE FROM notifications WHERE target_type = 'REPLY' AND target_id IN ({r_marks})", reply_ids)
            cur.execute(f"DELETE FROM likes WHERE target_type = 'REPLY' AND target_id IN ({r_marks})", reply_ids)
        cur.execute(f"DELETE FROM notifications WHERE target_type = 'POST' AND target_id IN ({p_marks})", post_ids)
        cur.execute(f"DELETE FROM likes WHERE target_type = 'POST' AND target_id IN ({p_marks})", post_ids)
        cur.execute(f"DELETE FROM favorites WHERE post_id IN ({p_marks})", post_ids)
        cur.execute(f"DELETE FROM post_tags WHERE post_id IN ({p_marks})", post_ids)
        cur.execute(f"DELETE FROM replies WHERE post_id IN ({p_marks})", post_ids)
        cur.execute(f"DELETE FROM posts WHERE id IN ({p_marks})", post_ids)
    conn.commit()
    print("清理完成。")


def seed(conn) -> None:
    existing = existing_titles(conn)
    todo = [p for p in POSTS if p[2] not in existing]
    created: list[tuple[int, dict]] = []
    if todo:
        needed = sorted({p[1] for p in todo} | {r[0] for p in todo for r in p[6]} | {n for p in todo for n in p[8] + p[9]})
        print(f"需要登录 {len(needed)} 个开发账号：{', '.join(f'{n:02d}' for n in needed)}")
        tokens: dict[int, str] = {}
        for n in needed:
            print(f"  登录 dev-stu-{n:02d} ...")
            tokens[n] = login(n)

        for board, author, title, md, tags, anon, replies, accept, likes, favorites in POSTS:
            if title in existing:
                print(f"跳过已存在：{title}")
                continue
            resp = call("POST", "/posts", {
                "boardCode": board,
                "title": title,
                "contentMd": md,
                "tags": list(tags),
                "anonymous": anon,
            }, tokens[author])
            if resp.get("code") != 0:
                raise RuntimeError(f"publish failed: {title}: {resp}")
            post_id = resp["data"]["id"]
            print(f"发布 #{post_id} [{board}] {title}")

            reply_ids = []
            for reply_author, reply_md in replies:
                r = call("POST", f"/posts/{post_id}/replies", {"contentMd": reply_md}, tokens[reply_author])
                if r.get("code") != 0:
                    raise RuntimeError(f"reply failed on {post_id}: {r}")
                reply_ids.append(r["data"]["id"])

            if accept is not None and reply_ids:
                r = call("POST", f"/posts/{post_id}/accept", {"replyId": reply_ids[accept]}, tokens[author])
                if r.get("code") != 0:
                    raise RuntimeError(f"accept failed on {post_id}: {r}")

            for n in likes:
                r = call("POST", f"/posts/{post_id}/like", None, tokens[n])
                if r.get("code") != 0:
                    raise RuntimeError(f"like failed on {post_id}: {r}")
            for n in favorites:
                r = call("POST", f"/posts/{post_id}/favorite", None, tokens[n])
                if r.get("code") != 0:
                    raise RuntimeError(f"favorite failed on {post_id}: {r}")
            created.append((post_id, {"title": title}))
    else:
        print("样例帖子已存在，只校准时间线。")

    # 让首页的时间线有自然的先后节奏：最早的样例退到 24 小时前，最新一条约 2 小时前。
    # 覆盖全部样例帖（含上次中断前已写入的），保证重跑后时间线一致；只改 posts 的时间列，
    # 回复时间保持发布时刻（晚于帖子），不影响楼层顺序。
    # 数据库与接口按 UTC 存 / 返回（V1 后时间列口径），这里必须用 UTC 的“现在”；
    # 用本地时间会把样例写成未来 8 小时。先 commit 结束旧快照，才能看到 API 刚提交的新帖。
    now = datetime.now(timezone.utc).replace(tzinfo=None, microsecond=0)
    conn.commit()
    with conn.cursor() as cur:
        placeholders = ",".join(["%s"] * len(TITLES))
        cur.execute(f"SELECT id FROM posts WHERE title IN ({placeholders}) ORDER BY id", TITLES)
        all_ids = [row[0] for row in cur.fetchall()]
        for index, post_id in enumerate(all_ids):
            created_at = now - timedelta(hours=(len(all_ids) - index) * 2)
            cur.execute(
                "UPDATE posts SET created_at = %s, updated_at = %s WHERE id = %s",
                (created_at, created_at, post_id),
            )
    conn.commit()
    print(f"完成：新增 {len(created)} 帖。热榜分数由定时任务在下一轮刷新（或重启后端立即刷新）。")


def main() -> int:
    parser = argparse.ArgumentParser(description="开发库合成帖子样例（UI-08）")
    parser.add_argument("--apply", action="store_true", help="实际写入 / 删除（默认 dry-run）")
    parser.add_argument("--cleanup", action="store_true", help="只清理本脚本创建的样例帖子")
    args = parser.parse_args()

    conn = db_connect()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM posts WHERE is_deleted = 0")
            total = cur.fetchone()[0]
        print(f"当前开发库有效帖子 {total} 条。")
        if args.cleanup:
            cleanup(conn, args.apply)
            return 0
        if not args.apply:
            existing = existing_titles(conn)
            print(f"计划写入 {len(POSTS)} 帖（已存在 {len(existing)}，将跳过）。")
            for board, author, title, *_ in POSTS:
                mark = "跳过" if title in existing else "写入"
                print(f"  [{mark}] [{board}] dev-stu-{author:02d}：{title}")
            print("dry-run：未写入。加 --apply 执行。")
            return 0
        seed(conn)
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
