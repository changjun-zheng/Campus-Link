# -*- coding: utf-8 -*-
"""D002：灌入固定种子标签集（CR-144 / CR-075 建议 N6 后半）。

背景：CR-083 已放开自由输入标签，但冷启动期 tags 表只有零星几个——
CR-075 建议「先灌一组固定种子标签（课程名 / 技术栈 / 竞赛名）」，
让发帖建议与标签搜索开箱有常用项，避免「tags 表空、推荐无从谈起」。

约定（同 _template.py，CR-014）：
  1. 默认 dry-run，只有显式 --apply 才写库；
  2. 凭据只从环境变量读取；
  3. 幂等：INSERT IGNORE，靠 tags.uk_tags_name 唯一键跳过已存在行
     （不 UPDATE 既有行的 use_count——种子不伪造热度）；
  4. DDL 不放这里（Flyway 职责），本脚本只做纯数据插入。

用法：
    python data/D002__seed_tags.py            # 干跑，打印将插入的行数
    python data/D002__seed_tags.py --apply    # 真正执行
"""

import argparse
import os
import sys

import pymysql

# ---- 连接信息：只从环境变量读，默认值与 application.yml 的开发默认值一致 ----
DB_CONFIG = {
    "host": os.environ.get("DB_HOST", "127.0.0.1"),
    "port": int(os.environ.get("DB_PORT", "3306")),
    "database": os.environ.get("DB_NAME", "campuslink"),
    "user": os.environ.get("DB_USER", "campuslink"),
    "password": os.environ.get("DB_PASSWORD", "campuslink"),
    "charset": "utf8mb4",
    "autocommit": False,
}

# 种子标签集：与 frontend/src/views/PublishView.vue 的 TAG_SUGGESTIONS 同源
# （CR-144 注：两处各维护一份是刻意代价——标签是运营数据、建议是 UI 常量，联动的唯一约定是本注释）。
SEED_TAGS = [
    # 课程名（计算机专业核心课）
    "数据结构", "操作系统", "计算机网络", "数据库原理", "Java 程序设计",
    "Web 开发", "软件工程", "计算机组成原理", "编译原理", "人工智能",
    "离散数学", "线性代数",
    # 技术栈
    "Java", "Spring Boot", "MySQL", "Redis", "Vue", "Python", "C",
    "C++", "JavaScript", "TypeScript", "Linux", "Git", "Docker",
    # 竞赛
    "蓝桥杯", "软件杯", "ACM", "互联网+", "挑战杯", "数学建模",
    # 求职与通用
    "实习", "校招", "面经", "考研", "课程设计", "期末复习",
    "学习笔记", "前端", "算法", "大一", "大二", "大三", "大四",
]


def main() -> int:
    parser = argparse.ArgumentParser(description="灌入固定种子标签集（默认 dry-run）")
    parser.add_argument("--apply", action="store_true", help="真正写库；不加则只干跑")
    args = parser.parse_args()

    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM tags")
            (before,) = cur.fetchone()
            print(f"[检查] 当前 tags 行数：{before}")

            # INSERT IGNORE + uk_tags_name：已存在的标签（含自由输入产生的）原样保留
            rows = [(name,) for name in SEED_TAGS]
            affected = cur.executemany(
                "INSERT IGNORE INTO tags (name, use_count) VALUES (%s, 0)", rows
            )
            print(f"[执行] INSERT IGNORE 影响 {affected} 行（种子 {len(SEED_TAGS)} 个，已存在自动跳过）")

            cur.execute("SELECT COUNT(*) FROM tags")
            (after,) = cur.fetchone()
            print(f"[核对] 执行后 tags 行数：{after}（{before} + {affected}）")

        if args.apply:
            conn.commit()
            print("[提交] 已提交")
        else:
            conn.rollback()
            print("[干跑] 已回滚（未写库）。确认无误后加 --apply 重新执行")
        return 0
    except Exception as e:  # noqa: BLE001 - 脚本入口，需保证任何异常都回滚并给出非零退出码
        conn.rollback()
        print(f"[失败] 已回滚：{e}", file=sys.stderr)
        return 1
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
