import { defineStore } from 'pinia'

import { authApi } from '@/api/auth'

/** 与后端 `sys_role.role_code` / 契约 Role 枚举保持一致 */
export type Role = 'PATIENT' | 'DOCTOR' | 'PHARMACIST' | 'ADMIN'

export const ROLE_LABELS: Record<Role, string> = {
  PATIENT: '患者',
  DOCTOR: '医生',
  PHARMACIST: '药师',
  ADMIN: '管理员',
}

export interface UserInfo {
  id: number
  username: string
  realName?: string
  role: Role
}

const TOKEN_KEY = 'mp.token'
const USER_KEY = 'mp.user'

/** 合作药房操作员（PHARMACY_OPERATOR）复用药师工作台 */
const ROLE_ALIASES: Record<string, Role> = {
  PATIENT: 'PATIENT',
  DOCTOR: 'DOCTOR',
  PHARMACIST: 'PHARMACIST',
  PHARMACY: 'PHARMACIST',
  PHARMACY_OPERATOR: 'PHARMACIST',
  ADMIN: 'ADMIN',
}

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

function mapRole(roleCode: string): Role {
  const role = ROLE_ALIASES[roleCode?.toUpperCase?.() ?? '']
  if (!role) {
    throw new Error(`账号角色“${roleCode}”无前端入口，请联系管理员`)
  }
  return role
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    user: readUser(),
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    role: (state): Role | null => state.user?.role ?? null,
    displayName: (state) => state.user?.realName || state.user?.username || '未登录',
  },
  actions: {
    /**
     * POST /api/v1/auth/login（契约 v1.0）。
     * 后端当前返回 user.roleCode，契约定义 user.role，这里两者都兼容。
     */
    async login(username: string, password: string) {
      const result = await authApi.login(username.trim(), password)
      const backendUser = result.user
      this.token = result.accessToken
      this.user = {
        id: backendUser.id,
        username: backendUser.username,
        realName: backendUser.realName,
        role: mapRole(backendUser.role ?? backendUser.roleCode ?? ''),
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
