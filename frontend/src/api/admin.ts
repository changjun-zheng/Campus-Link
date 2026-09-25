import { get, post, put } from './client'

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
