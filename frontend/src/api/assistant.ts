import {
  API_BASE,
  ApiError,
  get,
  httpStatusMessage,
  notifyUnauthorized,
  type ApiResponse,
} from './client'

/** 后端 NOT_LOGGED_IN（ResultCode 4001） */
const UNAUTHORIZED_CODE = 4001

export interface AssistantConfig {
  enabled: boolean
  dailyLimit: number
  maxSelectedChars: number
  maxQuestionChars: number
  privacyNoticeVersion: string
  quickPrompts: string[]
}

export interface AssistantMeta {
  conversationId: string
  remainingToday: number
}

export interface AssistantDone {
  /** 最终 Markdown 原文（复制用） */
  raw: string
  /** 服务端净化后的 HTML（展示用，前端不再解析 Markdown） */
  html: string
  finishReason: string
  blocked: boolean
  promptTokens?: number | null
  completionTokens?: number | null
}

export interface AssistantStreamHandlers {
  onMeta: (meta: AssistantMeta) => void
  onDelta: (text: string) => void
  onDone: (done: AssistantDone) => void
  /** 流内 error 事件或流中断；预检失败以 Promise reject 的形式抛出 */
  onError: (error: ApiError) => void
}

/** 公开配置：前端据此决定是否显示「问 AI」入口与字数上限 */
export const getAssistantConfig = () => get<AssistantConfig>('/assistant/config')

interface StartConversationBody {
  postId: number
  selectedText: string
  question: string
}

/**
 * 开启一轮问 AI 并消费 SSE。
 *
 * <p>不用 `EventSource`：它无法携带 `Authorization`，且只支持 GET。这里用 `fetch` 读
 * `ReadableStream`，与通知流（`api/notifications.ts`）保持同一口径。
 * `onDone` / `onError` 二选一触发；调用方通过 signal 主动停止。
 */
export function startConversation(
  body: StartConversationBody,
  handlers: AssistantStreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  return stream('/assistant/conversations', body, handlers, signal)
}

/** 在当前会话内追问；会话归属与 30 分钟过期由后端校验（7005 / 7008） */
export function followUpConversation(
  conversationId: string,
  question: string,
  handlers: AssistantStreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  return stream(
    `/assistant/conversations/${encodeURIComponent(conversationId)}/messages`,
    { question },
    handlers,
    signal,
  )
}

async function stream(
  path: string,
  body: unknown,
  handlers: AssistantStreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const token = localStorage.getItem('cl_token')
  if (!token) {
    notifyUnauthorized()
    throw new ApiError(UNAUTHORIZED_CODE, '请先登录')
  }

  let res: Response
  try {
    res = await fetch(API_BASE + path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(-1, '网络异常，请检查后端服务是否已启动')
  }

  const contentType = res.headers.get('content-type') || ''
  if (!res.ok || !contentType.includes('text/event-stream')) {
    throw await toApiError(res)
  }
  if (!res.body) {
    throw new ApiError(-1, '回答流不可用，请稍后再试')
  }

  await readSse(res.body, handlers, signal)
}

/** 预检失败走普通 `ApiResponse` 外壳；非 JSON（网关 / 代理错误页）同样收口成可读文案 */
async function toApiError(res: Response): Promise<ApiError> {
  const text = await res.text().catch(() => '')
  let body: ApiResponse<never> | null = null
  try {
    body = text ? (JSON.parse(text) as ApiResponse<never>) : null
  } catch {
    body = null
  }
  if (body?.code === UNAUTHORIZED_CODE) {
    notifyUnauthorized()
  }
  return new ApiError(
    body?.code ?? res.status,
    body?.message || httpStatusMessage(res.status),
    body?.traceId,
  )
}

async function readSse(
  body: ReadableStream<Uint8Array>,
  handlers: AssistantStreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let settled = false

  const dispatch = (frame: string): boolean => {
    const event = parseFrame(frame)
    if (!event.data) return false
    switch (event.name) {
      case 'meta': {
        const parsed = safeParse<AssistantMeta>(event.data)
        if (parsed && typeof parsed.conversationId === 'string') {
          handlers.onMeta(parsed)
        }
        return false
      }
      case 'delta': {
        const parsed = safeParse<{ text?: string }>(event.data)
        if (parsed && typeof parsed.text === 'string') {
          handlers.onDelta(parsed.text)
        }
        return false
      }
      case 'done': {
        const parsed = safeParse<AssistantDone>(event.data)
        if (parsed && typeof parsed.html === 'string') {
          handlers.onDone(parsed)
        } else {
          handlers.onError(new ApiError(-1, '回答解析失败，请重试'))
        }
        return true
      }
      case 'error': {
        const parsed = safeParse<{ code?: number; message?: string }>(event.data)
        const code = typeof parsed?.code === 'number' ? parsed.code : -1
        if (code === UNAUTHORIZED_CODE) notifyUnauthorized()
        handlers.onError(new ApiError(code, parsed?.message || 'AI 助手暂不可用，请稍后再试'))
        return true
      }
      default:
        return false
    }
  }

  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read()
      if (done) break
      buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        if (dispatch(frame)) {
          settled = true
          return
        }
        boundary = buffer.indexOf('\n\n')
      }
    }
    // 末尾可能没有空行分隔的收尾帧；只在未主动停止时处理
    if (!settled && !signal.aborted && buffer.trim()) {
      settled = dispatch(buffer)
    }
    if (!settled && !signal.aborted) {
      handlers.onError(new ApiError(-1, '连接中断，请重试'))
    }
  } catch (error) {
    if (signal.aborted || (error instanceof DOMException && error.name === 'AbortError')) {
      return
    }
    handlers.onError(new ApiError(-1, '网络异常，回答生成中断'))
  } finally {
    try {
      reader.releaseLock()
    } catch {
      // 已取消的 reader 再次释放不构成错误
    }
  }
}

function parseFrame(frame: string): { name: string; data: string } {
  let name = 'message'
  const data: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      name = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).replace(/^ /, ''))
    }
  }
  return { name, data: data.join('\n') }
}

function safeParse<T>(raw: string): T | null {
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}
