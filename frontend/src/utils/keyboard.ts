/**
 * 键盘事件辅助（Modern Web Guidance · ime-safe-enter-submit）。
 *
 * 中文 / 日文 / 韩文输入法用 Enter 确认候选词时，`keydown` 的 `isComposing` 为 `true`。
 * 自定义「Enter 提交」若只看 `event.key === 'Enter'`，会在用户还没选完词时提前提交。
 *
 * 旧版 Safari 存在事件顺序缺陷（WebKit bug 165004）：确认键的 `keydown` 在
 * `compositionend` 之后才派发给脚本，此时 `isComposing` 已被重置；用
 * `keyCode === 229`（VK_PROCESSKEY，输入法处理中）兜底。该判断只在
 * `key === 'Enter'` 的分支里调用，不会误伤移动端虚拟键盘的普通输入。
 */
export function isImeConfirm(event?: Event): boolean {
  return event instanceof KeyboardEvent && (event.isComposing || event.keyCode === 229)
}
