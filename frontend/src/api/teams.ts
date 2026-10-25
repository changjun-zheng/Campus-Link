import { get, post, put } from './client'

/**
 * 组队广场 API（F-TEAM-001 / CR-137）。
 *
 * 后端全局 `default-property-inclusion: non_null`，null 字段直接从 JSON 消失，
 * 因此 `target` / `deadline` / `avatarUrl` / `contact` / `myRole` / `myApplicationStatus` 均为可选。
 */

export type TeamDirectionCode =
  | 'ALGORITHM'
  | 'FRONTEND'
  | 'BACKEND'
  | 'MOBILE'
  | 'DATA'
  | 'SECURITY'
  | 'OTHER'

export const TEAM_DIRECTIONS: { code: TeamDirectionCode; label: string }[] = [
  { code: 'ALGORITHM', label: '算法' },
  { code: 'FRONTEND', label: '前端' },
  { code: 'BACKEND', label: '后端' },
  { code: 'MOBILE', label: '移动端' },
  { code: 'DATA', label: '数据' },
  { code: 'SECURITY', label: '安全' },
  { code: 'OTHER', label: '其他' },
]

export type TeamStatusCode = 'RECRUITING' | 'FULL' | 'CLOSED'

export const TEAM_STATUS_LABELS: Record<TeamStatusCode, string> = {
  RECRUITING: '招募中',
  FULL: '已满员',
  CLOSED: '已关闭',
}

export interface TeamSummaryVo {
  id: number
  title: string
  direction: TeamDirectionCode
  directionLabel: string
  target?: string
  capacity: number
  acceptedCount: number
  status: TeamStatusCode
  deadline?: string
  ownerId: number
  ownerNickname: string
  createdAt: string
}

export interface TeamMemberVo {
  userId: number
  nickname: string
  avatarUrl?: string
  role: 'OWNER' | 'MEMBER'
}

export interface TeamDetailVo {
  id: number
  title: string
  /** 服务端渲染 + jsoup 白名单后的 HTML（与帖子详情同一条安全链路） */
  descriptionHtml: string
  direction: TeamDirectionCode
  directionLabel: string
  target?: string
  capacity: number
  acceptedCount: number
  status: TeamStatusCode
  deadline?: string
  ownerId: number
  ownerNickname: string
  createdAt: string
  members: TeamMemberVo[]
  myRole?: 'OWNER' | 'MEMBER'
  myApplicationStatus?: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  /** 仅发起人与已加入成员可见 */
  contact?: string
  contactVisible: boolean
}

export interface TeamApplicationVo {
  userId: number
  nickname: string
  avatarUrl?: string
  status: 'PENDING'
  appliedAt: string
}

export interface MyTeamVo {
  id: number
  title: string
  direction: TeamDirectionCode
  directionLabel: string
  target?: string
  capacity: number
  acceptedCount: number
  status: TeamStatusCode
  deadline?: string
  myRole: 'OWNER' | 'MEMBER'
  myApplicationStatus: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  pendingCount: number
  createdAt: string
}

export interface TeamPageVo<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface CreateTeamPayload {
  title: string
  descriptionMd: string
  direction: TeamDirectionCode
  target?: string
  capacity: number
  contact: string
  deadline?: string
}

function pageQuery(page: number, size: number): string {
  return `page=${page}&size=${size}`
}

/** 广场列表：status 缺省招募中；direction 缺省不过滤 */
export const listTeams = (params: {
  direction?: string
  status?: TeamStatusCode
  page?: number
  size?: number
}) => {
  const qs = [
    `page=${params.page ?? 1}`,
    `size=${params.size ?? 20}`,
    params.direction ? `direction=${encodeURIComponent(params.direction)}` : '',
    params.status ? `status=${params.status}` : '',
  ]
    .filter(Boolean)
    .join('&')
  return get<TeamPageVo<TeamSummaryVo>>(`/teams?${qs}`)
}

export const getTeam = (id: number) => get<TeamDetailVo>(`/teams/${id}`)

export const createTeam = (payload: CreateTeamPayload) => post<TeamDetailVo>('/teams', payload)

/** 申请加入：返回 PENDING；重复申请 / 满员 / 关闭等由后端给业务码 */
export const applyTeam = (id: number) =>
  post<{ userId: number; status: string; appliedAt: string }>(`/teams/${id}/applications`)

/** 发起人待审批列表 */
export const listTeamApplications = (id: number, page = 1, size = 20) =>
  get<TeamPageVo<TeamApplicationVo>>(`/teams/${id}/applications?${pageQuery(page, size)}`)

export const decideTeamApplication = (
  id: number,
  userId: number,
  decision: 'ACCEPT' | 'REJECT',
) => put<{ status: TeamStatusCode }>(`/teams/${id}/applications/${userId}`, { decision })

export const closeTeam = (id: number) => put<{ status: TeamStatusCode }>(`/teams/${id}/status`, { status: 'CLOSED' })

/** 我的组队：role=owner 我发起的（带待审批数）/ role=member 我申请的 */
export const listMyTeams = (role: 'owner' | 'member', page = 1, size = 20) =>
  get<TeamPageVo<MyTeamVo>>(`/teams/mine?role=${role}&${pageQuery(page, size)}`)
