import { get, post, put } from './client'
import type { PageVo } from './forum'

/** 通知类型码，与后端 notifications.type 落库值一致（quote 尚未实现，故不在此列出） */
export type NotificationType = 'reply' | 'like' | 'favorite' | 'accept'

/**
 * 与后端 web/vo/NotificationVo 一一对应（F-SOC-001）。
 * 内容字段（昵称 / 标题 / 楼层号）由服务端读时组装：触发人已注销时昵称是「已注销用户」，
 * 原帖已删除时标题是「内容已删除」且不给 postId（此时前端不应给出跳转）。
 * ⚠️ 后端全局 `default-property-inclusion: non_null`，null 字段直接从 JSON 消失，
 * 因此可空字段声明为可选（判空一律用真值判断，不要用 `=== null`）。
 */
export interface NotificationVo {
  id: number
  type: NotificationType
  actorId: number
  actorNickname: string
  /** POST = 目标是帖子（点赞 / 收藏），REPLY = 目标是楼层（回复 / 采纳 / 楼层点赞） */
  targetType: 'POST' | 'REPLY'
  targetId: number
  /** 目标帖已删除时不下发 */
  postId?: number
  postTitle: string
  /** 仅楼层类通知有值，用于「#N 楼」提示 */
  floorNo?: number
  read: boolean
  createdAt: string
}

export interface UnreadCountVo {
  unreadCount: number
}

/** 我的通知（需登录）：unread 缺省不限、true 只未读；按时间倒序 */
export const listNotifications = (opts: { unread?: boolean; page?: number; size?: number } = {}) => {
  const query = new URLSearchParams({
    page: String(opts.page ?? 1),
    size: String(opts.size ?? 20),
  })
  if (opts.unread !== undefined) {
    query.set('unread', String(opts.unread))
  }
  return get<PageVo<NotificationVo>>(`/notifications?${query.toString()}`)
}

/** 未读数（顶栏铃铛角标） */
export const getUnreadCount = () => get<UnreadCountVo>('/notifications/unread-count')

/** 通知流连接失败（携带 HTTP 状态；401 表示登录态已失效，调用方应停止重连） */
export class NotificationStreamError extends Error {
  constructor(readonly status: number) {
    super(`通知流连接失败（${status}）`)
    this.name = 'NotificationStreamError'
  }
}

/**
 * 未读数实时流（F-010 / CR-121）：`event: unread` + `data: {"unreadCount": n}`。
 *
 * <p>不用 `EventSource`——它无法携带 `Authorization` 头，而令牌不能进 query string；
 * 这里用 `fetch` 读 `ReadableStream` 并自行解析 SSE 帧。
 * 连接结束（服务端 30 分钟超时 / 网络断开）时返回，由调用方决定重连节奏。
 */
export async function openUnreadStream(
  onUnread: (count: number) => void,
  signal: AbortSignal,
): Promise<void> {
  const token = localStorage.getItem('cl_token')
  if (!token) {
    throw new NotificationStreamError(401)
  }
  const res = await fetch('/api/v1/notifications/stream', {
    headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
    signal,
  })
  if (!res.ok || !res.body) {
    throw new NotificationStreamError(res.status)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (!signal.aborted) {
    const { done, value } = await reader.read()
    if (done) break
    buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const frame = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const event = /^event:\s*(.+)$/m.exec(frame)?.[1]?.trim()
      if (event === 'unread') {
        const data = /^data:\s*(.+)$/m.exec(frame)?.[1]
        if (data) {
          try {
            const parsed = JSON.parse(data) as { unreadCount?: number }
            if (typeof parsed.unreadCount === 'number') {
              onUnread(parsed.unreadCount)
            }
          } catch {
            // 单帧解析失败不终止连接：下一帧仍可恢复（角标还有轮询兜底）
          }
        }
      }
      boundary = buffer.indexOf('\n\n')
    }
  }
}

/** 全部标记已读：updated 为本次新标记的条数 */
export const markAllRead = () => put<{ updated: number }>('/notifications/read-all')

/**
 * 单条标记已读（幂等，CR-078 功能 C）：无请求体也无出参。
 * 已读再调仍 200；不存在与非本人一律 404 / 3001（后端不区分二者，避免泄露他人通知的存在性）。
 */
export const markNotificationRead = (id: number) => post<void>(`/notifications/${id}/read`)
