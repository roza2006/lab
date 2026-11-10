import { http } from './http'

/** 后端 `UserView`（identity 模块） */
export interface BackendUser {
  id: number
  username: string
  roleCode: string
}

/** 后端 `LoginResponse` */
export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: BackendUser
}

export const authApi = {
  /** POST /api/v1/auth/login（R3 已实现，返回裸对象） */
  login: (username: string, password: string) =>
    http.post<LoginResponse>('/auth/login', { username, password }),

  /** GET /api/v1/auth/me */
  me: () => http.get<BackendUser>('/auth/me'),
}
