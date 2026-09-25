# -*- coding: utf-8 -*-
"""D001 · 学籍名册模拟数据（45 名合成学生）+ 账号注册（CR-054）。

**与 `_template.py` 的一处结构性差异，显式声明**：本脚本对数据库**只读不写**。
名册与账号的写入全部经后端既有接口完成——名册走 `POST /api/v1/admin/roster/import`，
账号走 `/api/v1/auth/verify-student → captcha → register`。理由：加密口径（学号 HMAC
哈希、邮箱 / 学号 AES-GCM 密文）与账号不变量（名册占用、领域事件、审计日志）只有生产
代码一份实现，脚本复刻即造出**第二事实源**，并把 `APP_HASH_KEY` / `APP_CRYPT_KEY` 的
读取面从后端扩到脚本。PyMySQL 在这里只承担「前置检查 + 落库复核」。

约定（详见 backend/scripts/README.md，CR-014）：
  1. 默认 dry-run：不加 `--apply` 只打印将发生的调用与当前库内计数，不发任何写请求；
  2. 凭据只从环境变量读取，严禁硬编码；
  3. 幂等：名册靠服务端 `saveIfAbsent`（重跑 inserted=0 / skipped=45），账号靠
     `2101`（学号已占用）与 `2004`（邮箱已注册）判定为"已存在"跳过，再以 DB 计数复核；
  4. DDL 不放这里（那是 Flyway 的职责）。

前置条件（三条缺一即失败，脚本会指出是哪一条）：
  - 后端已启动（默认 http://localhost:8088）；
  - 注册阶段必须 `APP_ROSTER_BYPASS=false`——否则学籍核验查的是内置测试名册（4 条）而非库；
  - 注册阶段必须 `VERIFY_IP_HOURLY_LIMIT>=45`——默认 10，第 11 次核验即 `2103`；
  - admin 账号存在（`bypass=true` 启动时由 `DevAdminAccountSeeder` 种子，生产不存在）。

⚠️ 本脚本只可用于**本机开发库**：`CAPTCHA_CODE` 依赖 dev 固定验证码，任何共享 / 预发 /
生产环境该配置必须留空（留空后本脚本无法运行，这是预期行为，不是缺陷）。

用法：
    .venv/Scripts/python data/D001__seed_dev_roster_45.py                       # 干跑
    .venv/Scripts/python data/D001__seed_dev_roster_45.py --apply               # 名册 + 注册 + 登录复核
    .venv/Scripts/python data/D001__seed_dev_roster_45.py --apply --stage roster
"""

import argparse
import http.client
import json
import os
import sys
import time
from urllib.parse import urlsplit

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

API_BASE = os.environ.get("API_BASE", "http://localhost:8088").rstrip("/")
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "admin@campuslink.local")
# dev 固定验证码（campuslink.captcha.fixed-code）；生产留空 ⇒ 本脚本不可用，属预期
CAPTCHA_CODE = os.environ.get("CAPTCHA_CODE", "123456")
LOGIN_CHECK_COUNT = int(os.environ.get("LOGIN_CHECK", "3"))
# 后端 campuslink.captcha.resend-interval-seconds=60：注册已消耗掉验证码，
# 60 秒内重发会被 2002 拒绝，登录复核须等过一个间隔再重发
CAPTCHA_RESEND_WAIT = int(os.environ.get("CAPTCHA_RESEND_WAIT", "61"))

# ---- 合成身份口径：真实名册不可能出现的保留段 + 一眼可辨的批次 / 昵称标记 ----
BATCH = "dev-sim-45"
COUNT = 45
ID_PREFIX = "88880"
NICKNAME_PREFIX = "模拟学生"
GRADE = "模拟2024级"
DEPARTMENT = "模拟-计算机科学与技术"

SURNAME_POOL = "赵钱孙李周吴郑王冯陈蒋沈韩杨朱秦许何吕张孔曹严华金魏陶姜"
GIVEN_POOL = (
    "子涵", "雨桐", "浩然", "思远", "嘉懿", "若曦", "沐辰", "书瑶", "梓萱", "宇轩",
    "语汐", "泽楷", "静姝", "亦辰", "婉清", "博文", "念安", "景行", "清和", "知夏",
)


def build_students():
    """确定性生成 45 个合成身份：同一份输入永远得到同一份名单（幂等的前提）。"""
    students = []
    seen = set()
    for surname in SURNAME_POOL:
        for given in GIVEN_POOL:
            name = surname + given
            if name in seen:
                continue
            seen.add(name)
            seq = len(students) + 1
            students.append({
                "studentId": f"{ID_PREFIX}{seq:04d}",
                "name": name,
                "email": f"dev-stu-{seq:02d}@dev.campuslink.local",
                "nickname": f"{NICKNAME_PREFIX}{seq:02d}",
            })
            if len(students) >= COUNT:
                return students
    raise SystemExit(f"[失败] 姓名组合不足 {COUNT} 个，请扩充 GIVEN_POOL")


def call(path, *, method="POST", json_body=None, text_body=None, token=None):
    """返回 (http_status, payload)；4xx / 5xx 也解析出 ApiError 外壳，便于按业务码分支。"""
    data = None
    headers = {}
    if json_body is not None:
        data = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=UTF-8"
    elif text_body is not None:
        data = text_body.encode("utf-8")
        # charset 必须显式声明：`@RequestBody String` 走 StringHttpMessageConverter，
        # 未声明时按 ISO-8859-1 解码，中文姓名会整批变乱码
        headers["Content-Type"] = "text/plain; charset=UTF-8"
    if token:
        headers["Authorization"] = "Bearer " + token

    # 本工具只允许打本机开发实例：http.client + 域名白名单显式收口（替代 urlopen，安全扫描器口径）
    parts = urlsplit(API_BASE + path)
    if parts.hostname not in ("127.0.0.1", "localhost"):
        raise SystemExit("refusing non-local host: %s" % parts.hostname)
    conn = (http.client.HTTPSConnection if parts.scheme == "https" else http.client.HTTPConnection)(
        parts.hostname, parts.port or (443 if parts.scheme == "https" else 80), timeout=30)
    try:
        conn.request(method, parts.path + ("?" + parts.query if parts.query else ""), body=data, headers=headers)
        resp = conn.getresponse()
        raw = resp.read()
        try:
            return resp.status, json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            return resp.status, {"code": None, "message": raw[:200].decode("utf-8", errors="replace")}
    except OSError as e:
        raise SystemExit(f"[失败] 连不上后端 {API_BASE}：{e}（请先启动后端）")
    finally:
        conn.close()


def db_report(label):
    """只读复核：名册批次行数 / 已占用数 / 带标记昵称的账号数 / 抽样 3 行确认中文未乱码。"""
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*), COALESCE(SUM(used_user_id IS NOT NULL), 0) "
                "FROM student_roster WHERE source_batch = %s", (BATCH,))
            roster_total, roster_used = cur.fetchone()
            cur.execute(
                "SELECT COUNT(*) FROM users WHERE nickname LIKE %s", (NICKNAME_PREFIX + "%",))
            (user_total,) = cur.fetchone()
            cur.execute(
                "SELECT student_id_hash, name, grade, department FROM student_roster "
                "WHERE source_batch = %s ORDER BY id LIMIT 3", (BATCH,))
            samples = cur.fetchall()
        print(f"[复核·{label}] student_roster(batch={BATCH}) = {roster_total} 行，"
              f"其中已占用 used_user_id 非空 = {roster_used} 行")
        print(f"[复核·{label}] users(nickname LIKE '{NICKNAME_PREFIX}%') = {user_total} 行")
        for h, name, grade, dept in samples:
            print(f"[复核·{label}] 抽样：hash={h[:12]}… name={name} grade={grade} dept={dept}")
        return roster_total, roster_used, user_total
    finally:
        conn.rollback()  # 全程只读，显式回滚以符合"事务包裹"约定
        conn.close()


def stage_roster(students, apply):
    csv_lines = ["学号,姓名,年级,专业"]
    csv_lines += [f"{s['studentId']},{s['name']},{GRADE},{DEPARTMENT}" for s in students]
    csv = "\n".join(csv_lines) + "\n"

    print(f"[名册] 将向 POST /api/v1/admin/roster/import?batch={BATCH} 提交 {len(students)} 行 CSV")
    for line in csv_lines[:3]:
        print(f"[名册]   {line}")
    print(f"[名册]   …（共 {len(csv_lines)} 行，含表头）")

    if not apply:
        print("[名册·干跑] 未发送任何请求。确认无误后加 --apply")
        return True

    # admin 登录：邮箱 + 验证码（与普通用户同一口径）
    _, cap = call("/api/v1/auth/captcha", json_body={"target": ADMIN_EMAIL})
    if cap.get("code") not in (0, 2002):
        raise SystemExit(f"[失败] admin 验证码发送未通过：{cap.get('code')} {cap.get('message')}")
    st, login = call("/api/v1/auth/login", json_body={"email": ADMIN_EMAIL, "code": CAPTCHA_CODE})
    if st != 200 or login.get("code") != 0:
        raise SystemExit(
            f"[失败] admin 登录失败（{st} / {login.get('code')} {login.get('message')}）。"
            f"排查：① 后端是否以 APP_ROSTER_BYPASS=true 启动过（admin 由 DevAdminAccountSeeder 种子，"
            f"bypass=false 时不创建）；② CAPTCHA_CODE 是否等于 campuslink.captcha.fixed-code")
    token = login["data"]["token"]

    st, res = call(f"/api/v1/admin/roster/import?batch={BATCH}", text_body=csv, token=token)
    if st != 200 or res.get("code") != 0:
        raise SystemExit(f"[失败] 名册导入未通过：{st} / {res.get('code')} {res.get('message')}")
    data = res["data"]
    print(f"[名册] 导入结果 inserted={data['inserted']} skipped={data['skipped']} failed={data['failed']}")
    if data["failed"] > 0:
        raise SystemExit(f"[失败] 有 {data['failed']} 行被判为非法（学号须 9 位数字），已中止")
    return True


def stage_register(students, apply):
    print(f"[注册] 将对 {len(students)} 个身份依次执行 verify-student → captcha → register")
    if not apply:
        print("[注册·干跑] 未发送任何请求。"
              "真跑前置：APP_ROSTER_BYPASS=false + VERIFY_IP_HOURLY_LIMIT>=45")
        return True

    registered, skipped, failed = [], [], []
    for s in students:
        st, ver = call("/api/v1/auth/verify-student",
                       json_body={"studentId": s["studentId"], "name": s["name"]})
        code = ver.get("code")
        if code == 2101:
            skipped.append((s, "学籍核验未通过（通常=该学号已被注册占用）"))
            continue
        if code == 2103:
            print(f"[注册] 撞限流 2103（第 {len(registered) + len(skipped) + len(failed) + 1} 个）。"
                  f"请以 VERIFY_IP_HOURLY_LIMIT>=45 重启后端后重跑——本脚本幂等，可续跑")
            break
        if code != 0:
            failed.append((s, f"verify-student {st} / {code} {ver.get('message')}"))
            continue

        _, cap = call("/api/v1/auth/captcha", json_body={"target": s["email"]})
        if cap.get("code") not in (0, 2002, 2003):
            failed.append((s, f"captcha {cap.get('code')} {cap.get('message')}"))
            continue

        st, reg = call("/api/v1/auth/register", json_body={
            "ticket": ver["data"]["ticket"],
            "email": s["email"],
            "code": CAPTCHA_CODE,
            "nickname": s["nickname"],
        })
        if reg.get("code") == 0:
            registered.append(s)
        elif reg.get("code") in (2004, 2101):
            skipped.append((s, f"已存在：{reg.get('message')}"))
        else:
            failed.append((s, f"register {st} / {reg.get('code')} {reg.get('message')}"))

    print(f"[注册] 新注册 {len(registered)} / 跳过（已存在）{len(skipped)} / 失败 {len(failed)}")
    for s, why in failed:
        print(f"[注册·失败] {s['studentId']} {s['name']} {s['email']} → {why}")
    return not failed


def login_check(students):
    """用清单里前 N 个账号真登一次，证明文档写的登录方式确实可用。"""
    ok = 0
    for s in students[:LOGIN_CHECK_COUNT]:
        _, cap = call("/api/v1/auth/captcha", json_body={"target": s["email"]})
        if cap.get("code") == 2002:
            print(f"[登录复核] {s['email']} 仍在重发间隔内，等待 {CAPTCHA_RESEND_WAIT}s 后重发")
            time.sleep(CAPTCHA_RESEND_WAIT)
            _, cap = call("/api/v1/auth/captcha", json_body={"target": s["email"]})
        if cap.get("code") != 0:
            print(f"[登录复核·跳过] {s['email']}：验证码发送 {cap.get('code')} {cap.get('message')}")
            continue
        st, lg = call("/api/v1/auth/login", json_body={"email": s["email"], "code": CAPTCHA_CODE})
        if st != 200 or lg.get("code") != 0:
            print(f"[登录复核·失败] {s['email']}：{st} / {lg.get('code')} {lg.get('message')}")
            continue
        st2, me = call("/api/v1/users/me", method="GET", token=lg["data"]["token"])
        nickname = (me.get("data") or {}).get("nickname")
        if st2 == 200 and me.get("code") == 0 and nickname == s["nickname"]:
            ok += 1
            print(f"[登录复核·通过] {s['email']} → /users/me 200，nickname={nickname}")
        else:
            print(f"[登录复核·失败] {s['email']}：/users/me {st2} / {me.get('code')} nickname={nickname}")
    print(f"[登录复核] {ok}/{min(LOGIN_CHECK_COUNT, len(students))} 通过")
    return ok > 0


def main() -> int:
    parser = argparse.ArgumentParser(description="D001 · 学籍名册模拟数据 45 名（默认 dry-run）")
    parser.add_argument("--apply", action="store_true", help="真正发请求；不加则只干跑")
    parser.add_argument("--stage", choices=("roster", "register", "all"), default="all",
                        help="roster=只导名册；register=只注册账号；all=两阶段（默认）")
    args = parser.parse_args()

    students = build_students()
    print(f"[名单] 确定性生成 {len(students)} 个合成身份："
          f"学号 {students[0]['studentId']}~{students[-1]['studentId']}，"
          f"邮箱 {students[0]['email']}~{students[-1]['email']}，批次 {BATCH}")
    print(f"[后端] {API_BASE}")

    if not args.apply:
        db_report("干跑前")
    stage_roster(students, args.apply and args.stage in ("roster", "all"))
    if args.stage in ("register", "all"):
        stage_register(students, args.apply)
        if args.apply:
            login_check(students)

    roster_total, roster_used, user_total = db_report("执行后")
    if args.apply:
        expect = COUNT
        if roster_total != expect or roster_used != expect or user_total != expect:
            print(f"[结论] ⚠️ 与预期不符：期望名册 {expect} 行 / 已占用 {expect} / 账号 {expect}，"
                  f"实得 {roster_total} / {roster_used} / {user_total}")
            return 1
        print(f"[结论] ✅ 名册 {roster_total} 行、已占用 {roster_used} 行、账号 {user_total} 个，全部符合预期")
    return 0


if __name__ == "__main__":
    sys.exit(main())
