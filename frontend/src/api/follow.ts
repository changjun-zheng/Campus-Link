import { del, get, post } from './client'

/** 关注状态（CR-074）：following 为登录者视角；fanCount / followCount 为目标的粉丝数与关注数 */
export interface FollowStateVo {
  following: boolean
  fanCount: number
  followCount: number
}

/** 关注用户（不能关注自己 → 400 / 2009；重复关注幂等） */
export const followUser = (targetId: number) => post<void>(`/users/${targetId}/follow`)

/** 取消关注（未关注时幂等） */
export const unfollowUser = (targetId: number) => del<void>(`/users/${targetId}/follow`)

/** 我与目标的关注状态 + 目标的粉丝 / 关注计数（需登录） */
export const getFollowState = (targetId: number) => get<FollowStateVo>(`/users/${targetId}/follow-state`)

/**
 * 粉丝 / 关注列表条目（F-SOC-002 补齐 / CR-130），对应后端 `FollowUserVo`。
 *
 * `major` / `bio` 可能是 `undefined` 而不是 `null`：后端 `non_null` 序列化会把 null 字段整个删掉
 * （与 `NotificationVo` 的 `postId` 同一口径），判空一律用真值判断。
 */
export interface FollowUserVo {
  id: number
  nickname: string
  /** 默认头像（CR-163）：空值为 null / 缺省，前端回落昵称首字 */
  avatarUrl?: string | null
  major?: string | null
  bio?: string | null
}

/**
 * 粉丝 / 关注分页（需登录）。`total` 是**可点开的条目数**：已注销账号与登录者已拉黑账号
 * 已在服务端 SQL 侧剔除，与 `getFollowState` 的 `fanCount / followCount` 口径不同。
 */
export interface FollowUsersVo {
  items: FollowUserVo[]
  total: number
  page: number
  size: number
}

/** 某人的粉丝（需登录，按关注时间倒序） */
export const listFollowers = (targetId: number, page = 1, size = 20) =>
  get<FollowUsersVo>(`/users/${targetId}/followers?page=${page}&size=${size}`)

/** 某人关注的人（需登录，按关注时间倒序） */
export const listFollowing = (targetId: number, page = 1, size = 20) =>
  get<FollowUsersVo>(`/users/${targetId}/following?page=${page}&size=${size}`)
