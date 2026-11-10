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
const draft = ref('')
const messageListRef = ref<HTMLElement | null>(null)

const closed = computed(
  () => session.value?.status === 'ENDED' || session.value?.status === 'ARCHIVED',
)
const canSend = computed(() => session.value?.status === 'IN_PROGRESS')
const statusLabel = computed(() =>
  session.value ? SESSION_STATUS_LABELS[session.value.status] : '',
)
const statusTag = computed(() =>
  session.value ? SESSION_STATUS_TAG[session.value.status] : 'info',
)
const doctorLabel = computed(() =>
  session.value?.doctorId ? `医生 #${session.value.doctorId}` : '待分配医生',
)

let pollTimer: number | undefined

async function loadDetail() {
  session.value = await consultationApi.detail(sessionId.value)
}

async function loadMessages() {
  messages.value = await consultationApi.messages(sessionId.value)
  await scrollToBottom()
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

async function scrollToBottom() {
  await nextTick()
  const element = messageListRef.value
  if (element) {
    element.scrollTop = element.scrollHeight
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
  <section class="chat" v-loading="loading">
    <el-button link type="primary" class="chat__back" @click="router.push('/patient/consultations')">
      ← 返回我的问诊
    </el-button>

    <el-card v-if="session" shadow="never" class="chat__card">
      <template #header>
        <div class="chat__title">
          <div>
            <strong>{{ doctorLabel }}</strong>
            <span class="chat__meta">
              {{ session.department }} · {{ session.diseaseName }} ·
              {{ formatDateTime(session.createdAt) }}
            </span>
          </div>
          <div class="chat__actions">
            <el-tag :type="statusTag" effect="light">{{ statusLabel }}</el-tag>
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

      <div class="chat__complaint">
        <span class="chat__complaint-label">主诉</span>
        <p class="chat__complaint-text">{{ session.chiefComplaint }}</p>
      </div>

      <div ref="messageListRef" class="chat__messages">
        <el-empty
          v-if="!messages.length"
          description="暂无消息，医生接诊后即可开始沟通"
          :image-size="80"
        />
        <div
          v-for="message in messages"
          :key="message.id"
          :class="['msg', message.senderRole === 'PATIENT' ? 'msg--self' : 'msg--other']"
        >
          <div class="msg__meta">
            {{ message.senderRole === 'PATIENT' ? '我' : '医生' }} ·
            {{ formatDateTime(message.sentAt) }}
          </div>
          <div v-if="message.contentType === 'IMAGE'" class="msg__bubble">[图片] {{ message.content }}</div>
          <div v-else class="msg__bubble">{{ message.content }}</div>
        </div>
      </div>

      <el-alert
        v-if="session.status === 'WAITING'"
        type="warning"
        show-icon
        :closable="false"
        title="已进入候诊队列，医生接诊后即可开始沟通。"
        class="chat__waiting"
      />

      <div class="chat__composer">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          :disabled="!canSend"
          placeholder="输入消息，Enter 发送，Shift + Enter 换行"
          @keydown.enter="onEnter"
        />
        <div class="chat__composer-actions">
          <span class="chat__hint">
            {{
              closed
                ? '本次问诊已结束'
                : session.status === 'WAITING'
                  ? '医生接诊后即可发送消息'
                  : '医生回复可能稍有延迟，可稍等刷新'
            }}
          </span>
          <el-button type="primary" :loading="sending" :disabled="!canSend" @click="send">
            发送
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card v-else shadow="never">
      <el-empty description="会话不存在，或您无权访问">
        <el-button type="primary" @click="router.push('/patient/consultations')">
          返回我的问诊
        </el-button>
      </el-empty>
    </el-card>
  </section>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.chat__back {
  align-self: flex-start;
}

.chat__card {
  display: flex;
  flex-direction: column;
}

.chat__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.chat__meta {
  margin-left: 10px;
  color: var(--mp-text-secondary);
  font-size: 13px;
}

.chat__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat__complaint {
  padding: 12px 14px;
  border-radius: 8px;
  background: #f7f9fc;
  border: 1px solid var(--mp-color-border);
}

.chat__complaint-label {
  font-size: 12px;
  color: var(--mp-text-secondary);
}

.chat__complaint-text {
  margin: 6px 0 0;
  line-height: 1.7;
}

.chat__messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 380px;
  min-height: 220px;
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

.chat__waiting {
  margin-top: 8px;
}

.chat__composer {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--mp-color-border);
}

.chat__composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.chat__hint {
  font-size: 12px;
  color: var(--mp-text-secondary);
}
</style>
