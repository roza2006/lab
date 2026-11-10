import { http } from './http'

/**
 * 问诊接口约定（R3 前端先行版）。
 *
 * 路径沿用 `docs/requirements/use-cases.md` §3/§7：
 *   POST /consultations               创建会话
 *   GET  /consultations               我的会话列表
 *   GET  /consultations/{id}          会话详情
 *   GET  /consultations/{id}/messages 消息列表
 *   POST /consultations/{id}/messages 发送消息
 *   POST /consultations/{id}/end      结束问诊
 *
 * B 的 `docs/design/api-contract.md` 合并后，如字段命名不同，只改本文件的适配层。
 */

export type SessionStatus = 'WAITING' | 'IN_PROGRESS' | 'ENDED' | 'CANCELLED'

export type MessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM'

export interface ConsultationSession {
  id: number
  sessionNo?: string
  patientId: number
  patientName?: string
  doctorId?: number
  doctorName?: string
  departmentId?: number
  departmentName?: string
  status: SessionStatus
  chiefComplaint: string
  description?: string
  createdAt: string
  startedAt?: string
  endedAt?: string
}

export interface ConsultationMessage {
  id: number
  sessionId: number
  senderId: number
  senderRole: 'PATIENT' | 'DOCTOR' | 'SYSTEM'
  senderName?: string
  msgType: MessageType
  content: string
  sentAt: string
}

export interface CreateConsultationPayload {
  departmentId: number
  doctorId?: number
  chiefComplaint: string
  description?: string
}

export interface SendMessagePayload {
  content: string
  clientMsgId: string
  msgType?: MessageType
}

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  WAITING: '待接诊',
  IN_PROGRESS: '进行中',
  ENDED: '已结束',
  CANCELLED: '已取消',
}

export const SESSION_STATUS_TAG: Record<SessionStatus, 'warning' | 'primary' | 'info' | 'danger'> = {
  WAITING: 'warning',
  IN_PROGRESS: 'primary',
  ENDED: 'info',
  CANCELLED: 'danger',
}

export const DEPARTMENT_OPTIONS = [
  { value: 1, label: '内科' },
  { value: 2, label: '外科' },
  { value: 3, label: '儿科' },
  { value: 4, label: '皮肤科' },
] as const

type ListResponse = ConsultationSession[] | { items: ConsultationSession[] }

export const consultationApi = {
  async list(): Promise<ConsultationSession[]> {
    const data = await http.get<ListResponse>('/consultations')
    return Array.isArray(data) ? data : data.items
  },

  detail: (id: number | string) => http.get<ConsultationSession>(`/consultations/${id}`),

  create: (payload: CreateConsultationPayload) =>
    http.post<ConsultationSession>('/consultations', payload),

  messages: (id: number | string) =>
    http.get<ConsultationMessage[]>(`/consultations/${id}/messages`),

  send: (id: number | string, payload: SendMessagePayload) =>
    http.post<ConsultationMessage>(`/consultations/${id}/messages`, payload),

  end: (id: number | string) => http.post<ConsultationSession>(`/consultations/${id}/end`, {}),
}
