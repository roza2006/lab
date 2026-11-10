import { http } from './http'

/** 后端 `UserView` / 契约 `User`；兼容当前实现的 roleCode 与契约的 role */
export interface BackendUser {
  id: number
  username: string
  realName?: string
  role?: string
  roleCode?: string
  department?: string
  status?: string
}

/** 契约 `LoginResponse`；后端当前实现返回同样的四字段 */
export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: BackendUser
}

export const authApi = {
  /** POST /api/v1/auth/login */
  login: (username: string, password: string) =>
    http.post<LoginResponse>('/auth/login', { username, password }),

  /** GET /api/v1/auth/me */
  me: () => http.get<BackendUser>('/auth/me'),
}
