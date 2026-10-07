/**
 * 代码块复制按钮（Task #7）
 *
 * 后端 MarkdownRenderer 输出的 `<pre><code>` 是静态片段，无法在渲染时嵌入 Vue 组件，
 * 因此走 DOM 注入：给每个 `<pre>` 包一层 `.cl-pre-wrap`，在这层 wrapper 上挂一枚绝对
 * 定位的按钮（不挂在 `<pre>` 自身，见 `injectCopyButtons` 的说明），点击调 Clipboard API。
 *
 * 幂等性：以 `<pre>` 是否已被 `.cl-pre-wrap` 包裹作为已处理标记；v-html 替换整棵子树时
 * wrapper 与按钮随旧子树一起被丢弃，新的 pre 不带 wrapper，会自动重新注入，无泄漏。
 *
 * 与 v-highlight 的时序：highlight 只改 `<code>` 的 innerHTML（包 span，不改文本内容），
 * 不动 `<pre>` 本身，因此按钮注入放在 nextTick 之后即可，先后顺序都不影响快照到的代码文本。
 */

const BUTTON_MARK = 'data-copy-btn'

function setSuccessStyle(btn: HTMLButtonElement) {
  btn.classList.add('is-success')
  btn.setAttribute('aria-label', '复制成功')
}

function clearSuccessStyle(btn: HTMLButtonElement) {
  btn.classList.remove('is-success')
  btn.setAttribute('aria-label', '复制代码')
}

async function copyText(text: string): Promise<boolean> {
  // 优先走异步 Clipboard API（HTTPS / localhost 下可用）
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 落到 execCommand 兜底
    }
  }
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.top = '-1000px'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}

function createButton(codeText: string): HTMLButtonElement {
  const btn = document.createElement('button')
  btn.type = 'button'
  btn.className = 'cl-copy-btn'
  btn.setAttribute(BUTTON_MARK, '1')
  btn.setAttribute('aria-label', '复制代码')
  // 图标 + 文字：小方块作为"复制"隐喻，成功后切成勾
  btn.innerHTML = `
    <span class="cl-copy-btn__icon" aria-hidden="true">
      <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
        <rect x="5.5" y="5.5" width="8" height="8" rx="1.4"></rect>
        <path d="M10.5 3.2A1.7 1.7 0 0 0 8.8 2.5H4.2A1.7 1.7 0 0 0 2.5 4.2v4.6a1.7 1.7 0 0 0 .7 1.7"></path>
      </svg>
      <svg class="cl-copy-btn__check" viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3.5 8.5l3 3 6-7"></path>
      </svg>
    </span>
    <span class="cl-copy-btn__label">复制</span>
  `

  // 成功 / 失败两条分支共用同一个 timer：赋值前先清掉上一个，避免两个 setTimeout
  // 互相覆盖导致按钮卡在"已复制"或"复制失败"态不恢复。
  let timer: number | null = null
  function resetTimer() {
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
  }

  btn.addEventListener('click', async (ev) => {
    ev.preventDefault()
    ev.stopPropagation()
    const label = btn.querySelector<HTMLSpanElement>('.cl-copy-btn__label')
    const ok = await copyText(codeText)
    resetTimer()
    if (!ok) {
      if (label) label.textContent = '复制失败'
      btn.classList.add('is-error')
      timer = window.setTimeout(() => {
        btn.classList.remove('is-error')
        if (label) label.textContent = '复制'
        timer = null
      }, 1600)
      return
    }
    if (label) label.textContent = '已复制'
    setSuccessStyle(btn)
    timer = window.setTimeout(() => {
      if (label) label.textContent = '复制'
      clearSuccessStyle(btn)
      timer = null
    }, 2000)
  })

  return btn
}

/**
 * 给容器内所有 `<pre>` 注入复制按钮，返回注入数量。
 * 已有按钮的 pre 会被跳过；调用方在 v-html 更新后再次调用即可自动补齐。
 *
 * 按钮**不直接挂在 `<pre>` 上**：全局样式给 `.markdown-body pre` 设了 `overflow-x: auto`，
 * 绝对定位的子元素会随代码横向滚动一起被卷走，滚出可视区。改为给每个 `<pre>` 外面
 * 包一层 `.cl-pre-wrap`（`position: relative`），按钮挂在这层 wrapper 上，与 pre 的
 * 滚动区域解耦。
 */
export function injectCopyButtons(container: HTMLElement | null | undefined): number {
  if (!container) return 0
  const pres = Array.from(container.querySelectorAll<HTMLPreElement>('pre'))
  let added = 0
  for (const pre of pres) {
    // 已经被包过 wrapper（幂等）：v-html 整体替换时会连带丢弃 wrapper，不会误判
    if (pre.parentElement?.classList.contains('cl-pre-wrap')) continue
    // 必须在注入按钮**之前**快照代码文本：若 `<pre>` 没有 `<code>` 子元素，
    // 实时读 `pre.innerText` / `textContent` 会把按钮自身的"复制"文本一起带上。
    const codeEl = pre.querySelector('code')
    const codeText = ((codeEl || pre).textContent || '').replace(/\n$/, '')

    const wrap = document.createElement('div')
    wrap.className = 'cl-pre-wrap'
    // 显式补上定位上下文，避免依赖调用方的 scoped CSS（本工具可能被多个组件复用）
    wrap.style.position = 'relative'
    // 语言标注（CR-091，参考 paicoding）：读 highlight.js 的 `language-*` 类，写到
    // wrapper 的 data-lang 上，由 CSS 以 content:attr(data-lang) 常驻显示在复制按钮左侧；
    // 无语言类（无围栏标注的代码块）不显示。
    const lang = codeEl?.className.match(/language-([\w+-]+)/)
    if (lang) wrap.dataset.lang = lang[1].toUpperCase()
    pre.parentNode?.insertBefore(wrap, pre)
    wrap.appendChild(pre)
    wrap.appendChild(createButton(codeText))
    added += 1
  }
  return added
}
