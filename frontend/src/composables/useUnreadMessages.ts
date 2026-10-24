import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getUnreadMessageCount } from '../api/messages'
import { useAuthStore } from '../stores/auth'

/**
 * 顶栏私信未读角标的共享状态（F-SOC-003 / CR-135）。
 *
 * <p>本轮只做 **30 秒轮询 + 发送 / 已读后主动刷新**（方案 D5：不做 SSE，后续可复用 CR-121 的模式）。
 * 取数失败静默：角标是提示不是主链路，下一次轮询自然恢复。
 *
 * <p>用模块级 ref 而不是 Pinia：全站只有顶栏一个消费者，会话页「已读 / 发送」后直接调
 * {@link refreshUnreadMessages} 刷新，一份计数不值得再开一个 store。
 */
const POLL_INTERVAL_MS = 30_000

const unreadCount = ref(0)
let pollTimer: ReturnType<typeof setInterval> | null = null

export async function refreshUnreadMessages(): Promise<void> {
  const auth = useAuthStore()
  if (!auth.isLoggedIn) {
    unreadCount.value = 0
    return
  }
  try {
    unreadCount.value = (await getUnreadMessageCount()).unreadCount
  } catch {
    // 静默：见上文说明
  }
}

export function useUnreadMessages() {
  const auth = useAuthStore()
  const route = useRoute()

  function armPolling() {
    if (pollTimer === null) {
      pollTimer = setInterval(refreshUnreadMessages, POLL_INTERVAL_MS)
    }
  }

  function disarmPolling() {
    if (pollTimer !== null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  watch(
    () => auth.isLoggedIn,
    (loggedIn) => {
      if (loggedIn) {
        armPolling()
      } else {
        disarmPolling()
        unreadCount.value = 0
      }
      void refreshUnreadMessages()
    },
    { immediate: true },
  )

  // 站内动作（发送 / 已读）不落服务端推送，靠切页时补一次读
  watch(() => route.fullPath, () => void refreshUnreadMessages())

  onBeforeUnmount(disarmPolling)

  return { unreadCount, refreshUnreadMessages }
}
