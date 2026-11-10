#!/usr/bin/env node
/**
 * 零依赖本地 mock 服务（R3 演示/联调前使用）。
 *
 *   node mock/server.mjs        # 默认 http://127.0.0.1:8080
 *
 * 与 Vite 的 /api 代理对接；真实后端就绪后无需启动本服务。
 * 演示账号：patient / doctor / pharmacist / admin，密码均为 123456。
 * 这里只实现 R3 患者端用到的接口，字段与前端 `src/api/*.ts` 保持一致。
 */
import { createServer } from 'node:http'

const PORT = Number(process.env.MOCK_PORT || 8080)

const USERS = [
  { id: 1, username: 'patient', password: '123456', roleCode: 'PATIENT' },
  { id: 2, username: 'doctor', password: '123456', roleCode: 'DOCTOR' },
  { id: 3, username: 'pharmacist', password: '123456', roleCode: 'PHARMACIST' },
  { id: 4, username: 'admin', password: '123456', roleCode: 'ADMIN' },
]

const DOCTOR_NAMES = { 2: '李医生' }
const DEPARTMENT_NAMES = { 1: '内科', 2: '外科', 3: '儿科', 4: '皮肤科' }

const iso = (offsetMinutes = 0) =>
  new Date(Date.now() + offsetMinutes * 60_000).toISOString()

let sequence = 100
const nextId = () => (sequence += 1)

const sessions = [
  {
    id: 1,
    sessionNo: 'S20261110001',
    patientId: 1,
    patientName: '张小明',
    doctorId: 2,
    doctorName: '李医生',
    departmentId: 1,
    departmentName: '内科',
    status: 'IN_PROGRESS',
    chiefComplaint: '反复咳嗽三天，伴低热，无呼吸困难',
    description: '无药物过敏史，已服用两次止咳糖浆。',
    createdAt: iso(-120),
    startedAt: iso(-110),
  },
  {
    id: 2,
    sessionNo: 'S20261110002',
    patientId: 1,
    patientName: '张小明',
    departmentId: 4,
    departmentName: '皮肤科',
    status: 'WAITING',
    chiefComplaint: '手臂出现红色皮疹，偶有瘙痒',
    description: '两天前开始，未使用新化妆品。',
    createdAt: iso(-30),
  },
  {
    id: 3,
    sessionNo: 'S20261109007',
    patientId: 1,
    patientName: '张小明',
    doctorId: 2,
    doctorName: '李医生',
    departmentId: 1,
    departmentName: '内科',
    status: 'ENDED',
    chiefComplaint: '咽痛两天，吞咽时加重',
    createdAt: iso(-1440),
    startedAt: iso(-1435),
    endedAt: iso(-1400),
  },
]

const messages = [
  {
    id: 1,
    sessionId: 1,
    senderId: 0,
    senderRole: 'SYSTEM',
    senderName: '系统',
    msgType: 'SYSTEM',
    content: '问诊已创建，医生已接诊。',
    sentAt: iso(-120),
  },
  {
    id: 2,
    sessionId: 1,
    senderId: 2,
    senderRole: 'DOCTOR',
    senderName: '李医生',
    msgType: 'TEXT',
    content: '您好，我是内科李医生。请问咳嗽是干咳还是有痰？体温最高多少？',
    sentAt: iso(-108),
  },
  {
    id: 3,
    sessionId: 1,
    senderId: 1,
    senderRole: 'PATIENT',
    senderName: '张小明',
    msgType: 'TEXT',
    content: '干咳为主，偶尔有少量白痰；昨晚体温 37.8℃。',
    sentAt: iso(-100),
  },
]

const tokens = new Map()

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  })
  res.end(body)
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
        reject(new Error('invalid json'))
      }
    })
    req.on('error', reject)
  })
}

const publicUser = (user) => ({
  id: user.id,
  username: user.username,
  roleCode: user.roleCode,
})

function canAccess(user, session) {
  if (user.roleCode === 'PATIENT') return session.patientId === user.id
  if (user.roleCode === 'DOCTOR') return session.doctorId === user.id
  return user.roleCode === 'ADMIN'
}

function requireSession(res, user, id) {
  const session = sessions.find((item) => item.id === Number(id))
  if (!session) {
    sendJson(res, 404, { message: '会话不存在' })
    return null
  }
  if (!canAccess(user, session)) {
    sendJson(res, 403, { message: '无权访问该会话' })
    return null
  }
  return session
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
        sendJson(res, 401, { message: '用户名或密码错误' })
        return
      }
      const token = `mock-${user.username}-${Date.now()}`
      tokens.set(token, user)
      sendJson(res, 200, {
        accessToken: token,
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: publicUser(user),
      })
      return
    }

    const user = userOf(req)
    if (!user) {
      sendJson(res, 401, { message: '未登录或登录已失效' })
      return
    }

    if (req.method === 'GET' && path === '/api/v1/auth/me') {
      sendJson(res, 200, publicUser(user))
      return
    }

    if (req.method === 'GET' && path === '/api/v1/consultations') {
      const mine = sessions
        .filter((item) => canAccess(user, item) && item.status !== 'CANCELLED')
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      sendJson(res, 200, mine)
      return
    }

    if (req.method === 'POST' && path === '/api/v1/consultations') {
      const body = await readBody(req)
      if (!body.chiefComplaint || String(body.chiefComplaint).length < 5) {
        sendJson(res, 400, { message: '主诉至少 5 个字' })
        return
      }
      const departmentId = Number(body.departmentId)
      const session = {
        id: nextId(),
        sessionNo: `S${Date.now()}`,
        patientId: user.id,
        patientName: user.username,
        departmentId,
        departmentName: DEPARTMENT_NAMES[departmentId] || '全科',
        status: 'WAITING',
        chiefComplaint: String(body.chiefComplaint),
        description: body.description ? String(body.description) : undefined,
        createdAt: iso(),
      }
      sessions.push(session)
      messages.push({
        id: nextId(),
        sessionId: session.id,
        senderId: 0,
        senderRole: 'SYSTEM',
        senderName: '系统',
        msgType: 'SYSTEM',
        content: '问诊已提交，正在等待医生接诊。',
        sentAt: iso(),
      })
      sendJson(res, 200, session)
      return
    }

    const detailMatch = path.match(/^\/api\/v1\/consultations\/(\d+)$/)
    if (req.method === 'GET' && detailMatch) {
      const session = requireSession(res, user, detailMatch[1])
      if (session) sendJson(res, 200, session)
      return
    }

    const messageMatch = path.match(/^\/api\/v1\/consultations\/(\d+)\/messages$/)
    if (messageMatch) {
      const session = requireSession(res, user, messageMatch[1])
      if (!session) return

      if (req.method === 'GET') {
        const list = messages
          .filter((item) => item.sessionId === session.id)
          .sort((a, b) => a.id - b.id)
        sendJson(res, 200, list)
        return
      }

      if (req.method === 'POST') {
        if (session.status === 'ENDED' || session.status === 'CANCELLED') {
          sendJson(res, 409, { message: '会话已结束，不能继续发送消息' })
          return
        }
        const body = await readBody(req)
        const content = String(body.content || '').trim()
        if (!content) {
          sendJson(res, 400, { message: '消息内容不能为空' })
          return
        }
        const message = {
          id: nextId(),
          sessionId: session.id,
          senderId: user.id,
          senderRole: user.roleCode,
          senderName: user.username === 'patient' ? '张小明' : user.username,
          msgType: body.msgType || 'TEXT',
          content,
          sentAt: iso(),
        }
        messages.push(message)
        sendJson(res, 200, message)
        return
      }
    }

    const endMatch = path.match(/^\/api\/v1\/consultations\/(\d+)\/end$/)
    if (req.method === 'POST' && endMatch) {
      const session = requireSession(res, user, endMatch[1])
      if (!session) return
      if (session.status === 'ENDED') {
        sendJson(res, 200, session)
        return
      }
      session.status = 'ENDED'
      session.endedAt = iso()
      messages.push({
        id: nextId(),
        sessionId: session.id,
        senderId: 0,
        senderRole: 'SYSTEM',
        senderName: '系统',
        msgType: 'SYSTEM',
        content: '问诊已结束，感谢您的使用。',
        sentAt: iso(),
      })
      sendJson(res, 200, session)
      return
    }

    sendJson(res, 404, { message: `未实现的接口：${req.method} ${path}` })
  } catch (error) {
    sendJson(res, 500, { message: error instanceof Error ? error.message : 'mock 服务异常' })
  }
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[mock] listening on http://127.0.0.1:${PORT}`)
  console.log('[mock] demo accounts: patient / doctor / pharmacist / admin，密码 123456')
})
