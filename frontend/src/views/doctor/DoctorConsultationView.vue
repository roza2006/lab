<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  consultationApi,
  SESSION_STATUS_LABELS,
  SESSION_STATUS_TAG,
  type ConsultationMessage,
  type ConsultationSession,
} from '@/api/consultation'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const sessionId = computed(() => Number(route.params.id))
const session = ref<ConsultationSession | null>(null)
const messages = ref<ConsultationMessage[]>([])
const loading = ref(true)
const sending = ref(false)
const accepting = ref(false)
const draft = ref('')
const messageListRef = ref<HTMLElement | null>(null)

const waiting = computed(() => session.value?.status === 'WAITING')
const closed = computed(
  () => session.value?.status === 'ENDED' || session.value?.status === 'ARCHIVED',
)
const canSend = computed(() => session.value?.status === 'IN_PROGRESS')

let pollTimer: number | undefined

async function loadDetail() {
  session.value = await consultationApi.detail(sessionId.value)
}

async function loadMessages() {
  messages.value = await consultationApi.messages(sessionId.value)
  await nextTick()
  const element = messageListRef.value
  if (element) {
    element.scrollTop = element.scrollHeight
  }
}

async function refresh(silent = false) {
  try {
    await Promise.all([loadDetail(), loadMessages()])
  } catch (error) {
    if (!silent) {
      ElMessage.error(error instanceof Error ? error.message : '会话加载失败')
    }
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => {
    if (!closed.value) {
      void refresh(true)
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

function idempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function accept() {
  accepting.value = true
  try {
    await consultationApi.accept(sessionId.value)
    ElMessage.success('已接诊，可以开始沟通')
    await refresh()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '接诊失败')
  } finally {
    accepting.value = false
  }
}

async function send() {
  const content = draft.value.trim()
  if (!content || !canSend.value || sending.value) {
    return
  }
  sending.value = true
  try {
    await consultationApi.send(
      sessionId.value,
      { contentType: 'TEXT', content },
      idempotencyKey(),
    )
    draft.value = ''
    await loadMessages()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

function onEnter(event: KeyboardEvent) {
  if (event.shiftKey) {
    return
  }
  event.preventDefault()
  void send()
}

async function endConsultation() {
  try {
    await ElMessageBox.confirm('结束后将不能继续发送消息，确认结束本次问诊？', '结束问诊', {
      type: 'warning',
      confirmButtonText: '确认结束',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }
  try {
    await consultationApi.end(sessionId.value)
    ElMessage.success('已结束本次问诊')
    await refresh()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

onMounted(async () => {
  try {
    await refresh()
  } finally {
    loading.value = false
    startPolling()
  }
})

onUnmounted(stopPolling)
</script>

<template>
  <section class="consult" v-loading="loading">
    <div class="consult__bar">
      <el-button link type="primary" @click="router.push('/doctor/queue')">← 返回队列</el-button>
      <el-button
        v-if="session && canSend"
        type="primary"
        plain
        @click="router.push(`/doctor/consultations/${sessionId}/prescription`)"
      >
        进入开方工作台
      </el-button>
    </div>

    <el-card v-if="session" shadow="never">
      <template #header>
        <div class="consult__head">
          <div>
            <strong>患者 #{{ session.patientId }}</strong>
            <span class="consult__meta">
              {{ session.department }} · {{ session.diseaseName }} · 发起于
              {{ formatDateTime(session.createdAt) }}
            </span>
          </div>
          <div class="consult__actions">
            <el-tag :type="SESSION_STATUS_TAG[session.status]" effect="light">
              {{ SESSION_STATUS_LABELS[session.status] }}
            </el-tag>
            <el-button
              v-if="session.status === 'IN_PROGRESS'"
              link
              type="danger"
              @click="endConsultation"
            >
              结束问诊
            </el-button>
          </div>
        </div>
      </template>

      <div class="consult__complaint">
        <span class="consult__complaint-label">主诉</span>
        <p>{{ session.chiefComplaint }}</p>
      </div>

      <div v-if="waiting" class="consult__waiting">
        <el-alert
          type="warning"
          show-icon
          :closable="false"
          title="该会话尚未接诊，接诊后才能发送消息。"
        />
        <el-button type="primary" :loading="accepting" class="consult__accept" @click="accept">
          接诊该患者
        </el-button>
      </div>

      <div ref="messageListRef" class="consult__messages">
        <el-empty
          v-if="!messages.length"
          description="暂无消息，开始与患者沟通吧"
          :image-size="80"
        />
        <div
          v-for="message in messages"
          :key="message.id"
          :class="['msg', message.senderRole === 'DOCTOR' ? 'msg--self' : 'msg--other']"
        >
          <div class="msg__meta">
            {{ message.senderRole === 'DOCTOR' ? '我' : '患者' }} ·
            {{ formatDateTime(message.sentAt) }}
          </div>
          <div class="msg__bubble">{{ message.content }}</div>
        </div>
      </div>

      <div class="consult__composer">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          :disabled="!canSend"
          placeholder="输入回复，Enter 发送，Shift + Enter 换行"
          @keydown.enter="onEnter"
        />
        <div class="consult__composer-actions">
          <span class="consult__hint">
            {{ closed ? '本次问诊已结束' : waiting ? '接诊后即可回复' : '患者回复可能稍有延迟' }}
          </span>
          <el-button type="primary" :loading="sending" :disabled="!canSend" @click="send">
            发送
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card v-else shadow="never">
      <el-empty description="会话不存在，或无权访问">
        <el-button type="primary" @click="router.push('/doctor/queue')">返回队列</el-button>
      </el-empty>
    </el-card>
  </section>
</template>

<style scoped>
.consult {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.consult__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.consult__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.consult__meta {
  margin-left: 10px;
  color: var(--mp-text-secondary);
  font-size: 13px;
}

.consult__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.consult__complaint {
  padding: 12px 14px;
  border-radius: 8px;
  background: #f7f9fc;
  border: 1px solid var(--mp-color-border);
}

.consult__complaint-label {
  font-size: 12px;
  color: var(--mp-text-secondary);
}

.consult__complaint p {
  margin: 6px 0 0;
  line-height: 1.7;
}

.consult__waiting {
  margin-top: 12px;
}

.consult__accept {
  margin-top: 8px;
}

.consult__messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 360px;
  min-height: 200px;
  margin-top: 14px;
  padding: 4px;
  overflow-y: auto;
}

.msg {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 72%;
}

.msg--self {
  align-self: flex-end;
  align-items: flex-end;
}

.msg--other {
  align-self: flex-start;
  align-items: flex-start;
}

.msg__meta {
  font-size: 12px;
  color: var(--mp-text-secondary);
}

.msg__bubble {
  padding: 9px 12px;
  border-radius: 10px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.msg--self .msg__bubble {
  background: #2e75b6;
  color: #fff;
}

.msg--other .msg__bubble {
  background: #fff;
  border: 1px solid var(--mp-color-border);
}

.consult__composer {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--mp-color-border);
}

.consult__composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.consult__hint {
  font-size: 12px;
  color: var(--mp-text-secondary);
}
</style>
