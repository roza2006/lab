/**
 * 统一请求封装：对应后端 `ApiResult{code,message,data,traceId}`。
 * R2 阶段仅提供基础设施，具体接口在 R3–R5 按 `docs/design/api-contract.md` 接入。
 */

export interface ApiResult<T> {
  code: string
  message: string
  data: T
  traceId?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

type QueryValue = string | number | boolean | undefined | null

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  query?: Record<string, QueryValue>
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
const TOKEN_KEY = 'mp.token'

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = new URL(`${BASE_URL}${path}`, window.location.origin)
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value))
      }
    }
  }
  return url.toString()
}

function parsePayload<T>(text: string): ApiResult<T> | null {
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text) as ApiResult<T>
  } catch {
    return null
  }
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, query, headers, ...rest } = options
  const token = localStorage.getItem(TOKEN_KEY)

  const response = await fetch(buildUrl(path, query), {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  // 令牌失效：清除本地登录态，由布局层监听事件跳转登录页（R3 接入）。
  if (response.status === 401) {
    window.dispatchEvent(new CustomEvent('mp:unauthorized'))
    throw new ApiError(401, 'TOKEN_INVALID', '登录已失效，请重新登录')
  }

  const payload = parsePayload<T>(await response.text())
  if (!response.ok) {
    throw new ApiError(
      response.status,
      payload?.code ?? 'HTTP_ERROR',
      payload?.message ?? `请求失败（HTTP ${response.status}）`,
    )
  }
  if (payload?.code && payload.code !== 'OK') {
    throw new ApiError(response.status, payload.code, payload.message)
  }
  return (payload?.data ?? null) as T
}

export const http = {
  get: <T>(path: string, query?: Record<string, QueryValue>) => request<T>(path, { method: 'GET', query }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
