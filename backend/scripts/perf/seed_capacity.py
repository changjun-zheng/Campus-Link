#!/usr/bin/env python3
"""容量验证种子数据（CR-179）——默认 dry-run，--apply 写入，--cleanup 清理。

用法：
  python backend/scripts/perf/seed_capacity.py                 # 只看计划与当前量级
  python backend/scripts/perf/seed_capacity.py --apply         # 清掉旧 CAP 数据后写入 10 万帖 + 1000 楼
  python backend/scripts/perf/seed_capacity.py --cleanup       # 删除全部 [CAP] 前缀数据

标记约定：帖子标题以「[CAP] 」开头；清理按此前缀删除帖子及其回复 / 标签关联。
⚠️ 只对本机开发库使用；生产库禁止执行。
"""
import argparse
import os
import sys
from datetime import datetime, timedelta

import pymysql

DB = dict(
    host=os.getenv('DB_HOST', '127.0.0.1'),
    port=int(os.getenv('DB_PORT', '3306')),
    user=os.getenv('DB_USER', 'campuslink'),
    password=os.getenv('DB_PASSWORD', 'campuslink'),
    database=os.getenv('DB_NAME', 'campuslink'),
    charset='utf8mb4',
    autocommit=False,
)
PREFIX = '[CAP] '
BATCH = 5000


def connect():
    return pymysql.connect(**DB)


def counts(cur):
    cur.execute('SELECT COUNT(*) FROM posts')
    posts = cur.fetchone()[0]
    cur.execute('SELECT COUNT(*) FROM replies')
    replies = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM posts WHERE title LIKE %s", (PREFIX + '%',))
    cap_posts = cur.fetchone()[0]
    return posts, replies, cap_posts


def cleanup(conn, cur):
    cur.execute('SELECT id FROM posts WHERE title LIKE %s', (PREFIX + '%',))
    ids = [r[0] for r in cur.fetchall()]
    if not ids:
        print('没有 CAP 数据需要清理')
        return 0
    placeholders = ','.join(['%s'] * len(ids))
    cur.execute(f'DELETE FROM replies WHERE post_id IN ({placeholders})', ids)
    replies_deleted = cur.rowcount
    cur.execute(f'DELETE FROM post_tags WHERE post_id IN ({placeholders})', ids)
    tags_deleted = cur.rowcount
    cur.execute(f'DELETE FROM posts WHERE id IN ({placeholders})', ids)
    posts_deleted = cur.rowcount
    conn.commit()
    print(f'清理完成：posts {posts_deleted} / replies {replies_deleted} / post_tags {tags_deleted}')
    return posts_deleted


def seed(conn, cur, post_count, reply_count):
    cur.execute('SELECT id FROM boards WHERE code = %s', ('qna',))
    board_id = cur.fetchone()[0]
    cur.execute('SELECT id FROM users WHERE role = %s ORDER BY id LIMIT 1', ('SUPERADMIN',))
    author_id = cur.fetchone()[0]
    base = datetime.now().replace(microsecond=0) - timedelta(days=28)
    print(f'插入 {post_count} 帖（board={board_id} author={author_id}）...')
    inserted = 0
    while inserted < post_count:
        n = min(BATCH, post_count - inserted)
        rows = []
        for j in range(n):
            i = inserted + j
            title = f'{PREFIX}容量验证帖 {i + 1:06d}'
            md = f'容量验证种子内容 #{i + 1}：Java Redis 分页搜索压测。'
            html = f'<p>容量验证种子内容 #{i + 1}：Java Redis 分页搜索压测。</p>'
            created = base + timedelta(seconds=i * 20)
            rows.append((board_id, author_id, 'DISCUSSION', title, md, html, 'PUBLISHED', 0, 0, created, created))
        cur.executemany(
            '''INSERT INTO posts
               (board_id, author_id, type, title, content_md, content_html, status, is_deleted, hot_score, created_at, updated_at)
               VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)''',
            rows,
        )
        conn.commit()
        inserted += n
        print(f'  已写 {inserted}/{post_count}')

    now = datetime.now().replace(microsecond=0)
    cur.execute(
        '''INSERT INTO posts
           (board_id, author_id, type, title, content_md, content_html, status, is_deleted, reply_count, created_at, updated_at)
           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)''',
        (board_id, author_id, 'QUESTION', f'{PREFIX}楼层容量帖', '单帖 1000 楼分页压测。', '<p>单帖 1000 楼分页压测。</p>',
         'PUBLISHED', 0, reply_count, now, now),
    )
    floor_post_id = cur.lastrowid
    reply_rows = []
    for i in range(1, reply_count + 1):
        created = now - timedelta(minutes=reply_count - i)
        reply_rows.append((floor_post_id, author_id, i, f'容量验证楼层 #{i}。', f'<p>容量验证楼层 #{i}。</p>', created, created))
    if reply_rows:
        cur.executemany(
            '''INSERT INTO replies
               (post_id, author_id, floor_no, content_md, content_html, created_at, updated_at)
               VALUES (%s,%s,%s,%s,%s,%s,%s)''',
            reply_rows,
        )
        conn.commit()
    print(f'楼层容量帖 id={floor_post_id}，已写 {len(reply_rows)} 楼')
    return floor_post_id


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true', help='实际写入（默认 dry-run）')
    ap.add_argument('--cleanup', action='store_true', help='只清理 [CAP] 数据')
    ap.add_argument('--posts', type=int, default=100000)
    ap.add_argument('--replies', type=int, default=1000)
    args = ap.parse_args()

    conn = connect()
    try:
        with conn.cursor() as cur:
            before = counts(cur)
            print(f'当前量级：posts={before[0]} replies={before[1]} cap_posts={before[2]}')
            if args.cleanup:
                if not args.apply:
                    print('dry-run：将删除全部 [CAP] 帖子 / 回复 / 标签关联（加 --apply 执行）')
                    return
                cleanup(conn, cur)
                print(f'清理后量级：{counts(cur)}')
                return
            print(f'计划：清理旧 CAP 数据 → 写入 {args.posts} 帖 + 1 个 1000 楼帖子（{args.replies} 楼）')
            if not args.apply:
                print('dry-run：未写入。加 --apply 执行。')
                return
            cleanup(conn, cur)
            floor_post_id = seed(conn, cur, args.posts, args.replies)
            print(f'完成。楼层容量帖 id={floor_post_id}；当前量级：{counts(cur)}')
    finally:
        conn.close()


if __name__ == '__main__':
    sys.exit(main())
