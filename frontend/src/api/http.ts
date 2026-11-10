/**
 * 统一请求封装。
 *
 * 后端存在两种响应风格：
 * - 统一信封 `ApiResult{code,message,data,traceId}`（约定使用）；
 * - 裸对象（当前认证模块 `LoginResponse`/`UserView` 直接返回 record）。
 * 这里同时兼容两者，等 B 的 `docs/design/api-contract.md` 冻结后再统一。
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

function parsePayload(text: string): unknown {
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

function isEnvelope(payload: unknown): payload is ApiResult<unknown> {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'code' in payload &&
    'data' in payload
  )
}

function messageOf(payload: unknown, fallback: string): string {
  if (typeof payload === 'object' && payload !== null) {
    const record = payload as Record<string, unknown>
    if (typeof record.message === 'string' && record.message) {
      return record.message
    }
    if (typeof record.error === 'string' && record.error) {
      return record.error
    }
  }
  if (typeof payload === 'string' && payload) {
    return payload
  }
  return fallback
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

  const payload = parsePayload(await response.text())

  // 令牌失效：清除本地登录态，由 main.ts 监听事件跳转登录页。
  if (response.status === 401) {
    window.dispatchEvent(new CustomEvent('mp:unauthorized'))
    throw new ApiError(401, 'TOKEN_INVALID', '登录已失效，请重新登录')
  }

  if (!response.ok) {
    throw new ApiError(
      response.status,
      isEnvelope(payload) ? payload.code : 'HTTP_ERROR',
      messageOf(payload, `请求失败（HTTP ${response.status}）`),
    )
  }

  if (isEnvelope(payload)) {
    if (payload.code !== 'OK') {
      throw new ApiError(response.status, payload.code, payload.message)
    }
    return payload.data as T
  }

  return payload as T
}

export const http = {
  get: <T>(path: string, query?: Record<string, QueryValue>) => request<T>(path, { method: 'GET', query }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
