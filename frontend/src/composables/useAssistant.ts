import { computed, onBeforeUnmount, ref } from 'vue'
import { ApiError } from '../api/client'
import {
  followUpConversation,
  getAssistantConfig,
  startConversation,
  type AssistantConfig,
  type AssistantDone,
  type AssistantStreamHandlers,
} from '../api/assistant'
import { useAuthStore } from '../stores/auth'

/** 后端 7xxx：只对需要改变面板形态的几类做前端处理，其余按普通错误展示 */
const CODE_QUOTA_EXCEEDED = 7002
const CODE_CONVERSATION_NOT_FOUND = 7005
const CODE_TURN_LIMIT = 7008

export interface AssistantMessage {
  id: number
  role: 'user' | 'assistant'
  /** 流式期间是模型原始文本；done 后是最终 Markdown 原文（复制用） */
  content: string
  /** done 帧携带的服务端净化 HTML；非空时用 v-html 展示 */
  html?: string
  streaming: boolean
  stopped?: boolean
  blocked?: boolean
  error?: string
  errorCode?: number
}

interface Attempt {
  question: string
}

/**
 * 帖子详情页的「选中文本问 AI」状态（CR-200）。
 *
 * <p>状态只活在当前页面：会话 id 由后端 meta 帧下发，刷新 / 关闭面板即丢失（Redis 30 分钟，
 * 但前端不恢复——方案明确不做历史会话列表）。选中内容与问题由调用方传入，本组合式不读 DOM。
 */
export function useAssistant(postId: () => number | null) {
  const auth = useAuthStore()

  const config = ref<AssistantConfig | null>(null)
  const enabled = computed(() => Boolean(config.value?.enabled))
  const maxSelectedChars = computed(() => config.value?.maxSelectedChars ?? 0)
  const maxQuestionChars = computed(() => config.value?.maxQuestionChars ?? 0)

  const open = ref(false)
  const privacyVisible = ref(false)
  const selectedText = ref('')
  const input = ref('')
  const messages = ref<AssistantMessage[]>([])
  const streaming = ref(false)
  const conversationId = ref('')
  /** 当前会话是否至少有一条已成功落 Redis 的回答；决定下一次请求是 start 还是 follow-up */
  const hasSavedTurn = ref(false)
  const remainingToday = ref<number | null>(null)
  /** 会话过期（7005）或轮次用尽（7008）：面板改为「重新开始」态 */
  const ended = ref(false)
  const quotaExhausted = computed(() => remainingToday.value === 0)

  let nextMessageId = 1
  let configRequest: Promise<void> | null = null
  let controller: AbortController | null = null
  let lastAttempt: Attempt | null = null

  async function loadConfig(): Promise<void> {
    if (config.value || configRequest) return configRequest ?? Promise.resolve()
    configRequest = getAssistantConfig()
      .then((value) => {
        config.value = value
      })
      .catch(() => {
        // 详情页不因助手配置失败而报错：入口保持隐藏，下一次打开页面再试
        config.value = null
      })
      .finally(() => {
        configRequest = null
      })
    return configRequest
  }

  function privacyKey(): string {
    return `cl_assistant_privacy_${auth.user?.id ?? 'anonymous'}`
  }

  function needsPrivacyNotice(): boolean {
    if (!config.value) return false
    return localStorage.getItem(privacyKey()) !== config.value.privacyNoticeVersion
  }

  /** 由选区工具条调用；返回 false 表示助手未开放（调用方不必再走登录跳转） */
  function openWithSelection(text: string): boolean {
    if (!enabled.value) return false
    resetConversation()
    selectedText.value = text
    open.value = true
    privacyVisible.value = needsPrivacyNotice()
    return true
  }

  function acceptPrivacy(): void {
    if (config.value) {
      localStorage.setItem(privacyKey(), config.value.privacyNoticeVersion)
    }
    privacyVisible.value = false
  }

  function declinePrivacy(): void {
    privacyVisible.value = false
    close()
  }

  function close(): void {
    abort()
    open.value = false
    privacyVisible.value = false
  }

  async function sendQuestion(question?: string): Promise<void> {
    if (streaming.value || ended.value || quotaExhausted.value) return
    const text = (question ?? input.value).trim()
    if (!text || !selectedText.value) return
    if (maxQuestionChars.value > 0 && text.length > maxQuestionChars.value) return
    input.value = ''
    await dispatch(text)
  }

  async function dispatch(question: string): Promise<void> {
    const useStart = !hasSavedTurn.value || !conversationId.value
    const user: AssistantMessage = {
      id: nextMessageId++,
      role: 'user',
      content: question,
      streaming: false,
    }
    const assistant: AssistantMessage = {
      id: nextMessageId++,
      role: 'assistant',
      content: '',
      streaming: true,
    }
    messages.value.push(user, assistant)
    lastAttempt = { question }
    streaming.value = true

    const current = new AbortController()
    controller = current
    const handlers: AssistantStreamHandlers = {
      onMeta(meta) {
        conversationId.value = meta.conversationId
        remainingToday.value = meta.remainingToday
      },
      onDelta(text) {
        assistant.content += text
        assistant.stopped = false
      },
      onDone(done) {
        applyDone(assistant, done)
      },
      onError(error) {
        applyError(assistant, error)
      },
    }

    try {
      if (useStart) {
        await startConversation(
          { postId: postId() ?? 0, selectedText: selectedText.value, question },
          handlers,
          current.signal,
        )
      } else {
        await followUpConversation(conversationId.value, question, handlers, current.signal)
      }
    } catch (error) {
      if (!current.signal.aborted) {
        applyError(
          assistant,
          error instanceof ApiError ? error : new ApiError(-1, 'AI 助手暂不可用，请稍后再试'),
        )
      }
    } finally {
      if (controller === current) {
        controller = null
        streaming.value = false
      }
      assistant.streaming = false
    }
  }

  function applyDone(message: AssistantMessage, done: AssistantDone): void {
    message.content = done.raw
    message.html = done.html
    message.blocked = done.blocked
    message.streaming = false
    message.error = undefined
    message.errorCode = undefined
    hasSavedTurn.value = true
  }

  function applyError(message: AssistantMessage, error: ApiError): void {
    message.streaming = false
    message.error = error.message
    message.errorCode = error.code
    if (error.code === CODE_CONVERSATION_NOT_FOUND) {
      ended.value = true
      lastAttempt = null
    } else if (error.code === CODE_TURN_LIMIT) {
      ended.value = true
      lastAttempt = null
    } else if (error.code === CODE_QUOTA_EXCEEDED) {
      remainingToday.value = 0
    }
  }

  function stop(): void {
    if (!streaming.value) return
    const message = [...messages.value].reverse().find((m) => m.role === 'assistant' && m.streaming)
    if (message) {
      message.streaming = false
      message.stopped = true
      if (!message.content) message.content = '已停止生成'
    }
    streaming.value = false
    abort()
  }

  async function retry(): Promise<void> {
    if (streaming.value || !lastAttempt) return
    const question = lastAttempt.question
    const last = messages.value[messages.value.length - 1]
    if (last?.role === 'assistant' && last.error) {
      messages.value.pop()
    }
    const previous = messages.value[messages.value.length - 1]
    if (previous?.role === 'user' && previous.content === question) {
      messages.value.pop()
    }
    lastAttempt = null
    await dispatch(question)
  }

  function restart(): void {
    resetConversation()
  }

  function resetConversation(): void {
    abort()
    messages.value = []
    conversationId.value = ''
    hasSavedTurn.value = false
    ended.value = false
    input.value = ''
    lastAttempt = null
  }

  function abort(): void {
    const current = controller
    controller = null
    streaming.value = false
    current?.abort()
  }

  async function copyMessage(message: AssistantMessage): Promise<boolean> {
    const text = message.content
    if (!text) return false
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

  onBeforeUnmount(abort)

  return {
    enabled,
    config,
    maxSelectedChars,
    maxQuestionChars,
    open,
    privacyVisible,
    selectedText,
    input,
    messages,
    streaming,
    remainingToday,
    quotaExhausted,
    ended,
    loadConfig,
    openWithSelection,
    acceptPrivacy,
    declinePrivacy,
    close,
    sendQuestion,
    stop,
    retry,
    restart,
    copyMessage,
  }
}
