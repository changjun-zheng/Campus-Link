/**
 * 移动端加载性能基线（CR-172 建；CR-177 修复测量可靠性）——隔离无头 Edge + CDP，零依赖。
 *
 * 前置：`npm run build` + `npm run preview`（vite.config.ts 已配 preview.proxy /api）。
 * 用法：
 *   EDGE_PATH="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
 *   PERF_BASE="http://[::1]:4173" PERF_RUNS=3 \
 *   node backend/scripts/perf/run_mobile_perf.mjs mobile-perf.json
 *
 * 口径：375×812 @DPR2（touch/mobile）、CPU 4×、网络 1.6Mbps 下行 / 750kbps 上行 / 150ms RTT
 * （Lighthouse 移动端默认档），**每页冷缓存**（清 HTTP 缓存 + 禁缓存）后整页导航。
 * 测量改为「加载完成后用 buffered PerformanceObserver 回采」：CR-172 的注入式观察器在
 * 连续导航下会丢失，曾产出 LCP/TBT 全 0 的假数据；本版每页先校验 location / readyState，
 * 再回采 FCP / LCP / CLS / longtask。**每次采样使用独立浏览器进程**（同进程连续导航只首跑有
 * paint 指标），失败自动重试一次。
 * ⚠️ 单机回环 + preview，不含公网 RTT / TLS / 真实设备；数值只用于**同口径前后对比**。
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawn } from 'node:child_process'

const EDGE = process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const PORT = Number(process.env.CDP_PORT || 9340)
const PROFILE = process.env.CDP_PROFILE || path.join(os.tmpdir(), 'campuslink-mobile-perf-profile')
const BASE = process.env.PERF_BASE || 'http://localhost:4173'
const PAGES = (process.env.PERF_PAGES || '/,/post/51,/search?keyword=CR,/teams,/login')
  .split(',')
  .map((p) => p.trim())
  .filter(Boolean)
const RUNS = Number(process.env.PERF_RUNS || 1)
const OUT = process.argv[2]
if (!OUT) {
  console.error('用法：node backend/scripts/perf/run_mobile_perf.mjs <输出 json 路径>')
  process.exit(1)
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const MEASURE = `new Promise((resolve) => {
  const out = { lcp: 0, lcpEl: '', cls: 0, tbt: 0, longtasks: 0 };
  const paints = performance.getEntriesByType('paint');
  out.fcp = Math.round((paints.find((e) => e.name === 'first-contentful-paint') || {}).startTime || 0);
  try { new PerformanceObserver((l) => { const es = l.getEntries(); const e = es[es.length - 1]; if (e) { out.lcp = Math.round(e.startTime); const el = e.element; out.lcpEl = el ? el.tagName + '.' + String(el.className || '').slice(0, 40) : ''; } }).observe({ type: 'largest-contentful-paint', buffered: true }) } catch (e) {}
  try { new PerformanceObserver((l) => { for (const e of l.getEntries()) if (!e.hadRecentInput) out.cls += e.value }).observe({ type: 'layout-shift', buffered: true }) } catch (e) {}
  try { new PerformanceObserver((l) => { for (const e of l.getEntries()) { out.longtasks++; out.tbt += Math.max(0, e.duration - 50) } }).observe({ type: 'longtask', buffered: true }) } catch (e) {}
  setTimeout(() => {
    const nav = performance.getEntriesByType('navigation')[0] || {};
    const res = performance.getEntriesByType('resource');
    const sum = (arr) => Math.round(arr.reduce((a, r) => a + (r.transferSize || 0), 0) / 1024);
    resolve(JSON.stringify({
      path: location.pathname + location.search,
      fcp: out.fcp, lcp: out.lcp, lcpEl: out.lcpEl, cls: Number(out.cls.toFixed(4)),
      tbt: Math.round(out.tbt), longtasks: out.longtasks,
      dcl: Math.round(nav.domContentLoadedEventEnd || 0), load: Math.round(nav.loadEventEnd || 0),
      requests: res.length,
      jsKb: sum(res.filter((r) => r.name.includes('.js'))),
      cssKb: sum(res.filter((r) => r.name.includes('.css'))),
      imgKb: sum(res.filter((r) => r.initiatorType === 'img' || ['.png', '.jpg', '.svg', '.webp'].some((ext) => r.name.includes(ext)))),
      fontKb: sum(res.filter((r) => r.name.includes('.woff') || r.name.includes('.ttf'))),
      totalKb: sum(res),
      top: res.map((r) => ({ name: r.name.split('/').pop().slice(0, 48), kb: Math.round((r.transferSize || 0) / 1024), type: r.initiatorType }))
        .sort((a, b) => b.kb - a.kb).slice(0, 6),
    }));
  }, 1200);
})`

async function waitForCdp(port) {
  for (let i = 0; i < 60; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
      const page = list.find((t) => t.type === 'page')
      if (page) return page.webSocketDebuggerUrl
    } catch {}
    await sleep(500)
  }
  throw new Error('CDP 未就绪')
}

async function withBrowser(port, profile, fn) {
  fs.rmSync(profile, { recursive: true, force: true })
  const proc = spawn(EDGE, [
    '--headless=new',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profile}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--window-size=375,812',
    'about:blank',
  ])
  try {
    const ws = new WebSocket(await waitForCdp(port))
    let seq = 0
    const pending = new Map()
    ws.addEventListener('message', (e) => {
      const m = JSON.parse(e.data)
      if (m.id && pending.has(m.id)) {
        pending.get(m.id)(m.result)
        pending.delete(m.id)
      }
    })
    const send = (method, params = {}) =>
      new Promise((resolve, reject) => {
        const id = ++seq
        pending.set(id, resolve)
        ws.send(JSON.stringify({ id, method, params }))
        setTimeout(() => pending.has(id) && (pending.delete(id), reject(new Error(`CDP timeout: ${method}`))), 30000)
      })
    const ev = async (expression, awaitPromise = false) =>
      (await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise })).result?.value
    await new Promise((r) => ws.addEventListener('open', r, { once: true }))
    await send('Page.enable')
    await send('Runtime.enable')
    await send('Network.enable')
    await send('Emulation.setDeviceMetricsOverride', {
      width: 375, height: 812, deviceScaleFactor: 2, mobile: true, screenWidth: 375, screenHeight: 812,
    })
    await send('Network.emulateNetworkConditions', {
      offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8,
    })
    await send('Emulation.setCPUThrottlingRate', { rate: 4 })
    await send('Network.setCacheDisabled', { cacheDisabled: true })
    try {
      return await fn({ send, ev })
    } finally {
      ws.close()
    }
  } finally {
    proc.kill()
  }
}

async function main() {
  const results = []
  let runIndex = 0
  for (const page of PAGES) {
    const runs = []
    for (let run = 0; run < RUNS; run++) {
      const index = ++runIndex
      const row = await withBrowser(
        PORT + index,
        `${PROFILE}-${index}`,
        async ({ send, ev }) => {
          for (let attempt = 0; attempt < 2; attempt++) {
            await send('Network.clearBrowserCache')
            const url = `${BASE}${page}${page.includes('?') ? '&' : '?'}perfrun=${index}`
            await send('Page.navigate', { url })
            for (let i = 0; i < 40; i++) {
              const state = await ev(`location.href + '|' + document.readyState`)
              if (state === `${url}|complete`) break
              await sleep(250)
            }
            await sleep(4000)
            const raw = await ev(MEASURE, true)
            if (!raw) continue
            const parsed = JSON.parse(raw)
            if (!parsed.lcp) continue
            parsed.path = parsed.path.replace(/[?&]perfrun=[^&]*/, '').replace(/[?&]$/, '')
            return parsed
          }
          return null
        },
      )
      if (row) runs.push(row)
    }
    const median = (key) => {
      const vals = runs.map((r) => Number(r[key])).sort((a, b) => a - b)
      return vals.length ? vals[Math.floor(vals.length / 2)] : 0
    }
    const first = runs[0] || { path: page, top: [], requests: 0, jsKb: 0, cssKb: 0, imgKb: 0, fontKb: 0, totalKb: 0, lcpEl: '' }
    const result = {
      path: first.path ?? page,
      fcp: median('fcp'), lcp: median('lcp'), tbt: median('tbt'), cls: median('cls'),
      dcl: median('dcl'), load: median('load'),
      requests: first.requests, jsKb: first.jsKb, cssKb: first.cssKb, imgKb: first.imgKb, fontKb: first.fontKb,
      totalKb: first.totalKb, lcpEl: first.lcpEl, top: first.top, runs,
    }
    results.push(result)
    console.log(`${page}  LCP ${result.lcp}ms  TBT ${result.tbt}ms  CLS ${result.cls}  total ${result.totalKb}KB  req ${result.requests}  (median of ${runs.length})`)
  }
  fs.writeFileSync(OUT, JSON.stringify({
    base: BASE, viewport: '375x812@2x', cpu: 4, network: { latency: 150, downMbps: 1.6, upMbps: 0.75 }, pages: results,
  }, null, 2))
  console.log('written:', OUT)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
