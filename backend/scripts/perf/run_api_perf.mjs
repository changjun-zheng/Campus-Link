/**
 * PRD §4 性能基线实测（CR-154）——零依赖 Node 脚本。
 *
 * 用法：
 *   PERF_TOKEN=<dev token> node backend/scripts/perf/run_api_perf.mjs \
 *     [--base http://localhost:8088] [--reads 200] [--writes 20] [--out perf.json]
 *
 * 口径：
 * - 本地真实栈（MySQL / Redis / 后端），并发池发起请求，记录**端到端**耗时（fetch 开始到响应体读完）；
 * - 读接口每项先预热 20 次再计时；写接口并发 5、逐条创建后统一软删除（DELETE /posts/{id}）；
 * - 输出 P50 / P90 / P95 / P99，供对照 PRD：读 P95 ≤ 500ms、写 P95 ≤ 800ms。
 * ⚠️ 本地回环，不含公网 RTT 与 TLS；数值只能证明「应用侧处理 + 本机库」的量级，不等于线上 SLA。
 */
import fs from 'node:fs'

const args = process.argv.slice(2)
const pick = (name, fallback) => {
  const i = args.indexOf(`--${name}`)
  return i >= 0 ? args[i + 1] : fallback
}
const BASE = pick('base', 'http://localhost:8088')
const TOKEN = process.env.PERF_TOKEN || ''
const READS = Number(pick('reads', '200'))
const WRITES = Number(pick('writes', '20'))
const OUT = pick('out', '')
const READ_CONCURRENCY = 10
const WRITE_CONCURRENCY = 5
const WARMUP = 20

if (!TOKEN) {
  console.error('缺少 PERF_TOKEN（使用开发账号 token，勿用生产令牌）')
  process.exit(1)
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function timed(url, init) {
  const start = performance.now()
  const res = await fetch(url, init)
  await res.arrayBuffer()
  const ms = performance.now() - start
  return { ms, status: res.status, ok: res.ok }
}

async function pool(tasks, concurrency) {
  let next = 0
  const workers = Array.from({ length: Math.min(concurrency, tasks.length) }, async () => {
    while (next < tasks.length) {
      const i = next++
      await tasks[i]()
    }
  })
  await Promise.all(workers)
}

function percentile(sorted, p) {
  if (!sorted.length) return null
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  return Math.round(sorted[idx] * 100) / 100
}

function summarize(name, durations, statusCounts) {
  const sorted = [...durations].sort((a, b) => a - b)
  const errors = Object.entries(statusCounts)
    .filter(([s]) => !s.startsWith('2') && !s.startsWith('3'))
    .map(([status, count]) => ({ status: Number(status), count }))
  return {
    name,
    count: durations.length,
    statusCounts,
    errors,
    min: sorted.length ? Math.round(sorted[0] * 100) / 100 : null,
    p50: percentile(sorted, 50),
    p90: percentile(sorted, 90),
    p95: percentile(sorted, 95),
    p99: percentile(sorted, 99),
    max: sorted.length ? Math.round(sorted[sorted.length - 1] * 100) / 100 : null,
    samples: sorted.map((x) => Math.round(x * 100) / 100),
  }
}

async function measure(name, makeTask, total, concurrency = READ_CONCURRENCY) {
  const durations = []
  const statusCounts = {}
  const tasks = Array.from({ length: total }, (_, i) => async () => {
    try {
      const r = await makeTask(i)
      statusCounts[r.status] = (statusCounts[r.status] || 0) + 1
      // 非 2xx/3xx 不计入延迟分位（例如 /users/{id} 的 429 限流响应，混入会稀释真实分布）
      if (r.status >= 200 && r.status < 400) durations.push(r.ms)
    } catch (e) {
      statusCounts.error = (statusCounts.error || 0) + 1
    }
  })
  await pool(tasks, concurrency)
  return summarize(name, durations, statusCounts)
}

async function main() {
  const headers = { Authorization: `Bearer ${TOKEN}` }
  const readEndpoints = [
    ['list-latest', '/api/v1/posts?page=1&size=20&sort=latest'],
    ['list-hot', '/api/v1/posts?page=1&size=20&sort=hot'],
    ['post-detail', '/api/v1/posts/41'],
    ['post-replies', '/api/v1/posts/41/replies?page=1&size=20'],
    ['search', '/api/v1/posts/search?keyword=Redis&page=1&size=20'],
    // 不压测 GET /users/{id}：它按 IP 限流 10 次/小时（防名册批量枚举，F-ACC-002），
    // 并发压测只会得到 429；其单次延迟由手工 10 样本另行记录（见性能基线文档）。
    ['teams-list', '/api/v1/teams?page=1&size=12'],
  ]
  const results = { base: BASE, startedAt: new Date().toISOString(), reads: READS, writes: WRITES, endpoints: {}, writesCreated: 0, cleanup: { deleted: 0, failed: [] } }

  for (const [name, path] of readEndpoints) {
    for (let i = 0; i < WARMUP; i++) await timed(BASE + path, { headers })
    const r = await measure(name, () => timed(BASE + path, { headers }), READS)
    results.endpoints[name] = { path, ...r }
    console.log(`${name}: p50=${r.p50} p90=${r.p90} p95=${r.p95} p99=${r.p99} errors=${r.errors.length}`)
  }

  // 写：发帖
  const created = []
  const titles = Array.from({ length: WRITES }, (_, i) => `[perf] P95 基线 ${Date.now()}-${i}`)
  const publish = await measure('publish-post', async (i) => {
    return timed(`${BASE}/api/v1/posts`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ boardCode: 'qna', title: titles[i], contentMd: 'PRD §4 写接口性能基线实测，无需保留。', tags: [] }),
    })
  }, WRITES, WRITE_CONCURRENCY)
  results.endpoints['publish-post'] = { path: '/api/v1/posts', ...publish }
  console.log(`publish-post: p50=${publish.p50} p90=${publish.p90} p95=${publish.p95} p99=${publish.p99} ok=${publish.count}`)

  // 写响应体里拿 id 需要重新读；改为按标题检索拿 id（受搜索索引影响），这里直接查“我的帖子”更可靠
  const mine = await fetch(`${BASE}/api/v1/posts/mine?page=1&size=100`, { headers }).then((r) => r.json())
  const mineList = mine?.data?.list ?? mine?.data?.records ?? []
  for (const p of mineList) {
    if (typeof p.title === 'string' && p.title.startsWith('[perf] P95 基线')) created.push(p.id)
  }
  results.writesCreated = created.length

  // 回帖：并发对同一批 perf 帖各回一条（若创建成功拿到 id）
  const replyTargets = created.slice(0, WRITES)
  const reply = await measure('publish-reply', async (i) => {
    const postId = replyTargets[i % Math.max(1, replyTargets.length)]
    return timed(`${BASE}/api/v1/posts/${postId}/replies`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ contentMd: 'PRD §4 写接口性能基线实测回复，无需保留。' }),
    })
  }, replyTargets.length ? WRITES : 0, WRITE_CONCURRENCY)
  results.endpoints['publish-reply'] = { path: '/api/v1/posts/{id}/replies', ...reply }
  console.log(`publish-reply: p50=${reply.p50} p90=${reply.p90} p95=${reply.p95} p99=${reply.p99} ok=${reply.count}`)

  // 清理：软删除本次创建的帖（回复随父帖不可见；墓碑行留在开发库，如实记录）
  for (const id of created) {
    const r = await fetch(`${BASE}/api/v1/posts/${id}`, { method: 'DELETE', headers })
    if (r.ok) results.cleanup.deleted++
    else results.cleanup.failed.push({ id, status: r.status })
  }
  console.log(`cleanup: deleted=${results.cleanup.deleted} failed=${results.cleanup.failed.length}`)

  results.finishedAt = new Date().toISOString()
  const text = JSON.stringify(results, null, 2)
  if (OUT) {
    fs.writeFileSync(OUT, text, 'utf8')
    console.log('written', OUT)
  } else {
    console.log(text)
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
