import { get, post } from './client'

/**
 * 私信 API（F-SOC-003 / CR-135）。
 *
 * 后端全局 `default-property-inclusion: non_null`，null 字段直接从 JSON 消失，
 * 因此 `peerAvatarUrl` / `lastMessagePreview` / `readAt` 声明为可选，判空一律用真值判断。
 */
export interface ConversationVo {
  peerId: number
  peerNickname: string
  peerAvatarUrl?: string
  /** 最后一条消息的纯文本前 50 字 */
  lastMessagePreview?: string
  lastMessageAt: string
  unreadCount: number
}

/** 分页形状与论坛 / 通知一致：`list / total / page / size` */
export interface ConversationPageVo {
  list: ConversationVo[]
  total: number
  page: number
  size: number
}

export interface MessageVo {
  id: number
  senderId: number
  receiverId: number
  content: string
  createdAt: string
  /** 接收人读取时间；未读时字段整个不出现 */
  readAt?: string
}

export interface MessagePageVo {
  list: MessageVo[]
  total: number
  page: number
  size: number
}

export interface UnreadMessageCountVo {
  unreadCount: number
}

/** 我的会话列表（需登录）：按最后一条消息时间倒序 */
export const listConversations = (page = 1, size = 20) =>
  get<ConversationPageVo>(`/messages/conversations?page=${page}&size=${size}`)

/**
 * 与某人的消息（需登录）：**最新在前**倒序分页；无会话返回空页（不报错）。
 * 聊天页把每页收到后反转成时间正序再渲染。
 */
export const listMessages = (peerId: number, page = 1, size = 20) =>
  get<MessagePageVo>(`/messages/${peerId}?page=${page}&size=${size}`)

/** 发送私信（纯文本 1~1000 字）：返回落库后的消息，可直接追加到气泡流 */
export const sendMessage = (peerId: number, content: string) =>
  post<MessageVo>(`/messages/${peerId}`, { content })

/** 标记与某人的会话已读（幂等）：updated 为本次新标记条数 */
export const markConversationRead = (peerId: number) =>
  post<{ updated: number }>(`/messages/${peerId}/read`)

/** 未读私信总数（顶栏角标） */
export const getUnreadMessageCount = () => get<UnreadMessageCountVo>('/messages/unread-count')
