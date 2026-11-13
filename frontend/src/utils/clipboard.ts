/**
 * 统一剪贴板复制（CR-221）
 *
 * 代码块复制、AI 回答复制、楼层链接复制共用同一实现，避免三份逻辑各自漂移。
 * 优先走异步 Clipboard API（HTTPS / localhost 下可用）；不可用或被拒时降级到
 * `execCommand('copy')` 兜底（http 内网地址等场景），两条路径都失败才返回 false。
 * 调用方负责把 false 转成用户可见的失败提示，本函数不打日志、不弹 toast。
 */
export async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 落到 execCommand 兜底
    }
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.top = '-1000px'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    return ok
  } catch {
    return false
  }
}
