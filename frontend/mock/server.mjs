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
