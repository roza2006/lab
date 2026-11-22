import { http, request } from './http'

/**
 * 处方流转与核销接口。
 *
 * R5 契约（`docs/design/api-contract.md` 尚未定义流转端点，先按《代码实现方案》§5 收敛）：
 *   GET  /circulations                药房流转单列表 / 患者处方流转记录
 *   POST /circulations/{id}/accept    药房接方
 *   POST /circulations/{id}/reject    药房拒接
 *   POST /circulations/{id}/redeem    身份核验与核销（强制 Idempotency-Key）
 *   GET  /prescriptions/{id}/trace    处方全链路追踪
 *
 * R5-B 合并后若路径或字段变化，只改本文件的适配层。
 */

export type CirculationStatus = 'CREATED' | 'ACCEPTED' | 'REDEEMED' | 'REJECTED'

export interface CirculationItem {
  drugName: string
  dosage: string
  frequency: string
  duration: string
  quantity: number
}

export interface CirculationPrescription {
  id: number
  rxNo?: string
  diagnosis?: string
  patientId?: number
  totalAmount?: number
  items?: CirculationItem[]
}

export interface DispenseRecord {
  id: number
  batchNo: string
  evidence?: string
  redeemedAt: string
}

export interface CirculationOrder {
  id: number
  circulationNo: string
  rxId: number
  pharmacyId: number
  pharmacyName?: string
  status: CirculationStatus
  idempotencyKey?: string
  circulatedAt?: string
  acceptedAt?: string
  redeemedAt?: string
  rejectReason?: string
  version: number
  patientName?: string
  prescription?: CirculationPrescription
  dispenseRecord?: DispenseRecord
}

export interface TraceEvent {
  status: string
  at: string
  actor?: string
  note?: string
}

export interface PrescriptionTrace {
  prescriptionId: number
  status: string
  events: TraceEvent[]
}

export interface RedeemPayload {
  batchNo: string
  evidence?: string
}

export const CIRCULATION_STATUS_LABELS: Record<CirculationStatus, string> = {
  CREATED: '待接方',
  ACCEPTED: '待核销',
  REDEEMED: '已核销',
  REJECTED: '已拒接',
}

export const CIRCULATION_STATUS_TAG: Record<
  CirculationStatus,
  'warning' | 'primary' | 'success' | 'danger'
> = {
  CREATED: 'warning',
  ACCEPTED: 'primary',
  REDEEMED: 'success',
  REJECTED: 'danger',
}

type ListResponse<T> = T[] | { content: T[] }

function unwrapList<T>(data: ListResponse<T>): T[] {
  return Array.isArray(data) ? data : data.content
}

export const circulationApi = {
  list: async (status?: CirculationStatus) => {
    const data = await http.get<ListResponse<CirculationOrder>>('/circulations', { status })
    return unwrapList(data)
  },

  accept: (id: number | string, idempotencyKey: string) =>
    request<CirculationOrder>(`/circulations/${id}/accept`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
    }),

  reject: (id: number | string, reason: string, idempotencyKey: string) =>
    request<CirculationOrder>(`/circulations/${id}/reject`, {
      method: 'POST',
      body: { reason },
      headers: { 'Idempotency-Key': idempotencyKey },
    }),

  redeem: (id: number | string, payload: RedeemPayload, idempotencyKey: string) =>
    request<CirculationOrder>(`/circulations/${id}/redeem`, {
      method: 'POST',
      body: payload,
      headers: { 'Idempotency-Key': idempotencyKey },
    }),

  trace: (prescriptionId: number | string) =>
    http.get<PrescriptionTrace>(`/prescriptions/${prescriptionId}/trace`),
}

/** 生成幂等键：同一动作重试时必须复用同一个 key */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
