# -*- coding: utf-8 -*-
"""CR-188 · 注册 1 万容量种子（隔离 schema campuslink_cap，默认 dry-run）。

与 D001 同一条纪律：**脚本不复刻加密口径**——名册走 admin CSV 导入、账号走真实
`verify-student → captcha → register` API；PyMySQL 只做只读复核。10k 账号的邮箱 HMAC /
AES-GCM 密文、名册占用、审计与领域事件全部由生产代码生成。

前置（三条缺一即失败）：
  - 8097 测试实例已启动，且 `APP_ROSTER_BYPASS=false`（注册查库内 10k 名册，不查内置 4 条）；
  - `VERIFY_IP_HOURLY_LIMIT>=10000`（默认 10，会撞 2103）；
  - dev admin 已由 `APP_ROSTER_BYPASS=true` 的首轮启动种子化（admin@campuslink.local）。

用法：
  python backend/scripts/perf/seed_accounts_capacity.py                 # 干跑
  python backend/scripts/perf/seed_accounts_capacity.py --apply         # 名册 + 注册 + 捕获 1000 token
  python backend/scripts/perf/seed_accounts_capacity.py --apply --stage roster

只允许本机：API_BASE 必须解析到 127.0.0.1 / localhost；DB_HOST 必须是 127.0.0.1。
"""

import argparse
import http.client
import json
import os
import sys
import time
from urllib.parse import urlsplit

import pymysql

DB_CONFIG = {
    "host": os.environ.get("DB_HOST", "127.0.0.1"),
    "port": int(os.environ.get("DB_PORT", "3306")),
    # 默认指向隔离 schema：避免误连开发库；必须显式 DB_NAME=campuslink_cap 或本默认值
    "database": os.environ.get("DB_NAME", "campuslink_cap"),
    "user": os.environ.get("DB_USER", "campuslink"),
    "password": os.environ.get("DB_PASSWORD", "campuslink"),
    "charset": "utf8mb4",
    "autocommit": False,
}
API_BASE = os.environ.get("API_BASE", "http://127.0.0.1:8097").rstrip("/")
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "admin@campuslink.local")
CAPTCHA_CODE = os.environ.get("CAPTCHA_CODE", "123456")
COUNT = int(os.environ.get("CAP_COUNT", "10000"))
TOKEN_OUT = os.environ.get("CAP_TOKEN_OUT", "backend/target/cap-tokens.txt")
TOKEN_LIMIT = int(os.environ.get("CAP_TOKEN_LIMIT", "1000"))
BATCH = "cap-10k"
GRADE = "容量2024级"
DEPARTMENT = "容量测试-计算机科学与技术"

SURNAMES = "赵钱孙李周吴郑王冯陈蒋沈韩杨朱秦许何吕张孔曹严华金魏陶姜"
GIVEN = (
    "子涵", "雨桐", "浩然", "思远", "嘉懿", "若曦", "沐辰", "书瑶", "梓萱", "宇轩",
    "语汐", "泽楷", "静姝", "亦辰", "婉清", "博文", "念安", "景行", "清和", "知夏",
)
TAILS = "子安宁宇轩然涵辰曦瑶萱睿哲嘉思远知夏"


def build_students(count):
    """确定性生成 count 个 3~4 字中文名 + 9 位学号 + 不可投递邮箱；重跑结果一致。"""
    students = []
    seen = set()
    for surname in SURNAMES:
        for given in GIVEN:
            for tail in TAILS:
                name = surname + given + tail
                if name in seen:
                    continue
                seen.add(name)
                seq = len(students) + 1
                students.append({
                    "studentId": f"9999{seq:05d}",
                    "name": name,
                    "email": f"cap-stu-{seq:05d}@cap.campuslink.local",
                    "nickname": f"容量测试学生{seq:05d}",
                })
                if len(students) >= count:
                    return students
    raise SystemExit(f"[失败] 姓名组合不足 {count} 个（当前 {len(students)}）")


def call(path, *, method="POST", json_body=None, text_body=None, token=None, retries=2):
    data = None
    headers = {}
    if json_body is not None:
        data = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=UTF-8"
    elif text_body is not None:
        data = text_body.encode("utf-8")
        # 显式 charset：StringHttpMessageConverter 未声明时按 ISO-8859-1 解码，中文姓名会乱码
        headers["Content-Type"] = "text/plain; charset=UTF-8"
    if token:
        headers["Authorization"] = "Bearer " + token

    parts = urlsplit(API_BASE + path)
    if parts.hostname not in ("127.0.0.1", "localhost"):
        raise SystemExit("refusing non-local host: %s" % parts.hostname)

    last_error = None
    for attempt in range(retries + 1):
        conn = (http.client.HTTPSConnection if parts.scheme == "https" else http.client.HTTPConnection)(
            parts.hostname, parts.port or (443 if parts.scheme == "https" else 80), timeout=30)
        try:
            conn.request(method, parts.path + ("?" + parts.query if parts.query else ""), body=data, headers=headers)
            res = conn.getresponse()
            raw = res.read().decode("utf-8", errors="replace")
            try:
                return res.status, json.loads(raw) if raw else {}
            except json.JSONDecodeError:
                return res.status, {"_raw": raw[:300]}
        except OSError as e:
            last_error = e
            if attempt < retries:
                time.sleep(0.5 * (attempt + 1))
                continue
            raise SystemExit(f"[失败] {path} 网络异常：{last_error}")
        finally:
            conn.close()
    raise SystemExit(f"[失败] {path} 未知异常")


def admin_token():
    _, cap = call("/api/v1/auth/captcha", json_body={"target": ADMIN_EMAIL})
    if cap.get("code") not in (0, 2002, 2003):
        raise SystemExit(f"[失败] admin 验证码发送：{cap.get('code')} {cap.get('message')}")
    st, login = call("/api/v1/auth/login", json_body={"account": ADMIN_EMAIL, "code": CAPTCHA_CODE})
    if st != 200 or login.get("code") != 0:
        raise SystemExit(
            f"[失败] admin 登录：{st} / {login.get('code')} {login.get('message')}；"
            f"先以 APP_ROSTER_BYPASS=true 启动一次 8097 生成 dev admin")
    return login["data"]["token"]


def stage_roster(students, apply):
    chunks = [students[i:i + 2000] for i in range(0, len(students), 2000)]
    print(f"[名册] 分 {len(chunks)} 批向 /api/v1/admin/roster/import?batch={BATCH} 导入 {len(students)} 行")
    if not apply:
        print("[名册·干跑] 未发送请求")
        return True
    token = admin_token()
    inserted = skipped = failed = 0
    for idx, chunk in enumerate(chunks, 1):
        lines = ["学号,姓名,年级,专业"] + [
            f"{s['studentId']},{s['name']},{GRADE},{DEPARTMENT}" for s in chunk]
        csv = "\n".join(lines) + "\n"
        st, res = call(f"/api/v1/admin/roster/import?batch={BATCH}", text_body=csv, token=token)
        if st != 200 or res.get("code") != 0:
            raise SystemExit(f"[失败] 名册第 {idx} 批：{st} / {res.get('code')} {res.get('message')}")
        data = res["data"]
        inserted += data["inserted"]; skipped += data["skipped"]; failed += data["failed"]
        print(f"[名册] 批 {idx}/{len(chunks)} inserted={data['inserted']} skipped={data['skipped']} failed={data['failed']}")
    print(f"[名册] 合计 inserted={inserted} skipped={skipped} failed={failed}")
    return failed == 0


def login_token(email):
    _, cap = call("/api/v1/auth/captcha", json_body={"target": email})
    if cap.get("code") == 2002:
        time.sleep(61)  # 重发间隔；仅 resume 时偶发
        _, cap = call("/api/v1/auth/captcha", json_body={"target": email})
    if cap.get("code") not in (0, 2003):
        return None
    _, login = call("/api/v1/auth/login", json_body={"account": email, "code": CAPTCHA_CODE})
    return login.get("data", {}).get("token") if login.get("code") == 0 else None


def stage_register(students, apply):
    print(f"[注册] 对 {len(students)} 个身份执行 verify-student → captcha → register")
    if not apply:
        print("[注册·干跑] 未发送请求；真跑前置：APP_ROSTER_BYPASS=false + VERIFY_IP_HOURLY_LIMIT>=10000")
        return True

    tokens = []
    registered = skipped = failed = 0
    failures = []
    for idx, s in enumerate(students, 1):
        st, ver = call("/api/v1/auth/verify-student",
                       json_body={"studentId": s["studentId"], "name": s["name"]})
        code = ver.get("code")
        if code == 2103:
            raise SystemExit(f"[中断] 第 {idx} 个撞 verify 限流 2103；抬高 VERIFY_IP_HOURLY_LIMIT 后重跑（脚本幂等可续跑）")
        if code == 2101:
            skipped += 1
            if len(tokens) < TOKEN_LIMIT:
                token = login_token(s["email"])
                if token:
                    tokens.append(token)
            continue
        if code != 0:
            failed += 1
            failures.append((s, f"verify {st}/{code} {ver.get('message')}"))
            continue

        _, cap = call("/api/v1/auth/captcha", json_body={"target": s["email"]})
        if cap.get("code") not in (0, 2002, 2003):
            failed += 1
            failures.append((s, f"captcha {cap.get('code')} {cap.get('message')}"))
            continue

        st, reg = call("/api/v1/auth/register", json_body={
            "ticket": ver["data"]["ticket"],
            "contact": s["email"],
            "code": CAPTCHA_CODE,
            "nickname": s["nickname"],
        })
        if reg.get("code") == 0:
            registered += 1
            if len(tokens) < TOKEN_LIMIT:
                tokens.append(reg["data"]["token"])
        elif reg.get("code") in (2004, 2101):
            skipped += 1
            if len(tokens) < TOKEN_LIMIT:
                token = login_token(s["email"])
                if token:
                    tokens.append(token)
        else:
            failed += 1
            failures.append((s, f"register {st}/{reg.get('code')} {reg.get('message')}"))

        if idx % 100 == 0:
            print(f"[注册] {idx}/{len(students)} 新注册={registered} 跳过={skipped} 失败={failed} token={len(tokens)}")

    print(f"[注册] 完成：新注册={registered} 跳过={skipped} 失败={failed} token={len(tokens)}")
    for s, why in failures[:10]:
        print(f"[注册·失败] {s['studentId']} {s['name']} {s['email']} → {why}")
    if tokens:
        parent = os.path.dirname(os.path.abspath(TOKEN_OUT))
        os.makedirs(parent, exist_ok=True)
        with open(TOKEN_OUT, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(tokens) + "\n")
        print(f"[token] {len(tokens)} 个写入 {TOKEN_OUT}（不入仓）")
    return failed == 0 and len(tokens) >= min(TOKEN_LIMIT, len(students))


def db_report(label):
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*), COALESCE(SUM(used_user_id IS NOT NULL), 0) "
                "FROM student_roster WHERE source_batch = %s", (BATCH,))
            roster_total, roster_used = cur.fetchone()
            cur.execute("SELECT COUNT(*) FROM users WHERE nickname LIKE %s", ("容量测试学生%",))
            (user_total,) = cur.fetchone()
            cur.execute("SELECT COUNT(*) FROM users")
            (all_users,) = cur.fetchone()
        print(f"[复核·{label}] student_roster(batch={BATCH})={roster_total} used={roster_used}；"
              f"容量昵称账号={user_total}；users 总数={all_users}")
        return roster_total, roster_used, user_total
    finally:
        conn.rollback()
        conn.close()


def main():
    parser = argparse.ArgumentParser(description="CR-188 · 注册 1 万容量种子（默认 dry-run）")
    parser.add_argument("--apply", action="store_true", help="真正发请求；不加则干跑")
    parser.add_argument("--stage", choices=("roster", "register", "all"), default="all")
    parser.add_argument("--count", type=int, default=COUNT)
    args = parser.parse_args()

    students = build_students(args.count)
    print(f"[名单] 确定性生成 {len(students)} 人：学号 {students[0]['studentId']}~{students[-1]['studentId']}，"
          f"邮箱 {students[0]['email']}~{students[-1]['email']}，批次 {BATCH}")
    print(f"[后端] {API_BASE}；[DB] {DB_CONFIG['database']}")
    if not args.apply:
        try:
            db_report("干跑前")
        except Exception as e:
            print(f"[复核·跳过] 隔离库不可读（预期：尚未建库/授权）：{e}")
        print("[干跑] 未发送任何请求；确认后加 --apply")
        return 0

    ok_roster = stage_roster(students, args.stage in ("roster", "all"))
    ok_register = True
    if args.stage in ("register", "all"):
        ok_register = stage_register(students, True)
    try:
        roster_total, roster_used, user_total = db_report("执行后")
    except Exception as e:
        print(f"[复核·失败] {e}")
        return 1
    expected = len(students)
    if roster_total != expected or roster_used != expected or user_total != expected:
        print(f"[结论] ⚠️ 期望名册/占用/账号 = {expected}/{expected}/{expected}，实得 {roster_total}/{roster_used}/{user_total}")
        return 1
    print(f"[结论] ✅ 名册 {roster_total}、占用 {roster_used}、账号 {user_total}；注册失败={'无' if ok_register else '见上'}")
    return 0 if ok_roster and ok_register else 1


if __name__ == "__main__":
    sys.exit(main())
