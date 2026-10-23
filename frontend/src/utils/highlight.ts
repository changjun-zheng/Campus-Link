import type { Directive } from 'vue'
import type { HLJSApi, LanguageFn } from 'highlight.js'

/**
 * 代码高亮（CR-055 批次 1，界面规范 §9.1 P0④ / ADR-005 的最后一块欠账）。
 *
 * 边界：Markdown 渲染与净化只在后端 `common/markdown/MarkdownRenderer` 完成，
 * 这里拿到的是已净化的 `<pre><code class="language-*">` 静态 HTML。
 * `highlightElement` 只读 `textContent`、写回转义后的 span，既不解析 Markdown，
 * 也不承担任何净化职责——它只是着色器。
 *
 * 体积：highlight.js 全量约 1MB，故用 `lib/core` + 按需注册，且**只加载当前页面
 * 真正出现的语言**（core 与每个语言各自成 chunk，首屏完全不下载）。
 */

/** 计算机专业学生论坛的实际覆盖面；不在表内的语言静默跳过，不着色也不报错 */
const LOADERS = new Map<string, () => Promise<{ default: LanguageFn }>>([
  ['java', () => import('highlight.js/lib/languages/java')],
  ['javascript', () => import('highlight.js/lib/languages/javascript')],
  ['typescript', () => import('highlight.js/lib/languages/typescript')],
  ['python', () => import('highlight.js/lib/languages/python')],
  ['sql', () => import('highlight.js/lib/languages/sql')],
  ['bash', () => import('highlight.js/lib/languages/bash')],
  ['shell', () => import('highlight.js/lib/languages/shell')],
  ['json', () => import('highlight.js/lib/languages/json')],
  ['xml', () => import('highlight.js/lib/languages/xml')],
  ['css', () => import('highlight.js/lib/languages/css')],
  ['go', () => import('highlight.js/lib/languages/go')],
  ['c', () => import('highlight.js/lib/languages/c')],
  ['cpp', () => import('highlight.js/lib/languages/cpp')],
  ['csharp', () => import('highlight.js/lib/languages/csharp')],
  ['php', () => import('highlight.js/lib/languages/php')],
  ['ruby', () => import('highlight.js/lib/languages/ruby')],
  ['rust', () => import('highlight.js/lib/languages/rust')],
  ['kotlin', () => import('highlight.js/lib/languages/kotlin')],
  ['swift', () => import('highlight.js/lib/languages/swift')],
  ['yaml', () => import('highlight.js/lib/languages/yaml')],
  ['markdown', () => import('highlight.js/lib/languages/markdown')],
  ['diff', () => import('highlight.js/lib/languages/diff')],
  ['properties', () => import('highlight.js/lib/languages/properties')],
  ['ini', () => import('highlight.js/lib/languages/ini')],
  ['http', () => import('highlight.js/lib/languages/http')],
  ['dockerfile', () => import('highlight.js/lib/languages/dockerfile')],
  ['nginx', () => import('highlight.js/lib/languages/nginx')],
  ['gradle', () => import('highlight.js/lib/languages/gradle')],
  ['plaintext', () => import('highlight.js/lib/languages/plaintext')],
])

const LANGUAGE_CLASS = /(?:^|\s)language-([\w+-]+)/

let hljsPromise: Promise<HLJSApi> | null = null
const registered = new Set<string>()
const registering = new Map<string, Promise<void>>()

function core(): Promise<HLJSApi> {
  hljsPromise ??= import('highlight.js/lib/core').then(({ default: hljs }) => hljs)
  return hljsPromise
}

function ensureLanguage(hljs: HLJSApi, name: string): Promise<void> {
  const load = LOADERS.get(name)
  if (!load || registered.has(name)) return Promise.resolve()
  let pending = registering.get(name)
  if (!pending) {
    pending = load().then(({ default: lang }) => {
      hljs.registerLanguage(name, lang)
      registered.add(name)
      registering.delete(name)
    })
    registering.set(name, pending)
  }
  return pending
}

/**
 * 只给带**已支持** `language-*` 类的代码块着色。
 * 无 info string 的围栏后端输出的是裸 `<pre><code>`，交给 hljs 会自动猜语言，
 * 把日志、报错、纯文本涂得五颜六色——不可预测，故明确跳过（视觉上与现状一致）。
 */
async function highlightIn(el: HTMLElement): Promise<void> {
  const blocks = Array.from(
    el.querySelectorAll<HTMLElement>('pre code[class*="language-"]:not([data-highlighted])'),
  )
  const wanted = new Map<HTMLElement, string>()
  for (const block of blocks) {
    const name = LANGUAGE_CLASS.exec(block.className)?.[1]
    if (name && LOADERS.has(name)) wanted.set(block, name)
  }
  if (wanted.size === 0) return

  const hljs = await core()
  await Promise.all([...new Set(wanted.values())].map((name) => ensureLanguage(hljs, name)))
  for (const [block] of wanted) {
    // 不要预写 dataset.highlighted：highlight.js 的 highlightElement 见到该标记会直接跳过
    // （控制台只留一行 "Element previously highlighted"，页面永远不着色——CR-128 浏览器验收抓到的真实缺陷）。
    // 标记由 highlightElement 自己写；上面的选择器已排除 [data-highlighted]，不会重复处理。
    hljs.highlightElement(block)
  }
}

export const vHighlight: Directive<HTMLElement> = {
  mounted: (el) => void highlightIn(el),
  updated: (el) => void highlightIn(el),
}
