/**
 * 计数缩写（CR-091，参考 CSDN 列表元信息）：千位 k、万位 w，一位小数并去尾零。
 * 论坛量级下 999 以下原样显示；"1234" → "1.2k"、"12000" → "1.2w"。
 */
export function formatCount(n: number): string {
  if (n >= 10000) {
    const w = n / 10000
    return (w >= 100 ? Math.round(w) : Number(w.toFixed(1))) + 'w'
  }
  if (n >= 1000) {
    const k = n / 1000
    return (k >= 100 ? Math.round(k) : Number(k.toFixed(1))) + 'k'
  }
  return String(n)
}
