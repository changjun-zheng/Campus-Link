import { del, get, getBinary, post, postText, put } from './client'

/** 举报工单（F-SAFE-002 / CR-082、CR-196）：OPS / SUPERADMIN 可读写列表与处理 */
export interface AdminReportVo {
  id: number
  reporterId: number
  targetType: 'POST' | 'REPLY' | 'USER'
  targetId: number
  reason: string
  detail?: string
  status: 'PENDING' | 'PROCESSING' | 'CLOSED'
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

/** 认领工单（CR-214）：PENDING → PROCESSING 并记录处理人；已处理中 / 已闭环时幂等 200 */
export const claimReport = (id: number) => post<void>(`/admin/reports/${id}/claim`)

/** 工单处理：action=REMOVE 联动目标处置（下架/封禁），DISMISS 驳回；note 为处理结论 */
export const closeReport = (id: number, action: 'REMOVE' | 'DISMISS', note?: string) =>
  put<void>(`/admin/reports/${id}`, { action, note })

/** 处置帖子（CR-066）：status=REMOVED 下架 / PUBLISHED 恢复 */
export const moderatePost = (id: number, status: 'PUBLISHED' | 'REMOVED', reason?: string) =>
  put<void>(`/admin/posts/${id}/status`, { status, reason })

/** 处置楼层（CR-066）：同帖子侧 */
export const moderateReply = (id: number, status: 'PUBLISHED' | 'REMOVED', reason?: string) =>
  put<void>(`/admin/replies/${id}/status`, { status, reason })

/**
 * 后台用户检索（CR-211，仅 SUPERADMIN）：纯数字关键字按用户 id 精确查，否则按昵称 LIKE；
 * 最多返回 50 条，`truncated=true` 表示还有更多。只含账号基础状态，不含邮箱 / 学号。
 */
export interface AdminUserItemVo {
  id: number
  nickname: string
  role: 'USER' | 'OPS' | 'SUPERADMIN'
  status: 'ACTIVE' | 'BANNED' | 'DEACTIVATED'
  mutedUntil?: string | null
  createdAt: string
}

export interface AdminUserSearchVo {
  items: AdminUserItemVo[]
  truncated: boolean
}

export const searchAdminUsers = (keyword: string) =>
  get<AdminUserSearchVo>(`/admin/users?${new URLSearchParams({ keyword }).toString()}`)

/** 封禁 / 解封（CR-082）：BANNED 即时生效 */
export const setUserStatus = (id: number, status: 'ACTIVE' | 'BANNED') =>
  put<void>(`/admin/users/${id}/status`, { status })

/**
 * 授予运营角色（F-ADMIN-001 / CR-196，仅 SUPERADMIN）：只允许 USER → OPS，幂等。
 * 目标用户须重新登录后才拿到 OPS 权限；撤销见 `revokeOpsRole`。
 */
export const grantOpsRole = (id: number) => put<void>(`/admin/users/${id}/role`, { role: 'OPS' })

/**
 * 撤销运营角色（CR-212，仅 SUPERADMIN）：只允许 OPS → USER，幂等；
 * 撤销即时吊销目标旧 token，重新登录后为普通用户。
 */
export const revokeOpsRole = (id: number) => del<void>(`/admin/users/${id}/role`)

/** 警告用户（F-ADMIN-002 / CR-209）：不改账号状态，仅发送带原因的站内通知 */
export const warnUser = (id: number, reason: string) => post<void>(`/admin/users/${id}/warn`, { reason })

/** 禁言（F-ADMIN-002 / CR-209）：1~720 小时；到期自然解除 */
export const muteUser = (id: number, hours: number, reason: string) =>
  put<void>(`/admin/users/${id}/mute`, { hours, reason })

/** 解除禁言（F-ADMIN-002 / CR-209）：幂等 */
export const unmuteUser = (id: number) => del<void>(`/admin/users/${id}/mute`)

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
  reportProcessing: number
  reportClosed: number
}

/** 运营看板（仅 SUPERADMIN）：进入看板 Tab 才调用 */
export const getAdminDashboard = () => get<AdminDashboardVo>('/admin/dashboard')

/**
 * 操作审计（CR-215，仅 SUPERADMIN）：`audit_logs` 只读查询，按时间倒序。
 *
 * `actorNickname` 由服务端 LEFT JOIN users 给出——`actorId` 为空时是「系统」，
 * 账号行缺失时是「已注销用户」，前端不自行推断；`detail` 原样展示（列宽 2048）。
 */
export interface AdminAuditLogVo {
  id: number
  actorId?: number | null
  actorNickname: string
  action: string
  targetType?: string | null
  targetId?: number | null
  detail?: string | null
  createdAt: string
}

/** 审计分页（CR-215 / CR-216）：action / targetType 精确匹配、actorId / targetId 精确匹配、days 只接受 7 / 30 / 90 */
export interface AuditQueryParams {
  action?: string
  actorId?: number
  targetType?: string
  targetId?: number
  days?: 7 | 30 | 90
}

function auditQuery(params: AuditQueryParams): URLSearchParams {
  const query = new URLSearchParams()
  if (params.action) query.set('action', params.action)
  if (params.actorId != null) query.set('actorId', String(params.actorId))
  if (params.targetType) query.set('targetType', params.targetType)
  if (params.targetId != null) query.set('targetId', String(params.targetId))
  if (params.days) query.set('days', String(params.days))
  return query
}

export const listAuditLogs = (
  params: AuditQueryParams & { page?: number; size?: number } = {},
) => {
  const query = auditQuery(params)
  query.set('page', String(params.page ?? 1))
  query.set('size', String(params.size ?? 20))
  return get<PageVo<AdminAuditLogVo>>(`/admin/audit-logs?${query.toString()}`)
}

/** 审计 CSV 导出（CR-216）：与分页同筛选，后端上限 5000 行（超出 1001）；返回 Blob 供浏览器下载 */
export const exportAuditLogs = (params: AuditQueryParams = {}) => {
  const query = auditQuery(params)
  return getBinary(`/admin/audit-logs/export?${query.toString()}`)
}


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
 * 内容巡查队列（F-SAFE-004 / CR-133、CR-196）：OPS / SUPERADMIN 可读。
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

/** 巡查队列（OPS / SUPERADMIN）：kind 只认 reply，其余服务端归一为 post */
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
 * 后台内容搜索（F-ADMIN-002 / CR-141、CR-196）：OPS / SUPERADMIN 可读。
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

/**
 * 名册 CSV 导入（F-ACC-004 / CR-210，仅 SUPERADMIN）。
 *
 * body 为 CSV 原文（`学号,姓名[,年级[,专业]]`，后端自动跳过中文 / 英文表头），
 * batch 写入 `student_roster.source_batch` 并进入 `ROSTER_IMPORT` 审计。
 * `skipped` 是学号已存在（幂等跳过），`failed` 是格式不合规（不入库），均不是请求失败。
 */
export interface RosterImportResultVo {
  inserted: number
  skipped: number
  failed: number
}

export const importRoster = (csv: string, batch: string) => {
  const query = new URLSearchParams({ batch })
  return postText<RosterImportResultVo>(
    `/admin/roster/import?${query.toString()}`,
    csv,
    'text/csv;charset=utf-8',
  )
}
