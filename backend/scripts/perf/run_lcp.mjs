/**
 * PRD §4 首屏性能基线（CR-154）——隔离无头 Edge + CDP 测 LCP，零依赖。
 *
 * 前置：`npm run build` + `npm run preview`（vite.config.ts 已配 preview.proxy /api）。
 * 用法：
 *   EDGE_PATH="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
 *   LCP_BASE=http://localhost:4173 LCP_RUNS=10 \
 *   node backend/scripts/perf/run_lcp.mjs perf-lcp.json
 *
 * 口径：1440×900 @DPR1、**缓存禁用**、每次整页导航；等待 load 后再观察 LCP 2.5s。
 * ⚠️ 本地回环不含公网 RTT / TLS，数值是前端渲染 + 本机库的量级，不是线上 SLA。
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawn } from 'node:child_process'

const EDGE = process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PORT = Number(process.env.CDP_PORT || 9334)
const PROFILE = process.env.CDP_PROFILE || path.join(os.tmpdir(), 'campuslink-lcp-profile')
const BASE = process.env.LCP_BASE || 'http://localhost:4173'
const RUNS = Number(process.env.LCP_RUNS || 10)
const OUT = process.argv[2]
if (!OUT) {
  console.error('用法：node backend/scripts/perf/run_lcp.mjs <输出 json 路径>')
  process.exit(1)
}

const PAGES = [
  ['home', '/'],
  ['board', '/board/qna'],
  ['post-detail', '/post/41'],
  ['search-result', '/search?keyword=Redis'],
]
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

class CDP {
  constructor(ws) {
    this.ws = ws
    this.seq = 0
    this.pending = new Map()
    this.listeners = new Set()
    ws.onmessage = (e) => {
      const msg = JSON.parse(e.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result)
      } else if (msg.method) for (const fn of this.listeners) fn(msg)
    }
  }
  send(method, params = {}) {
    const id = ++this.seq
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error(`CDP timeout: ${method}`))
        }
      }, 30000)
    })
  }
  on(fn) {
    this.listeners.add(fn)
    return () => this.listeners.delete(fn)
  }
}

function percentile(sorted, p) {
  if (!sorted.length) return null
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  return Math.round(sorted[idx] * 100) / 100
}

async function main() {
  fs.rmSync(PROFILE, { recursive: true, force: true })
  const proc = spawn(EDGE, [
    '--headless=new',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${PROFILE}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-extensions',
    '--disable-sync',
    '--disable-component-update',
    'about:blank',
  ], { stdio: 'ignore' })

  let version = null
  for (let i = 0; i < 80; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/version`)
      if (r.ok) { version = await r.json(); break }
    } catch {}
    await sleep(250)
  }
  if (!version) throw new Error('Edge CDP 未就绪')

  const pageInfo = await (await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' })).json()
  const ws = new WebSocket(pageInfo.webSocketDebuggerUrl)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  const cdp = new CDP(ws)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: true })
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

  const waitLoad = (timeout = 20000) => new Promise((resolve) => {
    const off = cdp.on((msg) => {
      if (msg.method === 'Page.loadEventFired') { off(); resolve(true) }
    })
    setTimeout(() => { off(); resolve(false) }, timeout)
  })

  const evaluate = async (expression) => {
    const r = await cdp.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.text)
    return r.result.value
  }

  const results = { base: BASE, runs: RUNS, cacheDisabled: true, startedAt: new Date().toISOString(), pages: {} }
  await cdp.send('Page.navigate', { url: `${BASE}/` })
  await waitLoad()

  for (const [name, pagePath] of PAGES) {
    const samples = []
    for (let i = 0; i < RUNS; i++) {
      const load = waitLoad()
      await cdp.send('Page.navigate', { url: `${BASE}${pagePath}` })
      await load
      samples.push(await evaluate(`(async () => {
        const lcp = await new Promise((resolve) => {
          let last = 0;
          try {
            new PerformanceObserver((list) => { for (const e of list.getEntries()) last = e.startTime; })
              .observe({ type: 'largest-contentful-paint', buffered: true });
          } catch {}
          setTimeout(() => resolve(last), 2500);
        });
        const nav = performance.getEntriesByType('navigation')[0] || {};
        const fcp = performance.getEntriesByName('first-contentful-paint')[0]?.startTime ?? null;
        return { lcp, fcp, dcl: nav.domContentLoadedEventEnd ?? null, load: nav.loadEventEnd ?? null,
                 resources: performance.getEntriesByType('resource').length,
                 path: location.pathname + location.search };
      })()`))
    }
    const lcps = samples.map((s) => s.lcp).filter((x) => x > 0).sort((a, b) => a - b)
    results.pages[name] = {
      path: pagePath,
      count: lcps.length,
      lcpP50: percentile(lcps, 50),
      lcpP90: percentile(lcps, 90),
      lcpMax: lcps.length ? Math.round(lcps[lcps.length - 1] * 100) / 100 : null,
      fcpP90: percentile(samples.map((s) => s.fcp).filter((x) => x > 0).sort((a, b) => a - b), 90),
      samples,
    }
    console.log(`${name}: LCP p50=${results.pages[name].lcpP50} p90=${results.pages[name].lcpP90} max=${results.pages[name].lcpMax}`)
  }

  results.finishedAt = new Date().toISOString()
  fs.writeFileSync(OUT, JSON.stringify(results, null, 2), 'utf8')
  console.log('written', OUT)
  ws.close()
  proc.kill()
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
