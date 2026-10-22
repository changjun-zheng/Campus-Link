import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getUnreadCount, NotificationStreamError, openUnreadStream } from '../api/notifications'
import { useAuthStore } from '../stores/auth'

/**
 * 顶栏铃铛未读角标的共享状态（F-SOC-001）。
 *
 * <p>主通道是 **SSE**（F-010 / CR-121）：登录后连 `/notifications/stream`，通知产生即推未读数；
 * 30 秒轮询保留为**断线兜底**——SSE 不可用（代理 / 网络 / 服务端异常）时角标仍能在 30 秒内恢复。
 * 取数失败静默：角标是提示不是主链路，下一次推送或轮询自然恢复。
 *
 * <p>用模块级 ref 而不是 Pinia：全站只有顶栏一个消费者、通知中心页「全部已读」后需要顺带清零，
 * 一份计数不值得再开一个 store。
 */
const POLL_INTERVAL_MS = 30_000
const STREAM_RETRY_MS = 5_000

const unreadCount = ref(0)
let pollTimer: ReturnType<typeof setInterval> | null = null
let streamAbort: AbortController | null = null
let streamActive = false

export async function refreshUnreadCount(): Promise<void> {
  const auth = useAuthStore()
  if (!auth.isLoggedIn) {
    unreadCount.value = 0
    return
  }
  try {
    unreadCount.value = (await getUnreadCount()).unreadCount
  } catch {
    // 静默：见上文说明
  }
}

export function useUnreadNotifications() {
  const auth = useAuthStore()
  const route = useRoute()

  function armPolling() {
    if (pollTimer === null) {
      pollTimer = setInterval(refreshUnreadCount, POLL_INTERVAL_MS)
    }
  }

  function disarmPolling() {
    if (pollTimer !== null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function startStream() {
    if (streamActive || !auth.isLoggedIn) {
      return
    }
    streamActive = true
    const controller = new AbortController()
    streamAbort = controller
    void (async () => {
      while (streamActive && !controller.signal.aborted) {
        try {
          await openUnreadStream((count) => {
            unreadCount.value = count
          }, controller.signal)
        } catch (error) {
          if (error instanceof NotificationStreamError && error.status === 401) {
            // 登录态失效：停止重连，交给 30 秒轮询里的全局 401 处理器清会话
            streamActive = false
            break
          }
          // 其他失败（网络 / 代理 / 5xx）：轮询仍在跑，5 秒后重连
        }
        if (!streamActive || controller.signal.aborted) {
          break
        }
        await new Promise((resolve) => setTimeout(resolve, STREAM_RETRY_MS))
      }
    })()
  }

  function stopStream() {
    streamActive = false
    streamAbort?.abort()
    streamAbort = null
  }

  watch(
    () => auth.isLoggedIn,
    (loggedIn) => {
      if (loggedIn) {
        armPolling()
        startStream()
      } else {
        disarmPolling()
        stopStream()
      }
      void refreshUnreadCount()
    },
    { immediate: true },
  )

  // 站内动作（回帖 / 点赞 / 全部已读）不落服务端推送，靠切页时补一次读
  watch(() => route.fullPath, () => void refreshUnreadCount())

  onBeforeUnmount(() => {
    disarmPolling()
    stopStream()
  })

  return { unreadCount, refreshUnreadCount }
}
