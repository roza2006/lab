import { defineStore } from 'pinia'

import { authApi } from '@/api/auth'

/** 与后端 `sys_role.role_code` 保持一致 */
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
  role: Role
}

const TOKEN_KEY = 'mp.token'
const USER_KEY = 'mp.user'

/** 后端还存在 PHARMACY（合作药房）角色，v1 复用药师工作台 */
const ROLE_ALIASES: Record<string, Role> = {
  PATIENT: 'PATIENT',
  DOCTOR: 'DOCTOR',
  PHARMACIST: 'PHARMACIST',
  PHARMACY: 'PHARMACIST',
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
    displayName: (state) => state.user?.username ?? '未登录',
  },
  actions: {
    /**
     * 真实登录：POST /api/v1/auth/login（R3-A 已实现）。
     * 后端只返回 accessToken 与 user{id,username,roleCode}，角色由 roleCode 映射。
     */
    async login(username: string, password: string) {
      const result = await authApi.login(username.trim(), password)
      this.token = result.accessToken
      this.user = {
        id: result.user.id,
        username: result.user.username,
        role: mapRole(result.user.roleCode),
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
