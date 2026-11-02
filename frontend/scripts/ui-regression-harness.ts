/**
 * Vite 开发服务器下的手工/浏览器回归夹具，不进入 index.html 或生产构建。
 * 访问 /scripts/ui-regression-harness.html；沿用当前开发账号，从真实发布页开始。
 * 故障只在本页生效，不改后端、数据库或浏览器持久配置。
 */
const enabled = (id: string) => (document.getElementById(id) as HTMLInputElement).checked
const setItem = Storage.prototype.setItem
Storage.prototype.setItem = function (key: string, value: string) {
  if (key === 'cl_draft' && enabled('fail-draft')) {
    throw new DOMException('CR-185 simulated quota failure', 'QuotaExceededError')
  }
  return setItem.call(this, key, value)
}

const originalFetch = window.fetch.bind(window)
let submitCount = 0
let previewCount = 0
window.fetch = async (input, init) => {
  const url = new URL(input instanceof Request ? input.url : String(input), location.origin)
  const method = (init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase()
  const preview = url.pathname === '/api/v1/markdown/preview'
  const submit = /^\/api\/v1\/posts(?:\/\d+)?$/.test(url.pathname) && ['POST', 'PUT'].includes(method)
  if (preview) {
    previewCount++
    document.getElementById('preview-count')!.textContent = String(previewCount)
  }
  if (submit) {
    submitCount++
    document.getElementById('submit-count')!.textContent = String(submitCount)
  }
  if ((submit && enabled('fail-submit')) || (preview && enabled('fail-preview'))) {
    // 给重复点击留出可观测窗口；故障模式下完全不向后端发送提交。
    await new Promise((resolve) => setTimeout(resolve, 1200))
    return new Response(JSON.stringify({ code: 1000, message: 'CR-185 模拟服务暂时不可用', traceId: 'cr185-fixture' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    })
  }
  const response = await originalFetch(input, init)
  if (preview && enabled('delay-preview')) {
    await new Promise((resolve) => setTimeout(resolve, 1800))
  }
  return response
}

history.replaceState(history.state, '', '/publish')
await import('../src/main')

export {}
