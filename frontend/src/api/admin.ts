import { del, get, post, put } from './client'

/** 举报工单（F-SAFE-002 / CR-082）：仅 SUPERADMIN 可读写列表与处理 */
export interface AdminReportVo {
  id: number
  reporterId: number
  targetType: 'POST' | 'REPLY' | 'USER'
  targetId: number
  reason: string
  detail?: string
  status: 'PENDING' | 'CLOSED'
  resolution?: string
  handlerId?: number
  createdAt: string
  closedAt?: string
}

export interface PageVo<T> {
  list: T[]
  total: number
  page: number
  size: number
}

/** 工单分页：status 缺省全部 */
export const listReports = (opts: { status?: string; page?: number; size?: number } = {}) => {
  const query = new URLSearchParams({ page: String(opts.page ?? 1), size: String(opts.size ?? 20) })
  if (opts.status) query.set('status', opts.status)
  return get<PageVo<AdminReportVo>>(`/admin/reports?${query.toString()}`)
}

/** 工单处理：action=REMOVE 联动目标处置（下架/封禁），DISMISS 驳回；note 为处理结论 */
export const closeReport = (id: number, action: 'REMOVE' | 'DISMISS', note?: string) =>
  put<void>(`/admin/reports/${id}`, { action, note })

/** 处置帖子（CR-066）：status=REMOVED 下架 / PUBLISHED 恢复 */
export const moderatePost = (id: number, status: 'PUBLISHED' | 'REMOVED', reason?: string) =>
  put<void>(`/admin/posts/${id}/status`, { status, reason })

/** 处置楼层（CR-066）：同帖子侧 */
export const moderateReply = (id: number, status: 'PUBLISHED' | 'REMOVED', reason?: string) =>
  put<void>(`/admin/replies/${id}/status`, { status, reason })

/** 封禁 / 解封（CR-082）：BANNED 即时生效 */
export const setUserStatus = (id: number, status: 'ACTIVE' | 'BANNED') =>
  put<void>(`/admin/users/${id}/status`, { status })

/** 提交举报（F-SAFE-002）：24h 同对象去重 → 429 / 2012 */
export const submitReport = (payload: {
  targetType: 'POST' | 'REPLY' | 'USER'
  targetId: number
  reason: string
  detail?: string
}) => post<{ id: number; status: string }>('/reports', payload)

/**
 * 数据看板（F-ADMIN-005 / CR-131，仅 SUPERADMIN）：四组扁平计数。
 *
 * 口径：内容组 `total` 只含未删内容，`removed` 是平台下架且计入 total，作者自删不计；
 * `*TodayCreated` 按 **UTC 自然日**；用户组 `userTotal` 含已注销、并单列 `userDeactivated`。
 */
export interface AdminDashboardVo {
  userTotal: number
  userActive: number
  userBanned: number
  userDeactivated: number
  postTotal: number
  postPublished: number
  postRemoved: number
  postTodayCreated: number
  replyTotal: number
  replyPublished: number
  replyRemoved: number
  replyTodayCreated: number
  reportTotal: number
  reportPending: number
  reportClosed: number
}

/** 运营看板（仅 SUPERADMIN）：进入看板 Tab 才调用 */
export const getAdminDashboard = () => get<AdminDashboardVo>('/admin/dashboard')

/**
 * 管理端标签（F-ADMIN-004 标签侧 / CR-132，仅 SUPERADMIN）。
 *
 * ⚠️ `useCount` 是**累计挂载次数**（`tags.use_count`，软删帖子不回退），不是"当前可见帖子数"；
 * 页面文案必须按此口径说明。
 */
export interface AdminTagVo {
  id: number
  name: string
  useCount: number
  createdAt: string
}

export interface AdminTagsVo {
  items: AdminTagVo[]
  total: number
  page: number
  size: number
}

/** 标签列表（仅 SUPERADMIN）：keyword 对名称模糊匹配；sort 只认 use_count（默认）/ name */
export const listAdminTags = (
  params: { keyword?: string; sort?: 'use_count' | 'name'; page?: number; size?: number } = {},
) => {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  query.set('sort', params.sort ?? 'use_count')
  query.set('page', String(params.page ?? 1))
  query.set('size', String(params.size ?? 20))
  return get<AdminTagsVo>(`/admin/tags?${query.toString()}`)
}

/**
 * 删除标签（仅 SUPERADMIN，破坏性操作、不可撤销）：服务端在一个事务里删 `post_tags` 关联、
 * 清受影响帖子的 `posts.tags` 冗余列、删 `tags` 行，并记一条 `TAG_DELETE` 审计。
 */
export const deleteAdminTag = (id: number) => del<void>(`/admin/tags/${id}`)

/**
 * 内容巡查队列（F-SAFE-004 / CR-133，仅 SUPERADMIN）。
 *
 * 只列未删内容、**已下架仍列出**（可恢复）；`kind=reply` 时 `title` 是楼层正文纯文本前 60 字（不含 HTML）。
 * 处置不在本组：复用 `moderatePost` / `moderateReply`（审计仍为 `MODERATE_POST` / `MODERATE_REPLY`）。
 */
export interface ReviewQueueItemVo {
  id: number
  kind: 'post' | 'reply'
  title: string
  boardName: string
  authorNickname: string
  status: 'PUBLISHED' | 'REMOVED'
  createdAt: string
}

export interface ReviewQueueVo {
  kind: 'post' | 'reply'
  items: ReviewQueueItemVo[]
  total: number
  page: number
  size: number
}

/** 巡查队列（仅 SUPERADMIN）：kind 只认 reply，其余服务端归一为 post */
export const getReviewQueue = (
  params: { kind?: 'post' | 'reply'; page?: number; size?: number } = {},
) => {
  const query = new URLSearchParams()
  query.set('kind', params.kind ?? 'post')
  query.set('page', String(params.page ?? 1))
  query.set('size', String(params.size ?? 20))
  return get<ReviewQueueVo>(`/admin/review-queue?${query.toString()}`)
}

/**
 * 后台内容搜索（F-ADMIN-002 / CR-141，仅 SUPERADMIN）。
 *
 * `keyword` 搜标题 / 正文（2~50 字）；`userId` 与 `authorKeyword` 二选一（userId 优先）；
 * `days` 只接受 7 / 30 / 90。只列未删内容、已下架仍列出；`summary` 为服务端纯文本前 200 字。
 * 处置不在本组：复用 `moderatePost` / `moderateReply`。
 */
export interface AdminContentItemVo {
  id: number
  kind: 'post' | 'reply'
  title: string
  summary: string
  boardName: string
  authorId: number
  authorNickname: string
  status: 'PUBLISHED' | 'REMOVED'
  createdAt: string
}

export interface AdminContentSearchVo {
  kind: 'post' | 'reply'
  items: AdminContentItemVo[]
  total: number
  page: number
  size: number
}

export const searchAdminContents = (
  params: {
    type?: 'post' | 'reply'
    keyword?: string
    userId?: number
    authorKeyword?: string
    days?: 7 | 30 | 90
    page?: number
    size?: number
  } = {},
) => {
  const query = new URLSearchParams()
  query.set('type', params.type ?? 'post')
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.userId) query.set('userId', String(params.userId))
  if (params.authorKeyword) query.set('authorKeyword', params.authorKeyword)
  if (params.days) query.set('days', String(params.days))
  query.set('page', String(params.page ?? 1))
  query.set('size', String(params.size ?? 20))
  return get<AdminContentSearchVo>(`/admin/contents?${query.toString()}`)
}
