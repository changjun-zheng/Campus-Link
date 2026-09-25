/**
 * 图标注册表（CR-055 批次 3）
 * 素材为 Tabler Icons（MIT，许可全文见同目录 LICENSE），SVG 用 `stroke="currentColor"`，
 * 颜色由调用点的文字色令牌决定，本体不带任何色值。
 *
 * `eager: true` 是**编译期内联**：本目录下每个 svg 都会进首屏包，故只放实际被引用的图标
 * （20 个 / 7250 B；全量 43 个为 16092 B）。要加新图标，先从 `D:\Workspace\TechResources\
 * 前端素材\01-图标库-tabler\` 取归一化副本放进本目录，再在下面 ICON_NAMES 里加一行。
 */

/**
 * 显式名册，不靠 glob 的键反推：Vite 把 `import.meta.glob` 的类型定为
 * `Record<string, T>`，键不是字面量类型，推不出可用名。若不显式声明，
 * 图标名写错只会在运行时静默渲染成空白——编译期报错比这个好得多。
 * 与目录内 *.svg 文件名（去 .svg）一一对应；开发态有一致性告警兜底（见文件末）。
 */
const ICON_NAMES = [
  'arrow-left',
  'article',
  'bell',
  'book-2',
  'bookmark',
  'briefcase',
  'chevron-down',
  'chevron-right',
  'circle-check',
  'eye',
  'link',
  'list',
  'message-2',
  'message-circle',
  'messages',
  'pencil',
  'photo',
  'school',
  'search',
  'thumb-up',
  'trophy',
] as const

export type IconName = (typeof ICON_NAMES)[number]

const MODULES = import.meta.glob<string>('./*.svg', {
  query: '?raw',
  import: 'default',
  eager: true,
})

export function iconSvg(name: IconName): string {
  return MODULES[`./${name}.svg`] ?? ''
}

if (import.meta.env.DEV) {
  const files = Object.keys(MODULES).map((path) => path.slice(2, -4)).sort()
  const declared: string[] = [...ICON_NAMES].sort()
  const missing = declared.filter((name) => !files.includes(name))
  const unlisted = files.filter((name) => !declared.includes(name))
  if (missing.length) console.warn('[icons] 名册声明了但目录缺文件:', missing)
  if (unlisted.length) console.warn('[icons] 目录有文件但名册未声明（会白进包）:', unlisted)
}
