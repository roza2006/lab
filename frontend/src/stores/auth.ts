import { defineStore } from 'pinia'

/** 与后端 `sys_role` 的 `role_code` 保持一致 */
export type Role = 'PATIENT' | 'DOCTOR' | 'PHARMACIST' | 'ADMIN'

export const ROLE_LABELS: Record<Role, string> = {
  PATIENT: '患者',
  DOCTOR: '医生',
  PHARMACIST: '药师',
  ADMIN: '管理员',
}

export interface UserInfo {
  id: string
  name: string
  role: Role
}

const TOKEN_KEY = 'mp.token'
const USER_KEY = 'mp.user'

function readUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    user: readUser(),
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    role: (state): Role | null => state.user?.role ?? null,
    displayName: (state) => state.user?.name ?? '未登录',
  },
  actions: {
    /**
     * R2 骨架阶段的本地模拟登录：后端 `/api/v1/auth/login` 由 R3-A 实现。
     * 接入真实接口时，把这里替换为 http 请求 + 保存后端返回的 token 即可，
     * 路由守卫、菜单与页面无需改动。
     */
    loginAs(role: Role, username: string) {
      this.token = `dev-mock-${role.toLowerCase()}-${Date.now()}`
      this.user = {
        id: `dev-${role.toLowerCase()}`,
        name: username.trim() || ROLE_LABELS[role],
        role,
      }
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
