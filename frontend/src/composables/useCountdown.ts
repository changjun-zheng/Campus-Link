import { onScopeDispose, ref } from 'vue'

/**
 * 组合式函数：验证码发送 60s 倒计时（登录 / 注册两处复用）。
 *
 * <p>只有发送成功才启动；2002（60s 重发窗口内）由调用方捕获后显式 {@link start}，
 * 让按钮进入倒计时而不是静默停在可点状态。scope 销毁时清定时器。
 */
export function useCountdown(seconds = 60) {
  const sending = ref(false)
  const remaining = ref(0)
  let timer: number | undefined

  function clear() {
    if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
  }

  function start() {
    clear()
    remaining.value = seconds
    timer = window.setInterval(() => {
      remaining.value--
      if (remaining.value <= 0) clear()
    }, 1000)
  }

  async function run(action: () => Promise<void>) {
    if (remaining.value > 0 || sending.value) return
    sending.value = true
    try {
      await action()
      start()
    } finally {
      sending.value = false
    }
  }

  onScopeDispose(clear)

  return { sending, remaining, run, start }
}
