import { http } from './http'

/**
 * 处方与审核接口：对齐 `docs/design/api-contract.md` v1.0 与后端 R4-A 实现。
 *
 *   POST /consultations/{id}/prescriptions   医生开方（DRAFT，201）
 *   PUT  /prescriptions/{id}                 修改草稿/驳回态
 *   POST /prescriptions/{id}/submit          提交审核
 *   POST /prescriptions/{id}/cancel          撤销
 *   GET  /prescriptions                      列表（分页，按角色过滤）
 *   GET  /prescriptions/{id}                 详情
 *   GET  /prescriptions/review-queue         药师待审队列
 *   GET  /prescriptions/{id}/reviews         审核记录
 *   POST /prescriptions/{id}/reviews/approve 审核通过
 *   POST /prescriptions/{id}/reviews/reject  驳回
 */

export type PrescriptionStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'REJECTED'
  | 'APPROVED'
  | 'CIRCULATED'
  | 'REDEEMED'
  | 'CANCELLED'
  | 'EXPIRED'

export type ReviewAction = 'APPROVED' | 'REJECTED'

export interface PrescriptionItem {
  id?: number
  drugCode: string
  drugName: string
  specification?: string
  dosage: string
  frequency: string
  duration: string
  quantity: number
  unitPrice?: number
  subtotal?: number
}

export interface Prescription {
  id: number
  rxNo?: string
  consultationId: number
  patientId: number
  doctorId: number
  diagnosis: string
  status: PrescriptionStatus
  items: PrescriptionItem[]
  totalAmount?: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface PrescriptionItemInput {
  drugCode: string
  drugName: string
  specification?: string
  dosage: string
  frequency: string
  duration: string
  quantity: number
  unitPrice?: number
}

export interface PrescriptionSavePayload {
  diagnosis: string
  items: PrescriptionItemInput[]
}

export interface Review {
  id: number
  prescriptionId: number
  pharmacistId: number
  action: ReviewAction
  comment: string
  signature?: string
  signedAt: string
}

export interface ReviewPayload {
  comment: string
}

/** 后端 `PrescriptionPageView` 与契约 `PageResponse` 的并集 */
export interface PrescriptionPage {
  content: Prescription[]
  page: number
  size: number
  total?: number
  totalElements?: number
  totalPages?: number
  last?: boolean
}

export const PRESCRIPTION_STATUS_LABELS: Record<PrescriptionStatus, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待审核',
  REJECTED: '已驳回',
  APPROVED: '已通过',
  CIRCULATED: '已流转',
  REDEEMED: '已核销',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
}

export const PRESCRIPTION_STATUS_TAG: Record<
  PrescriptionStatus,
  'info' | 'warning' | 'danger' | 'success' | 'primary'
> = {
  DRAFT: 'info',
  PENDING_REVIEW: 'warning',
  REJECTED: 'danger',
  APPROVED: 'success',
  CIRCULATED: 'primary',
  REDEEMED: 'success',
  CANCELLED: 'info',
  EXPIRED: 'info',
}

type ListResponse<T> = T[] | { content: T[] }

function unwrapList<T>(data: ListResponse<T>): T[] {
  return Array.isArray(data) ? data : data.content
}

export const prescriptionApi = {
  list: async (params: { status?: PrescriptionStatus; page?: number; size?: number } = {}) => {
    const data = await http.get<ListResponse<Prescription>>('/prescriptions', {
      status: params.status,
      page: params.page ?? 0,
      size: params.size ?? 50,
    })
    return unwrapList(data)
  },

  reviewQueue: async (params: { page?: number; size?: number } = {}) => {
    const data = await http.get<ListResponse<Prescription>>('/prescriptions/review-queue', {
      page: params.page ?? 0,
      size: params.size ?? 50,
    })
    return unwrapList(data)
  },

  detail: (id: number | string) => http.get<Prescription>(`/prescriptions/${id}`),

  create: (consultationId: number | string, payload: PrescriptionSavePayload) =>
    http.post<Prescription>(`/consultations/${consultationId}/prescriptions`, payload),

  update: (id: number | string, payload: PrescriptionSavePayload) =>
    http.put<Prescription>(`/prescriptions/${id}`, payload),

  submit: (id: number | string) => http.post<Prescription>(`/prescriptions/${id}/submit`),

  cancel: (id: number | string) => http.post<Prescription>(`/prescriptions/${id}/cancel`),

  reviews: (id: number | string) => http.get<Review[]>(`/prescriptions/${id}/reviews`),

  approve: (id: number | string, payload: ReviewPayload) =>
    http.post<Review>(`/prescriptions/${id}/reviews/approve`, payload),

  reject: (id: number | string, payload: ReviewPayload) =>
    http.post<Review>(`/prescriptions/${id}/reviews/reject`, payload),
}

/** 药品目录演示数据；R4 尚未接目录服务，联调前先用于开方页 */
export const DRUG_CATALOG = [
  { drugCode: 'DRUG001', drugName: '盐酸二甲双胍片', specification: '0.5g*20 片', unitPrice: 15 },
  { drugCode: 'DRUG002', drugName: '阿卡波糖片', specification: '50mg*30 片', unitPrice: 28.5 },
  { drugCode: 'DRUG003', drugName: '苯磺酸氨氯地平片', specification: '5mg*28 片', unitPrice: 22 },
  { drugCode: 'DRUG004', drugName: '阿莫西林胶囊', specification: '0.25g*24 粒', unitPrice: 9.8 },
  { drugCode: 'DRUG005', drugName: '布洛芬缓释胶囊', specification: '0.3g*20 粒', unitPrice: 18.6 },
] as const
