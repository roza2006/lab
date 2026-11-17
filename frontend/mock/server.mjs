#!/usr/bin/env node
/**
 * 零依赖本地 mock 服务（R3 演示/联调前使用）。
 *
 *   node mock/server.mjs                 # 默认 http://127.0.0.1:8080
 *   MOCK_PORT=18080 node mock/server.mjs # 端口被占用时改端口，并用 VITE_PROXY_TARGET 指向它
 *
 * 字段与 `docs/design/api-contract.md` v1.0 / `openapi.yaml` 对齐：
 *   POST /auth/login
 *   GET  /auth/me
 *   GET/POST /consultations
 *   GET  /consultations/{id}
 *   GET/POST /consultations/{id}/messages
 *   POST /consultations/{id}/end
 *
 * 演示账号：patient / doctor / pharmacist / admin，密码均为 123456。
 */
import { createServer } from 'node:http'

const PORT = Number(process.env.MOCK_PORT || 8080)

const USERS = [
  { id: 1002, username: 'patient', password: '123456', role: 'PATIENT', realName: '张小明' },
  { id: 1001, username: 'doctor', password: '123456', role: 'DOCTOR', realName: '李医生' },
  { id: 1003, username: 'pharmacist', password: '123456', role: 'PHARMACIST', realName: '王药师' },
  { id: 1004, username: 'admin', password: '123456', role: 'ADMIN', realName: '管理员' },
]

const iso = (offsetMinutes = 0) => new Date(Date.now() + offsetMinutes * 60_000).toISOString()

let sequence = 3000
const nextId = () => (sequence += 1)

const consultations = [
  {
    id: 2001,
    patientId: 1002,
    doctorId: 1001,
    department: '内分泌科',
    diseaseName: '2 型糖尿病',
    chiefComplaint: '血糖控制不佳，随访复诊；近一周空腹血糖 7.8 mmol/L 左右',
    status: 'IN_PROGRESS',
    createdAt: iso(-120),
    endedAt: null,
  },
  {
    id: 2002,
    patientId: 1002,
    doctorId: null,
    department: '皮肤科',
    diseaseName: '湿疹',
    chiefComplaint: '手臂出现红色皮疹，偶有瘙痒，两天前开始',
    status: 'WAITING',
    createdAt: iso(-30),
    endedAt: null,
  },
  {
    id: 2003,
    patientId: 1002,
    doctorId: 1001,
    department: '内科',
    diseaseName: '急性咽炎',
    chiefComplaint: '咽痛两天，吞咽时加重，无发热',
    status: 'ENDED',
    createdAt: iso(-1440),
    endedAt: iso(-1400),
  },
  {
    id: 2004,
    patientId: 1002,
    doctorId: 1001,
    department: '心血管内科',
    diseaseName: '原发性高血压',
    chiefComplaint: '血压控制不佳，晨起 150/95 mmHg，复诊调整用药',
    status: 'IN_PROGRESS',
    createdAt: iso(-60),
    endedAt: null,
  },
]

const messages = [
  {
    id: 3001,
    consultationId: 2001,
    senderId: 1001,
    senderRole: 'DOCTOR',
    contentType: 'TEXT',
    content: '您好，我是内分泌科李医生。请问最近一次糖化血红蛋白是多少？目前用药方案是什么？',
    read: false,
    sentAt: iso(-108),
  },
  {
    id: 3002,
    consultationId: 2001,
    senderId: 1002,
    senderRole: 'PATIENT',
    contentType: 'TEXT',
    content: '上个月糖化 8.1%，现在每天二甲双胍 0.5g，早晚各一次。',
    read: true,
    sentAt: iso(-100),
  },
]

const prescriptions = [
  {
    id: 4001,
    rxNo: 'RX202611160001',
    consultationId: 2001,
    patientId: 1002,
    doctorId: 1001,
    diagnosis: '2 型糖尿病（血糖控制不佳）',
    status: 'PENDING_REVIEW',
    items: [
      {
        id: 5001,
        drugCode: 'DRUG001',
        drugName: '盐酸二甲双胍片',
        specification: '0.5g*20 片',
        dosage: '0.5g',
        frequency: '每日 2 次',
        duration: '30 天',
        quantity: 2,
        unitPrice: 15,
        subtotal: 30,
      },
    ],
    totalAmount: 30,
    version: 1,
    createdAt: iso(-90),
    updatedAt: iso(-80),
  },
  {
    id: 4002,
    rxNo: 'RX202611160002',
    consultationId: 2004,
    patientId: 1002,
    doctorId: 1001,
    diagnosis: '原发性高血压 2 级',
    status: 'DRAFT',
    items: [
      {
        id: 5002,
        drugCode: 'DRUG003',
        drugName: '苯磺酸氨氯地平片',
        specification: '5mg*28 片',
        dosage: '5mg',
        frequency: '每日 1 次',
        duration: '28 天',
        quantity: 1,
        unitPrice: 22,
        subtotal: 22,
      },
    ],
    totalAmount: 22,
    version: 1,
    createdAt: iso(-50),
    updatedAt: iso(-40),
  },
]

const reviews = []

function canAccessPrescription(user, prescription) {
  if (user.role === 'PATIENT') return prescription.patientId === user.id
  if (user.role === 'DOCTOR') return prescription.doctorId === user.id
  return true
}

function normalizePrescriptionBody(body) {
  const diagnosis = String(body.diagnosis || '').trim()
  if (!diagnosis) {
    return { error: '诊断不能为空' }
  }
  const rawItems = Array.isArray(body.items) ? body.items : []
  if (!rawItems.length || rawItems.length > 5) {
    return { error: '处方至少 1 条、最多 5 条药品明细' }
  }
  const items = []
  for (const item of rawItems) {
    const drugCode = String(item.drugCode || '').trim()
    const drugName = String(item.drugName || '').trim()
    const dosage = String(item.dosage || '').trim()
    const frequency = String(item.frequency || '').trim()
    const duration = String(item.duration || '').trim()
    const quantity = Number(item.quantity)
    const unitPrice = Number(item.unitPrice || 0)
    if (!drugCode || !drugName || !dosage || !frequency || !duration || quantity < 1) {
      return { error: '药品明细字段不完整，数量必须大于 0' }
    }
    const id = nextId()
    items.push({
      id,
      drugCode,
      drugName,
      specification: String(item.specification || ''),
      dosage,
      frequency,
      duration,
      quantity,
      unitPrice,
      subtotal: Number((quantity * unitPrice).toFixed(2)),
    })
  }
  const totalAmount = Number(items.reduce((sum, item) => sum + item.subtotal, 0).toFixed(2))
  return { diagnosis, items, totalAmount }
}

const tokens = new Map()

function sendJson(res, status, payload) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, Idempotency-Key',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  })
  res.end(JSON.stringify(payload))
}

function userOf(req) {
  const header = req.headers.authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : ''
  return tokens.get(token) || null
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = ''
    req.on('data', (chunk) => {
      raw += chunk
      if (raw.length > 1_000_000) {
        reject(new Error('payload too large'))
        req.destroy()
      }
    })
    req.on('end', () => {
      if (!raw) {
        resolve({})
        return
      }
      try {
        resolve(JSON.parse(raw))
      } catch {
        reject(new Error('请求体不是合法 JSON'))
      }
    })
    req.on('error', reject)
  })
}

const publicUser = (user) => ({
  id: user.id,
  username: user.username,
  realName: user.realName,
  role: user.role,
  status: 'ACTIVE',
})

function canAccess(user, consultation) {
  if (user.role === 'PATIENT') return consultation.patientId === user.id
  if (user.role === 'DOCTOR') return consultation.doctorId === user.id
  return user.role === 'ADMIN'
}

function pageOf(items, url) {
  const page = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 20)
  const start = page * size
  const content = items.slice(start, start + size)
  return {
    content,
    page,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
    last: start + size >= items.length,
  }
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url || '/', `http://127.0.0.1:${PORT}`)
  const path = url.pathname

  if (req.method === 'OPTIONS') {
    sendJson(res, 204, {})
    return
  }

  try {
    if (req.method === 'POST' && path === '/api/v1/auth/login') {
      const { username, password } = await readBody(req)
      const user = USERS.find((item) => item.username === username && item.password === password)
      if (!user) {
        sendJson(res, 401, { code: 'UNAUTHORIZED', message: '用户名或密码错误' })
        return
      }
      const token = `mock-${user.username}-${Date.now()}`
      tokens.set(token, user)
      sendJson(res, 200, {
        accessToken: token,
        tokenType: 'Bearer',
        expiresIn: 7200,
        user: publicUser(user),
      })
      return
    }

    const user = userOf(req)
    if (!user) {
      sendJson(res, 401, { code: 'UNAUTHORIZED', message: '未登录或登录已失效' })
      return
    }

    if (req.method === 'GET' && path === '/api/v1/auth/me') {
      sendJson(res, 200, publicUser(user))
      return
    }

    if (path === '/api/v1/consultations') {
      if (req.method === 'GET') {
        const status = url.searchParams.get('status')
        const mine = consultations
          .filter((item) => canAccess(user, item))
          .filter((item) => (status ? item.status === status : true))
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        sendJson(res, 200, pageOf(mine, url))
        return
      }

      if (req.method === 'POST') {
        const body = await readBody(req)
        const department = String(body.department || '').trim()
        const diseaseName = String(body.diseaseName || '').trim()
        const chiefComplaint = String(body.chiefComplaint || '').trim()
        if (!department || !diseaseName || chiefComplaint.length < 5) {
          sendJson(res, 422, {
            code: 'VALIDATION_FAILED',
            message: '科室、复诊疾病必填，主诉至少 5 个字',
          })
          return
        }
        const consultation = {
          id: nextId(),
          patientId: user.id,
          doctorId: null,
          department,
          diseaseName,
          chiefComplaint,
          status: 'WAITING',
          createdAt: iso(),
          endedAt: null,
        }
        consultations.push(consultation)
        sendJson(res, 201, consultation)
        return
      }
    }

    const detailMatch = path.match(/^\/api\/v1\/consultations\/(\d+)$/)
    if (req.method === 'GET' && detailMatch) {
      const consultation = consultations.find((item) => item.id === Number(detailMatch[1]))
      if (!consultation) {
        sendJson(res, 404, { code: 'CONSULTATION_NOT_FOUND', message: '会话不存在' })
        return
      }
      if (!canAccess(user, consultation)) {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '无权访问该会话' })
        return
      }
      sendJson(res, 200, consultation)
      return
    }

    const messageMatch = path.match(/^\/api\/v1\/consultations\/(\d+)\/messages$/)
    if (messageMatch) {
      const consultation = consultations.find((item) => item.id === Number(messageMatch[1]))
      if (!consultation) {
        sendJson(res, 404, { code: 'CONSULTATION_NOT_FOUND', message: '会话不存在' })
        return
      }
      if (!canAccess(user, consultation)) {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '无权访问该会话' })
        return
      }

      if (req.method === 'GET') {
        const list = messages
          .filter((item) => item.consultationId === consultation.id)
          .sort((a, b) => a.id - b.id)
        sendJson(res, 200, pageOf(list, url))
        return
      }

      if (req.method === 'POST') {
        if (consultation.status !== 'IN_PROGRESS') {
          sendJson(res, 409, { code: 'ILLEGAL_STATE', message: '会话不在进行中状态，不能发送消息' })
          return
        }
        const body = await readBody(req)
        const contentType = body.contentType === 'IMAGE' ? 'IMAGE' : 'TEXT'
        const content = String(body.content || '').trim()
        if (!content) {
          sendJson(res, 422, { code: 'VALIDATION_FAILED', message: '消息内容不能为空' })
          return
        }
        const message = {
          id: nextId(),
          consultationId: consultation.id,
          senderId: user.id,
          senderRole: user.role,
          contentType,
          content,
          read: false,
          sentAt: iso(),
        }
        messages.push(message)
        sendJson(res, 201, message)
        return
      }
    }

    const endMatch = path.match(/^\/api\/v1\/consultations\/(\d+)\/end$/)
    if (req.method === 'POST' && endMatch) {
      const consultation = consultations.find((item) => item.id === Number(endMatch[1]))
      if (!consultation) {
        sendJson(res, 404, { code: 'CONSULTATION_NOT_FOUND', message: '会话不存在' })
        return
      }
      if (!canAccess(user, consultation)) {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '无权访问该会话' })
        return
      }
      if (consultation.status === 'ENDED' || consultation.status === 'ARCHIVED') {
        sendJson(res, 409, { code: 'ILLEGAL_STATE', message: '会话已结束' })
        return
      }
      if (consultation.status !== 'IN_PROGRESS') {
        sendJson(res, 409, { code: 'ILLEGAL_STATE', message: '医生接诊后才能结束会话' })
        return
      }
      consultation.status = 'ENDED'
      consultation.endedAt = iso()
      sendJson(res, 200, consultation)
      return
    }

    // ---------- 处方 ----------
    if (path === '/api/v1/prescriptions' && req.method === 'GET') {
      const status = url.searchParams.get('status')
      const mine = prescriptions
        .filter((item) => canAccessPrescription(user, item))
        .filter((item) => (status ? item.status === status : true))
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      sendJson(res, 200, pageOf(mine, url))
      return
    }

    if (path === '/api/v1/prescriptions/review-queue' && req.method === 'GET') {
      if (!['PHARMACIST', 'ADMIN'].includes(user.role)) {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '仅药师可查看待审队列' })
        return
      }
      const pending = prescriptions
        .filter((item) => item.status === 'PENDING_REVIEW')
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      sendJson(res, 200, pageOf(pending, url))
      return
    }

    const rxCreateMatch = path.match(/^\/api\/v1\/consultations\/(\d+)\/prescriptions$/)
    if (req.method === 'POST' && rxCreateMatch) {
      if (user.role !== 'DOCTOR') {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '仅医生可开具处方' })
        return
      }
      const consultation = consultations.find((item) => item.id === Number(rxCreateMatch[1]))
      if (!consultation) {
        sendJson(res, 404, { code: 'CONSULTATION_NOT_FOUND', message: '会话不存在' })
        return
      }
      if (consultation.doctorId !== user.id) {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '只能为本人接诊的会话开方' })
        return
      }
      if (consultation.status !== 'IN_PROGRESS') {
        sendJson(res, 409, { code: 'ILLEGAL_STATE', message: '会话不在进行中状态，不能开方' })
        return
      }
      const normalized = normalizePrescriptionBody(await readBody(req))
      if (normalized.error) {
        sendJson(res, 422, { code: 'VALIDATION_FAILED', message: normalized.error })
        return
      }
      const id = nextId()
      const prescription = {
        id,
        rxNo: `RX${new Date().toISOString().slice(0, 10).replaceAll('-', '')}${id}`,
        consultationId: consultation.id,
        patientId: consultation.patientId,
        doctorId: user.id,
        diagnosis: normalized.diagnosis,
        status: 'DRAFT',
        items: normalized.items,
        totalAmount: normalized.totalAmount,
        version: 1,
        createdAt: iso(),
        updatedAt: iso(),
      }
      prescriptions.push(prescription)
      sendJson(res, 201, prescription)
      return
    }

    const rxActionMatch = path.match(/^\/api\/v1\/prescriptions\/(\d+)\/(submit|cancel)$/)
    if (req.method === 'POST' && rxActionMatch) {
      const prescription = prescriptions.find((item) => item.id === Number(rxActionMatch[1]))
      if (!prescription) {
        sendJson(res, 404, { code: 'PRESCRIPTION_NOT_FOUND', message: '处方不存在' })
        return
      }
      if (prescription.doctorId !== user.id) {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '只能操作本人开具的处方' })
        return
      }
      if (rxActionMatch[2] === 'submit') {
        if (prescription.status !== 'DRAFT') {
          sendJson(res, 409, { code: 'ILLEGAL_STATE', message: '仅草稿可提交审核' })
          return
        }
        prescription.status = 'PENDING_REVIEW'
      } else {
        if (!['DRAFT', 'PENDING_REVIEW'].includes(prescription.status)) {
          sendJson(res, 409, { code: 'ILLEGAL_STATE', message: '当前状态不可撤销' })
          return
        }
        prescription.status = 'CANCELLED'
      }
      prescription.version += 1
      prescription.updatedAt = iso()
      sendJson(res, 200, prescription)
      return
    }

    const rxReviewsMatch = path.match(/^\/api\/v1\/prescriptions\/(\d+)\/reviews$/)
    if (req.method === 'GET' && rxReviewsMatch) {
      const prescription = prescriptions.find((item) => item.id === Number(rxReviewsMatch[1]))
      if (!prescription) {
        sendJson(res, 404, { code: 'PRESCRIPTION_NOT_FOUND', message: '处方不存在' })
        return
      }
      if (!canAccessPrescription(user, prescription)) {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '无权查看该处方' })
        return
      }
      sendJson(
        res,
        200,
        reviews.filter((item) => item.prescriptionId === prescription.id),
      )
      return
    }

    const rxReviewActionMatch = path.match(/^\/api\/v1\/prescriptions\/(\d+)\/reviews\/(approve|reject)$/)
    if (req.method === 'POST' && rxReviewActionMatch) {
      if (!['PHARMACIST', 'ADMIN'].includes(user.role)) {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '仅药师可审核处方' })
        return
      }
      const prescription = prescriptions.find((item) => item.id === Number(rxReviewActionMatch[1]))
      if (!prescription) {
        sendJson(res, 404, { code: 'PRESCRIPTION_NOT_FOUND', message: '处方不存在' })
        return
      }
      if (prescription.status !== 'PENDING_REVIEW') {
        sendJson(res, 409, { code: 'ILLEGAL_STATE', message: '处方不在待审核状态' })
        return
      }
      const body = await readBody(req)
      const comment = String(body.comment || '').trim()
      if (comment.length < 2) {
        sendJson(res, 422, { code: 'VALIDATION_FAILED', message: '审核意见至少 2 个字' })
        return
      }
      const action = rxReviewActionMatch[2] === 'approve' ? 'APPROVED' : 'REJECTED'
      prescription.status = action
      prescription.version += 1
      prescription.updatedAt = iso()
      const review = {
        id: nextId(),
        prescriptionId: prescription.id,
        pharmacistId: user.id,
        action,
        comment,
        signature: `SIG-MOCK-${prescription.id}-${Date.now()}`,
        signedAt: iso(),
      }
      reviews.push(review)
      sendJson(res, 200, review)
      return
    }

    const rxDetailMatch = path.match(/^\/api\/v1\/prescriptions\/(\d+)$/)
    if (rxDetailMatch) {
      const prescription = prescriptions.find((item) => item.id === Number(rxDetailMatch[1]))
      if (!prescription) {
        sendJson(res, 404, { code: 'PRESCRIPTION_NOT_FOUND', message: '处方不存在' })
        return
      }
      if (!canAccessPrescription(user, prescription)) {
        sendJson(res, 403, { code: 'FORBIDDEN', message: '无权访问该处方' })
        return
      }

      if (req.method === 'GET') {
        sendJson(res, 200, prescription)
        return
      }

      if (req.method === 'PUT') {
        if (prescription.doctorId !== user.id) {
          sendJson(res, 403, { code: 'FORBIDDEN', message: '只能修改本人开具的处方' })
          return
        }
        if (!['DRAFT', 'REJECTED'].includes(prescription.status)) {
          sendJson(res, 409, { code: 'ILLEGAL_STATE', message: '仅草稿或驳回态可修改' })
          return
        }
        const normalized = normalizePrescriptionBody(await readBody(req))
        if (normalized.error) {
          sendJson(res, 422, { code: 'VALIDATION_FAILED', message: normalized.error })
          return
        }
        prescription.diagnosis = normalized.diagnosis
        prescription.items = normalized.items
        prescription.totalAmount = normalized.totalAmount
        prescription.status = 'DRAFT'
        prescription.version += 1
        prescription.updatedAt = iso()
        sendJson(res, 200, prescription)
        return
      }
    }

    sendJson(res, 404, { code: 'NOT_FOUND', message: `未实现的接口：${req.method} ${path}` })
  } catch (error) {
    sendJson(res, 500, {
      code: 'INTERNAL_ERROR',
      message: error instanceof Error ? error.message : 'mock 服务异常',
    })
  }
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[mock] listening on http://127.0.0.1:${PORT}`)
  console.log('[mock] accounts: patient / doctor / pharmacist / admin，密码 123456')
})
