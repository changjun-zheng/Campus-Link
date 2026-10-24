import { del, get, post } from './client'

/** 我是否拉黑了目标（F-SOC-004 / CR-129）：仅本人视角，不泄露反向关系 */
export interface BlockStateVo {
  blocked: boolean
}

export interface BlockedUserVo {
  id: number
  nickname: string
}

export interface BlockedUsersVo {
  items: BlockedUserVo[]
  total: number
  page: number
  size: number
}

/** 拉黑（幂等；不能拉黑自己 → 400 / 1001） */
export const blockUser = (targetId: number) => post<void>(`/users/${targetId}/block`)

/** 解除拉黑（幂等）：解除后历史内容与互动立即恢复 */
export const unblockUser = (targetId: number) => del<void>(`/users/${targetId}/block`)

/** 我与目标的拉黑状态（需登录） */
export const getBlockState = (targetId: number) =>
  get<BlockStateVo>(`/users/${targetId}/block-state`)

/** 我的黑名单（需登录，按拉黑时间倒序） */
export const listBlockedUsers = (page = 1, size = 20) =>
  get<BlockedUsersVo>(`/users/me/blocks?page=${page}&size=${size}`)
