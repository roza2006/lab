import { http, request } from './http'

/**
 * 问诊接口：对齐 `docs/design/api-contract.md` v1.0 与 `backend/src/main/resources/openapi.yaml`
 *   POST /consultations               创建会话（201）
 *   GET  /consultations               我的会话列表（分页 PageResponse）
 *   GET  /consultations/{id}          会话详情
 *   GET  /consultations/{id}/messages 消息列表（分页）
 *   POST /consultations/{id}/messages 发送消息（201，可带 Idempotency-Key）
 *   POST /consultations/{id}/end      结束会话
 */

export type SessionStatus = 'WAITING' | 'IN_PROGRESS' | 'ENDED' | 'ARCHIVED'

export type MessageType = 'TEXT' | 'IMAGE'

/** 与 openapi.yaml 的 Role 枚举一致 */
export type RoleCode = 'PATIENT' | 'DOCTOR' | 'PHARMACIST' | 'PHARMACY_OPERATOR' | 'ADMIN'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface ConsultationSession {
  id: number
  patientId: number
  doctorId?: number | null
  department: string
  diseaseName: string
  chiefComplaint: string
  status: SessionStatus
  createdAt: string
  endedAt?: string | null
}

export interface ConsultationMessage {
  id: number
  consultationId: number
  senderId: number
  senderRole: RoleCode
  contentType: MessageType
  content: string
  read?: boolean
  sentAt: string
}

export interface CreateConsultationPayload {
  department: string
  diseaseName: string
  chiefComplaint: string
}

export interface SendMessagePayload {
  contentType: MessageType
  content: string
}

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  WAITING: '待接诊',
  IN_PROGRESS: '进行中',
  ENDED: '已结束',
  ARCHIVED: '已归档',
}

export const SESSION_STATUS_TAG: Record<SessionStatus, 'warning' | 'primary' | 'info' | 'danger'> = {
  WAITING: 'warning',
  IN_PROGRESS: 'primary',
  ENDED: 'info',
  ARCHIVED: 'info',
}

/** 契约中 department 为字符串；v1 先用固定科室，R4 接目录服务 */
export const DEPARTMENT_OPTIONS = [
  '内科',
  '外科',
  '儿科',
  '皮肤科',
  '内分泌科',
  '全科',
] as const

type ListResult<T> = PageResponse<T> | T[]

function unwrapPage<T>(data: ListResult<T>): T[] {
  return Array.isArray(data) ? data : data.content
}

export const consultationApi = {
  async list(params: { status?: SessionStatus; page?: number; size?: number } = {}) {
    const data = await http.get<ListResult<ConsultationSession>>('/consultations', {
      page: params.page ?? 0,
      size: params.size ?? 50,
      status: params.status,
    })
    return unwrapPage(data)
  },

  detail: (id: number | string) => http.get<ConsultationSession>(`/consultations/${id}`),

  create: (payload: CreateConsultationPayload) =>
    http.post<ConsultationSession>('/consultations', payload),

  async messages(id: number | string, params: { page?: number; size?: number } = {}) {
    const data = await http.get<ListResult<ConsultationMessage>>(`/consultations/${id}/messages`, {
      page: params.page ?? 0,
      size: params.size ?? 100,
    })
    return unwrapPage(data)
  },

  send: (id: number | string, payload: SendMessagePayload, idempotencyKey?: string) =>
    request<ConsultationMessage>(`/consultations/${id}/messages`, {
      method: 'POST',
      body: payload,
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    }),

  end: (id: number | string) => http.post<ConsultationSession>(`/consultations/${id}/end`),
}
