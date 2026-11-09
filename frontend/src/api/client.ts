/** 所有业务接口的统一前缀；SSE（fetch 流）与普通 JSON 请求共用 */
export const API_BASE = '/api/v1'

const BASE = API_BASE

/** 后端未认证错误码：401 / 4001（见 common/result/ResultCode.NOT_LOGGED_IN） */
const UNAUTHORIZED_CODE = 4001

/** 后端统一响应：{ code, message, data, traceId }，code=0 成功（技术方案第 5 节） */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

/** 统一业务错误：携带错误码与 traceId，便于上层区分处理与排障 */
export class ApiError extends Error {
  constructor(
    readonly code: number,
    message: string,
    readonly traceId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * 401 全局处理器（BUG-004）：由 main.ts 注册（清会话 + 提示 + 跳登录）。
 * client.ts 不直接 import router/store，规避与二者的 ESM 循环依赖。
 */
let unauthorizedHandler: (() => void) | null = null
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

/**
 * 供不走 `request()` 的调用点复用同一套 401 处理（如问 AI 的 SSE 流）。
 * 预检失败返回的是普通 `ApiError`，其中 4001 必须和 JSON 请求一样清会话并跳登录。
 */
export function notifyUnauthorized(): void {
  unauthorizedHandler?.()
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('cl_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let res: Response
  // FormData 由浏览器自动带 boundary；显式写 application/json 会让后端解析失败（CR-191 上传头像）
  const isFormData = options.body instanceof FormData
  try {
    res = await fetch(BASE + path, {
      ...options,
      headers: {
        ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
        ...authHeaders(),
        ...(options.headers as Record<string, string> | undefined),
      },
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(-1, '网络异常，请检查后端服务是否已启动')
  }
  // 非 JSON 响应（网关 / CORS 403 / 代理错误页）不能把解析器的原始错误抛给用户（CR-173）
  const text = await res.text()
  let body: ApiResponse<T> | null = null
  try {
    body = text ? (JSON.parse(text) as ApiResponse<T>) : null
  } catch {
    body = null
  }
  if (!body || typeof body.code !== 'number') {
    throw new ApiError(res.status, httpStatusMessage(res.status))
  }
  if (body.code !== 0) {
    // 未认证：先触发全局退出处理器（清会话 + 跳登录），再按既有语义抛错，调用方的 catch 不受影响
    if (body.code === UNAUTHORIZED_CODE) {
      unauthorizedHandler?.()
    }
    throw new ApiError(body.code, body.message || `请求失败（${body.code}）`, body.traceId)
  }
  return body.data
}

/** 非业务错误（响应不是 ApiResponse 外壳）时的可达文案；不暴露网关 / 解析器原文 */
export function httpStatusMessage(status: number): string {
  if (status === 403) return '请求被服务器拒绝（HTTP 403），请稍后重试'
  if (status === 429) return '请求过于频繁（HTTP 429），请稍后重试'
  if (status >= 500) return '服务暂时不可用（HTTP 5xx），请稍后重试'
  return `请求失败（HTTP ${status}）`
}

export const get = <T>(path: string): Promise<T> => request<T>(path)

export const post = <T>(path: string, data?: unknown): Promise<T> =>
  request<T>(path, { method: 'POST', body: data === undefined ? undefined : JSON.stringify(data) })

/**
 * 纯文本请求体（CR-210 名册 CSV）：`post` 的 JSON.stringify 会转义换行并在首尾加引号，
 * 后端 `@RequestBody String` 会把整份 CSV 当成一行；这里原样发送并显式声明文本类型。
 */
export const postText = <T>(path: string, body: string, contentType = 'text/plain;charset=utf-8'): Promise<T> =>
  request<T>(path, { method: 'POST', body, headers: { 'Content-Type': contentType } })

export const put = <T>(path: string, data?: unknown): Promise<T> =>
  request<T>(path, { method: 'PUT', body: data === undefined ? undefined : JSON.stringify(data) })

export const del = <T>(path: string): Promise<T> => request<T>(path, { method: 'DELETE' })

/** 表单上传（CR-191）：Content-Type 交给浏览器生成（含 boundary） */
export const upload = <T>(path: string, formData: FormData, signal?: AbortSignal): Promise<T> =>
  request<T>(path, { method: 'POST', body: formData, signal })

/** Binary reads share JWT and error semantics with JSON requests. No URL tokens. */
export async function getBinary(path: string, signal?: AbortSignal): Promise<Blob> {
  let res: Response
  try {
    res = await fetch(BASE + path, { headers: authHeaders(), signal, cache: 'no-store' })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(-1, '网络异常，请检查后端服务是否已启动')
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null) as ApiResponse<never> | null
    if (body?.code === UNAUTHORIZED_CODE) unauthorizedHandler?.()
    throw new ApiError(body?.code ?? res.status, body?.message || httpStatusMessage(res.status), body?.traceId)
  }
  return res.blob()
}
