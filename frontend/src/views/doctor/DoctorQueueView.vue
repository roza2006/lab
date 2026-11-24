<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { consultationApi, type ConsultationSession } from '@/api/consultation'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(true)
const accepting = ref<number | null>(null)
const sessions = ref<ConsultationSession[]>([])

const sorted = computed(() =>
  [...sessions.value].sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
)

function waitMinutes(createdAt: string) {
  const minutes = Math.floor((Date.now() - new Date(createdAt).getTime()) / 60000)
  return Math.max(0, minutes)
}

async function load() {
  loading.value = true
  try {
    sessions.value = await consultationApi.list({ status: 'WAITING', size: 50 })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '待接诊队列加载失败')
  } finally {
    loading.value = false
  }
}

async function accept(session: ConsultationSession) {
  try {
    await ElMessageBox.confirm(
      `确认接诊患者 #${session.patientId} 的「${session.diseaseName}」问诊？`,
      '接诊确认',
      { type: 'warning', confirmButtonText: '确认接诊', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  accepting.value = session.id
  try {
    await consultationApi.accept(session.id)
    ElMessage.success('接诊成功，进入会话')
    await router.push(`/doctor/consultations/${session.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '接诊失败')
  } finally {
    accepting.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="queue">
    <header class="queue__head">
      <div>
        <h2 class="queue__title">待接诊队列</h2>
        <p class="queue__summary">
          按等待时长排序展示待接诊会话；接诊后进入图文会话，处理完成可开具处方。
        </p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="queue__stats">
      <el-card shadow="never" class="stat">
        <span class="stat__label">待接诊</span>
        <strong class="stat__value">{{ sessions.length }}</strong>
      </el-card>
      <el-card shadow="never" class="stat">
        <span class="stat__label">最长等待</span>
        <strong class="stat__value">
          {{ sorted.length ? `${waitMinutes(sorted[0].createdAt)} 分钟` : '-' }}
        </strong>
      </el-card>
    </div>

    <el-card shadow="never">
      <el-skeleton v-if="loading" :rows="4" animated />
      <el-empty v-else-if="!sorted.length" description="暂无待接诊会话，稍后再看看" />
      <el-table v-else :data="sorted" row-key="id">
        <el-table-column label="患者" width="90">
          <template #default="{ row }">#{{ row.patientId }}</template>
        </el-table-column>
        <el-table-column prop="department" label="科室" width="120" />
        <el-table-column prop="diseaseName" label="复诊疾病" min-width="140" show-overflow-tooltip />
        <el-table-column prop="chiefComplaint" label="主诉" min-width="240" show-overflow-tooltip />
        <el-table-column label="发起时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="已等待" width="100">
          <template #default="{ row }">{{ waitMinutes(row.createdAt) }} 分钟</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              :loading="accepting === row.id"
              @click="accept(row)"
            >
              接诊
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped>
.queue {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.queue__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.queue__title {
  margin: 0;
  font-size: 20px;
  color: var(--mp-color-primary);
}

.queue__summary {
  margin: 6px 0 0;
  color: var(--mp-text-secondary);
}

.queue__stats {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat__label {
  color: var(--mp-text-secondary);
  font-size: 13px;
}

.stat__value {
  font-size: 24px;
  color: var(--mp-color-primary);
}
</style>
