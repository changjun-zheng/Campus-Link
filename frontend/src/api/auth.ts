import { get, post, put } from './client'
import type { UserInfo } from '../types'

export interface AuthResponse {
  token: string
  expiresAtEpochSeconds: number
  user: UserInfo
}

/** 学籍核验（F-ACC-004）：通过返回一次性核验票据 */
export const verifyStudent = (studentId: string, name: string) =>
  post<{ ticket: string }>('/auth/verify-student', { studentId, name })

export const sendCaptcha = (target: string) => post<void>('/auth/captcha', { target })

/** 注册（F-011 / CR-093）：contact 为邮箱**或**手机号（后端按形态分流） */
export const register = (payload: { ticket: string; contact: string; code: string; nickname: string }) =>
  post<AuthResponse>('/auth/register', payload)

/** 登录（F-011 / CR-093）：account 为邮箱或手机号 */
export const login = (account: string, code: string) => post<AuthResponse>('/auth/login', { account, code })

export const fetchMe = () => get<UserInfo>('/users/me')

/**
 * 资料编辑（F-ACC-007a）：PUT 全量语义——传 `null` 的字段后端保持原值，传空串即清空（昵称除外）。
 * 身份只从令牌取，请求体里没有 id；限流 10 次/小时，超限 `429 / 2008`（消息由后端给出，直接透出）。
 */
export const updateProfile = (payload: { nickname?: string | null; major?: string | null; bio?: string | null }) =>
  put<UserInfo>('/users/me', payload)

/** 注销账号（F-ACC-003 / CR-082）：进入 7 天冷静期，期内登录即撤销注销 */
export const deactivateAccount = () => post<void>('/users/me/deactivate')
