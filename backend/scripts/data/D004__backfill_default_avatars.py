# -*- coding: utf-8 -*-
"""D004：存量账号回填默认头像（CR-162）。

背景：`users.avatar_url` 自 V1 起存在，但注册链路此前从不写入。CR-162 起注册按
`userId % 24` 分配默认头像；本脚本对存量账号做同样映射的**一次性回填**。

约定（同 _template.py / D003，CR-014）：
  1. 默认 dry-run，只有显式 --apply 才写库；
  2. 凭据只从环境变量读取；
  3. 幂等：只 UPDATE `users.avatar_url IS NULL` 的行，重复执行结果不变；
  4. DDL 不放这里（Flyway 职责），本脚本只做纯数据 UPDATE；
  5. 映射与后端 `DefaultAvatar.forUser` 同源（userId % 24，id 为 24 的倍数 → 第 1 张）。

用法：
    python data/D004__backfill_default_avatars.py            # 干跑，打印将要回填的行
    python data/D004__backfill_default_avatars.py --apply    # 真正执行
"""

import argparse
import os
import sys

import pymysql

DB_CONFIG = {
    "host": os.environ.get("DB_HOST", "127.0.0.1"),
    "port": int(os.environ.get("DB_PORT", "3306")),
    "database": os.environ.get("DB_NAME", "campuslink"),
    "user": os.environ.get("DB_USER", "campuslink"),
    "password": os.environ.get("DB_PASSWORD", "campuslink"),
    "charset": "utf8mb4",
    "autocommit": False,
}

# 顺序须与 backend/src/main/java/com/campuslink/module/account/domain/model/DefaultAvatar.java 逐项一致
AVATARS = [
    "/avatars/avatar-01-terminal.png",
    "/avatars/avatar-02-keyboard.png",
    "/avatars/avatar-03-gear.png",
    "/avatars/avatar-04-cloud.png",
    "/avatars/avatar-05-cube.png",
    "/avatars/avatar-06-lightbulb.png",
    "/avatars/avatar-07-headphones.png",
    "/avatars/avatar-08-robot.png",
    "/avatars/avatar-09-chip.png",
    "/avatars/avatar-10-database.png",
    "/avatars/avatar-11-network.png",
    "/avatars/avatar-12-mouse.png",
    "/avatars/avatar-13-monitor.png",
    "/avatars/avatar-14-rocket.png",
    "/avatars/avatar-15-coffee.png",
    "/avatars/avatar-16-bolt.png",
    "/avatars/avatar-17-cursor.png",
    "/avatars/avatar-18-wifi.png",
    "/avatars/avatar-19-wrench.png",
    "/avatars/avatar-20-gradcap.png",
    "/avatars/avatar-21-book.png",
    "/avatars/avatar-22-backpack.png",
    "/avatars/avatar-23-pencil.png",
    "/avatars/avatar-24-globe.png",
]


def avatar_for(user_id: int) -> str:
    return AVATARS[user_id % len(AVATARS)]


def main() -> int:
    parser = argparse.ArgumentParser(description="回填存量账号默认头像（CR-162）")
    parser.add_argument("--apply", action="store_true", help="真正写库；缺省只 dry-run")
    args = parser.parse_args()

    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, nickname FROM users WHERE avatar_url IS NULL ORDER BY id")
            rows = cur.fetchall()
            print(f"[信息] 待回填 {len(rows)} 个账号（avatar_url IS NULL）")
            for user_id, nickname in rows[:10]:
                print(f"  id={user_id:<4} {nickname!r:<20} -> {avatar_for(user_id)}")
            if len(rows) > 10:
                print(f"  … 其余 {len(rows) - 10} 行省略")

            if not rows:
                print("[信息] 无需回填")
                return 0
            if not args.apply:
                print("[dry-run] 未写库；确认后加 --apply")
                return 0

            affected = 0
            for user_id, _ in rows:
                cur.execute(
                    "UPDATE users SET avatar_url = %s WHERE id = %s AND avatar_url IS NULL",
                    (avatar_for(user_id), user_id),
                )
                affected += cur.rowcount
            conn.commit()
            cur.execute("SELECT COUNT(*) FROM users WHERE avatar_url IS NULL")
            remaining = cur.fetchone()[0]
            print(f"[完成] 回填 {affected} 行；仍为空 {remaining} 行")
            return 0 if remaining == 0 else 2
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
