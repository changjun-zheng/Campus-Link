# -*- coding: utf-8 -*-
"""D003：历史账号回填入学年级（CR-145 届徽章）。

背景：注册时快照名册 grade 自 CR-145 起才生效，此前注册的账号 `users.grade` 全空。
本脚本按 `student_roster.used_user_id = users.id` 关联回填——归一规则与后端
`CohortYear.normalize` 同源（提取首个 4 位年份数字，提取不出不写）。

约定（同 _template.py，CR-014）：
  1. 默认 dry-run，只有显式 --apply 才写库；
  2. 凭据只从环境变量读取；
  3. 幂等：只 UPDATE `users.grade IS NULL` 的行，重复执行结果不变；
  4. DDL 不放这里（Flyway 职责），本脚本只做纯数据 UPDATE。

用法：
    python data/D003__backfill_grade_from_roster.py            # 干跑，打印将要回填的行
    python data/D003__backfill_grade_from_roster.py --apply    # 真正执行
"""

import argparse
import os
import re
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

FOUR_DIGITS = re.compile(r"\d{4}")


def normalize(grade):
    """与后端 CohortYear.normalize 同源：提取首个 4 位年份，提取不出返回 None"""
    if not grade:
        return None
    m = FOUR_DIGITS.search(grade)
    return m.group() if m else None


def main() -> int:
    parser = argparse.ArgumentParser(description="历史账号回填入学年级（默认 dry-run）")
    parser.add_argument("--apply", action="store_true", help="真正写库；不加则只干跑")
    args = parser.parse_args()

    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            # 候选行：名册已占用（used_user_id 非空）且对应用号 grade 为空
            cur.execute(
                "SELECT u.id, u.nickname, r.grade FROM users u "
                "JOIN student_roster r ON r.used_user_id = u.id "
                "WHERE u.grade IS NULL AND r.grade IS NOT NULL"
            )
            rows = cur.fetchall()
            plan = [(normalize(g), uid, nick, g) for uid, nick, g in rows]
            actionable = [p for p in plan if p[0] is not None]
            skipped = len(plan) - len(actionable)
            print(f"[检查] 名册已占用且账号 grade 为空：{len(plan)} 行；可归一回填 {len(actionable)} 行、名册无 4 位年份跳过 {skipped} 行")
            for year, uid, nick, raw in actionable[:10]:
                print(f"  [样例] users#{uid} {nick}: 「{raw}」→ {year}")
            if len(actionable) > 10:
                print(f"  … 其余 {len(actionable) - 10} 行类推")

            affected = 0
            for year, uid, _, _ in actionable:
                affected += cur.execute(
                    "UPDATE users SET grade = %s WHERE id = %s AND grade IS NULL", (year, uid)
                )
            print(f"[执行] UPDATE 影响 {affected} 行（幂等：仅 grade IS NULL 才写）")

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
