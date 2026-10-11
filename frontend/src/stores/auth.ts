import { defineStore } from 'pinia'
import { fetchMe, login as loginApi, register as registerApi } from '../api/auth'
import type { UserInfo } from '../types'

const TOKEN_KEY = 'cl_token'
const USER_KEY = 'cl_user'
const EXP_KEY = 'cl_exp'

/** 读取本地过期时间戳（秒）；缺失或非法一律返回 0，表示"无过期信息" */
function readStoredExpiresAt(): number {
  const raw = Number(localStorage.getItem(EXP_KEY))
  return Number.isFinite(raw) && raw > 0 ? raw : 0
}

/** 启动即校验（BUG-004）：token 存在但已过期就直接清本地会话，避免刷新后先渲染登录态再被跳走 */
function clearExpiredSessionOnBoot(): void {
  const exp = readStoredExpiresAt()
  if (localStorage.getItem(TOKEN_KEY) && exp > 0 && exp * 1000 <= Date.now()) {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(EXP_KEY)
  }
}
clearExpiredSessionOnBoot()

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null') as UserInfo | null,
    expiresAt: readStoredExpiresAt(),
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    /** 登录（F-011 / CR-093）：account 为邮箱或手机号 */
    async login(account: string, code: string) {
      const res = await loginApi(account, code)
      this.setSession(res.token, res.user, res.expiresAtEpochSeconds)
    },
    async register(payload: { ticket: string; contact: string; code: string; nickname: string }) {
      const res = await registerApi(payload)
      this.setSession(res.token, res.user, res.expiresAtEpochSeconds)
    },
    async fetchMe() {
      this.setUser(await fetchMe())
    },
    /** 资料编辑成功后由 PUT 的全量回显直接落库（F-ACC-007a：不必二次拉取 `GET /users/me`） */
    setUser(user: UserInfo) {
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    setSession(token: string, user: UserInfo, expiresAtEpochSeconds?: number) {
      this.token = token
      this.user = user
      this.expiresAt = expiresAtEpochSeconds && expiresAtEpochSeconds > 0 ? expiresAtEpochSeconds : 0
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      if (this.expiresAt > 0) {
        localStorage.setItem(EXP_KEY, String(this.expiresAt))
      } else {
        localStorage.removeItem(EXP_KEY)
      }
    },
    /**
     * 命令式过期判定（BUG-004）：Date.now() 非响应式，塞进 getter 会被 Pinia 缓存旧值，
     * 故做成 action 供路由守卫与 401 处理器按需调用；无过期信息（0）时不主动判过期，交后端 401 兜底。
     */
    hasExpired(): boolean {
      return this.expiresAt > 0 && this.expiresAt * 1000 <= Date.now()
    },
    logout() {
      this.token = ''
      this.user = null
      this.expiresAt = 0
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(EXP_KEY)
    },
  },
})
