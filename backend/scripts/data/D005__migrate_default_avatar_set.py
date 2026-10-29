# -*- coding: utf-8 -*-
"""D005：存量默认头像路径迁移到新素材集（CR-165 补漏）。

背景：CR-165 把 24 张默认头像从科技图标换成动物头像，`DefaultAvatar` / `D004` 已同步；
但**已回填过的 54 个账号**的 `users.avatar_url` 仍指向旧文件名。旧文件已删除，
dev server 对不存在的路径会走 SPA 兜底（`200 text/html`），头像因此实际显示为破图。
本脚本按「编号相同」把旧路径迁到新集（avatar-09-chip → avatar-09-pig，…）。

约定（同 D004 / _template.py）：
  1. 默认 dry-run，只有 --apply 才写库；
  2. 凭据只从环境变量读取；
  3. 幂等：只更新仍指向旧集的行，重复执行结果不变；
  4. 只做数据 UPDATE，不动表结构；
  5. 新清单与 `DefaultAvatar.java` 逐项一致。

用法：
    python data/D005__migrate_default_avatar_set.py            # 干跑
    python data/D005__migrate_default_avatar_set.py --apply    # 执行
"""

import argparse
import os
import re
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

# 与 backend/.../account/domain/model/DefaultAvatar.java 的新清单逐项一致
NEW_AVATARS = [
    "/avatars/avatar-01-cat.png",
    "/avatars/avatar-02-frog.png",
    "/avatars/avatar-03-penguin.png",
    "/avatars/avatar-04-bear-laptop.png",
    "/avatars/avatar-05-shiba.png",
    "/avatars/avatar-06-rabbit.png",
    "/avatars/avatar-07-fox.png",
    "/avatars/avatar-08-koala.png",
    "/avatars/avatar-09-pig.png",
    "/avatars/avatar-10-chick.png",
    "/avatars/avatar-11-owl.png",
    "/avatars/avatar-12-hedgehog.png",
    "/avatars/avatar-13-squirrel.png",
    "/avatars/avatar-14-dino.png",
    "/avatars/avatar-15-octopus.png",
    "/avatars/avatar-16-whale.png",
    "/avatars/avatar-17-seal.png",
    "/avatars/avatar-18-bee.png",
    "/avatars/avatar-19-turtle.png",
    "/avatars/avatar-20-snail.png",
    "/avatars/avatar-21-sheep.png",
    "/avatars/avatar-22-deer.png",
    "/avatars/avatar-23-monkey.png",
    "/avatars/avatar-24-crab.png",
]

INDEX = re.compile(r"^/avatars/avatar-(\d{2})-[^/]+\.png$")


def main() -> int:
    parser = argparse.ArgumentParser(description="存量默认头像路径迁移到新素材集（CR-165 补漏）")
    parser.add_argument("--apply", action="store_true", help="真正写库；缺省只 dry-run")
    args = parser.parse_args()

    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, avatar_url FROM users WHERE avatar_url LIKE '/avatars/avatar-%' ORDER BY id")
            rows = cur.fetchall()
            plan = []
            for user_id, old in rows:
                m = INDEX.match(old)
                if not m:
                    continue
                target = NEW_AVATARS[int(m.group(1)) - 1]
                if old != target:
                    plan.append((user_id, old, target))

            print(f"[信息] 待迁移 {len(plan)} 行 / 头像总数 {len(rows)}")
            for user_id, old, target in plan[:10]:
                print(f"  id={user_id:<4} {old} -> {target}")
            if len(plan) > 10:
                print(f"  … 其余 {len(plan) - 10} 行省略")

            if not plan:
                print("[信息] 无需迁移")
                return 0
            if not args.apply:
                print("[dry-run] 未写库；确认后加 --apply")
                return 0

            affected = 0
            for user_id, old, target in plan:
                cur.execute(
                    "UPDATE users SET avatar_url = %s WHERE id = %s AND avatar_url = %s",
                    (target, user_id, old),
                )
                affected += cur.rowcount
            conn.commit()

            cur.execute("SELECT COUNT(*) FROM users WHERE avatar_url LIKE '/avatars/avatar-%'")
            total = cur.fetchone()[0]
            cur.execute(
                "SELECT COUNT(*) FROM users WHERE avatar_url IS NOT NULL AND avatar_url NOT IN (%s)"
                % ",".join(["%s"] * len(NEW_AVATARS)),
                NEW_AVATARS,
            )
            legacy = cur.fetchone()[0]
            print(f"[完成] 迁移 {affected} 行；头像总数 {total}，仍指向旧集 {legacy} 行")
            return 0 if legacy == 0 else 2
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
