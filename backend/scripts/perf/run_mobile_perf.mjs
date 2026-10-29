/**
 * 移动端加载性能基线（CR-172）——隔离无头 Edge + CDP，零依赖。
 *
 * 前置：`npm run build` + `npm run preview`（vite.config.ts 已配 preview.proxy /api）。
 * 用法：
 *   EDGE_PATH="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
 *   PERF_BASE="http://[::1]:4173" \
 *   node backend/scripts/perf/run_mobile_perf.mjs mobile-perf.json
 *
 * 口径：375×812 @DPR2（touch/mobile）、CPU 4×、网络 1.6Mbps 下行 / 750kbps 上行 / 150ms RTT
 * （Lighthouse 移动端默认档），**每页冷缓存**（清 HTTP 缓存 + 禁缓存）后整页导航，观察 7s。
 * 指标：FCP / LCP（含元素）/ TBT（longtask > 50ms）/ CLS / 资源字节（JS·CSS·图片·字体）。
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
const PAGES = (process.env.PERF_PAGES || '/,/post/51,/search?q=Java,/teams,/login')
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

const OBSERVER = `(() => {
  window.__perf = { fcp: 0, lcp: 0, lcpEl: '', cls: 0, tbt: 0, longtasks: 0 };
  try { new PerformanceObserver(l => { for (const e of l.getEntries()) if (e.name === 'first-contentful-paint') window.__perf.fcp = Math.round(e.startTime) }).observe({ type: 'paint', buffered: true }) } catch (e) {}
  try { new PerformanceObserver(l => { const es = l.getEntries(); const e = es[es.length - 1]; if (e) { window.__perf.lcp = Math.round(e.startTime); const el = e.element; window.__perf.lcpEl = el ? el.tagName + '.' + String(el.className || '').slice(0, 40) : '' } }).observe({ type: 'largest-contentful-paint', buffered: true }) } catch (e) {}
  try { new PerformanceObserver(l => { for (const e of l.getEntries()) if (!e.hadRecentInput) window.__perf.cls += e.value }).observe({ type: 'layout-shift', buffered: true }) } catch (e) {}
  try { new PerformanceObserver(l => { for (const e of l.getEntries()) { window.__perf.longtasks++; window.__perf.tbt += Math.max(0, e.duration - 50) } }).observe({ type: 'longtask', buffered: true }) } catch (e) {}
})();`

async function waitForCdp() {
  for (let i = 0; i < 60; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const page = list.find((t) => t.type === 'page')
      if (page) return page.webSocketDebuggerUrl
    } catch {}
    await sleep(500)
  }
  throw new Error('CDP 未就绪')
}

async function main() {
  fs.rmSync(PROFILE, { recursive: true, force: true })
  const proc = spawn(EDGE, [
    '--headless=new',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${PROFILE}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--window-size=375,812',
    'about:blank',
  ])
  try {
    const wsUrl = await waitForCdp()
    const ws = new WebSocket(wsUrl)
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
    const ev = async (expression) => (await send('Runtime.evaluate', { expression, returnByValue: true })).result?.value
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
    await send('Page.addScriptToEvaluateOnNewDocument', { source: OBSERVER })

    const results = []
    for (const page of PAGES) {
      const runs = []
      for (let run = 0; run < RUNS; run++) {
        await send('Network.clearBrowserCache')
        await send('Page.navigate', { url: 'about:blank' })
        await sleep(200)
        await send('Page.navigate', { url: BASE + page })
        await sleep(7000)
        const raw = await ev(`(() => {
        const nav = performance.getEntriesByType('navigation')[0] || {};
        const res = performance.getEntriesByType('resource');
        const sum = (arr) => Math.round(arr.reduce((a, r) => a + (r.transferSize || 0), 0) / 1024);
        const p = window.__perf || {};
        return JSON.stringify({
          path: location.pathname + location.search,
          fcp: p.fcp, lcp: p.lcp, lcpEl: p.lcpEl, cls: Number((p.cls || 0).toFixed(4)),
          tbt: Math.round(p.tbt), longtasks: p.longtasks,
          dcl: Math.round(nav.domContentLoadedEventEnd || 0), load: Math.round(nav.loadEventEnd || 0),
          requests: res.length,
          jsKb: sum(res.filter(r => /\\.js($|\\?)/.test(r.name))),
          cssKb: sum(res.filter(r => /\\.css($|\\?)/.test(r.name))),
          imgKb: sum(res.filter(r => r.initiatorType === 'img' || /\\.(png|jpg|svg|webp)($|\\?)/.test(r.name))),
          fontKb: sum(res.filter(r => /\\.(woff2?|ttf)($|\\?)/.test(r.name))),
          totalKb: sum(res),
          top: res.map(r => ({ name: r.name.split('/').pop().slice(0, 48), kb: Math.round((r.transferSize || 0) / 1024), type: r.initiatorType }))
            .sort((a, b) => b.kb - a.kb).slice(0, 6),
        });
      })()`)
        runs.push(JSON.parse(raw))
      }
      const median = (key) => {
        const vals = runs.map((r) => Number(r[key])).sort((a, b) => a - b)
        return vals.length ? vals[Math.floor(vals.length / 2)] : 0
      }
      const row = {
        path: runs[0].path, fcp: median('fcp'), lcp: median('lcp'), tbt: median('tbt'), cls: median('cls'),
        dcl: median('dcl'), load: median('load'), requests: runs[0].requests,
        jsKb: runs[0].jsKb, cssKb: runs[0].cssKb, imgKb: runs[0].imgKb, fontKb: runs[0].fontKb, totalKb: runs[0].totalKb,
        lcpEl: runs[0].lcpEl, top: runs[0].top, runs,
      }
      results.push(row)
      console.log(`${row.path}  LCP ${row.lcp}ms  TBT ${row.tbt}ms  CLS ${row.cls}  total ${row.totalKb}KB  req ${row.requests}  (median of ${RUNS})`)
    }
    fs.writeFileSync(OUT, JSON.stringify({ base: BASE, viewport: '375x812@2x', cpu: 4, network: { latency: 150, downMbps: 1.6, upMbps: 0.75 }, pages: results }, null, 2))
    console.log('written:', OUT)
    ws.close()
  } finally {
    proc.kill()
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
