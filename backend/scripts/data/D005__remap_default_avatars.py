# -*- coding: utf-8 -*-
"""D005：存量账号默认头像迁移到 8 款矢量徽章（CR-187）。

背景：默认头像自 CR-162 起为 24 张 PNG（先科技图标、CR-165 换为可爱动物），CR-187
改为 8 款「连接结构」矢量徽章（`.svg`）。存量账号的 `users.avatar_url` 仍指向已废弃的
24 张 PNG，本脚本按 `userId % 8` 重算，并**只迁移命中旧默认头像集合的行**——自定义头像
一律不动（当前产品未开放上传，此保护是给未来留的口子）。

约定（同 D004 / _template.py，CR-014）：
  1. 默认 dry-run，只有显式 --apply 才写库；
  2. 凭据只从环境变量读取；
  3. 幂等：迁移后不再命中 OLD_PATHS，重复执行影响 0 行；
  4. DDL 不放这里（Flyway 职责），本脚本只做纯数据 UPDATE；
  5. 映射与后端 `DefaultAvatar.forUser` 同源（userId % 8，id 为 8 的倍数 → 第 1 张）。

用法：
    python data/D005__remap_default_avatars.py            # 干跑，打印将要迁移的行
    python data/D005__remap_default_avatars.py --apply    # 真正执行
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
    "/avatars/avatar-01-link.svg",
    "/avatars/avatar-02-network.svg",
    "/avatars/avatar-03-diamond.svg",
    "/avatars/avatar-04-brackets.svg",
    "/avatars/avatar-05-orbit.svg",
    "/avatars/avatar-06-core.svg",
    "/avatars/avatar-07-wave.svg",
    "/avatars/avatar-08-grid.svg",
]

# 已废弃的默认头像：CR-162 的 24 张科技图标 + CR-165 的 24 张动物图
OLD_PATHS = [
    "/avatars/avatar-01-terminal.png", "/avatars/avatar-02-keyboard.png",
    "/avatars/avatar-03-gear.png", "/avatars/avatar-04-cloud.png",
    "/avatars/avatar-05-cube.png", "/avatars/avatar-06-lightbulb.png",
    "/avatars/avatar-07-headphones.png", "/avatars/avatar-08-robot.png",
    "/avatars/avatar-09-chip.png", "/avatars/avatar-10-database.png",
    "/avatars/avatar-11-network.png", "/avatars/avatar-12-mouse.png",
    "/avatars/avatar-13-monitor.png", "/avatars/avatar-14-rocket.png",
    "/avatars/avatar-15-coffee.png", "/avatars/avatar-16-bolt.png",
    "/avatars/avatar-17-cursor.png", "/avatars/avatar-18-wifi.png",
    "/avatars/avatar-19-wrench.png", "/avatars/avatar-20-gradcap.png",
    "/avatars/avatar-21-book.png", "/avatars/avatar-22-backpack.png",
    "/avatars/avatar-23-pencil.png", "/avatars/avatar-24-globe.png",
    "/avatars/avatar-01-cat.png", "/avatars/avatar-02-frog.png",
    "/avatars/avatar-03-penguin.png", "/avatars/avatar-04-bear-laptop.png",
    "/avatars/avatar-05-shiba.png", "/avatars/avatar-06-rabbit.png",
    "/avatars/avatar-07-fox.png", "/avatars/avatar-08-koala.png",
    "/avatars/avatar-09-pig.png", "/avatars/avatar-10-chick.png",
    "/avatars/avatar-11-owl.png", "/avatars/avatar-12-hedgehog.png",
    "/avatars/avatar-13-squirrel.png", "/avatars/avatar-14-dino.png",
    "/avatars/avatar-15-octopus.png", "/avatars/avatar-16-whale.png",
    "/avatars/avatar-17-seal.png", "/avatars/avatar-18-bee.png",
    "/avatars/avatar-19-turtle.png", "/avatars/avatar-20-snail.png",
    "/avatars/avatar-21-sheep.png", "/avatars/avatar-22-deer.png",
    "/avatars/avatar-23-monkey.png", "/avatars/avatar-24-crab.png",
]


def avatar_for(user_id: int) -> str:
    return AVATARS[user_id % len(AVATARS)]


def main() -> int:
    parser = argparse.ArgumentParser(description="存量默认头像迁移到 8 款矢量徽章（CR-187）")
    parser.add_argument("--apply", action="store_true", help="真正写库；缺省只 dry-run")
    args = parser.parse_args()

    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            placeholders = ",".join(["%s"] * len(OLD_PATHS))
            cur.execute(
                f"SELECT id, nickname, avatar_url FROM users WHERE avatar_url IN ({placeholders}) ORDER BY id",
                OLD_PATHS,
            )
            rows = cur.fetchall()
            print(f"[信息] 待迁移 {len(rows)} 个账号（命中已废弃的默认头像）")
            for user_id, nickname, old in rows[:10]:
                print(f"  id={user_id:<4} {nickname!r:<20} {old} -> {avatar_for(user_id)}")
            if len(rows) > 10:
                print(f"  … 其余 {len(rows) - 10} 行省略")

            if not rows:
                print("[信息] 无需迁移")
                return 0
            if not args.apply:
                print("[dry-run] 未写库；确认后加 --apply")
                return 0

            affected = 0
            for user_id, _, old in rows:
                cur.execute(
                    "UPDATE users SET avatar_url = %s WHERE id = %s AND avatar_url = %s",
                    (avatar_for(user_id), user_id, old),
                )
                affected += cur.rowcount
            conn.commit()
            cur.execute(
                f"SELECT COUNT(*) FROM users WHERE avatar_url IN ({placeholders})",
                OLD_PATHS,
            )
            remaining = cur.fetchone()[0]
            print(f"[完成] 迁移 {affected} 行；仍为旧值 {remaining} 行")
            return 0 if remaining == 0 else 2
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
