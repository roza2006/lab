<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import {
  consultationApi,
  SESSION_STATUS_LABELS,
  SESSION_STATUS_TAG,
  type ConsultationSession,
} from '@/api/consultation'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const sessions = ref<ConsultationSession[]>([])

const ongoing = computed(() => sessions.value.filter((item) => item.status === 'IN_PROGRESS'))
const waiting = computed(() => sessions.value.filter((item) => item.status === 'WAITING'))
const recent = computed(() => sessions.value.slice(0, 5))

async function load() {
  loading.value = true
  try {
    sessions.value = await consultationApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '问诊列表加载失败')
  } finally {
    loading.value = false
  }
}

function doctorLabel(session: ConsultationSession) {
  return session.doctorId ? `医生 #${session.doctorId}` : '待分配'
}

function openSession(id: number) {
  router.push(`/patient/consultations/${id}`)
}

onMounted(load)
</script>

<template>
  <section class="home">
    <header class="home__head">
      <div>
        <h2 class="home__title">您好，{{ auth.displayName }}</h2>
        <p class="home__summary">汇总您的问诊进度与待办，三步内即可发起新的图文问诊。</p>
      </div>
      <el-button type="primary" @click="router.push('/patient/consultations/new')">
        发起图文问诊
      </el-button>
    </header>

    <div class="home__stats">
      <el-card shadow="never" class="stat">
        <span class="stat__label">进行中的问诊</span>
        <strong class="stat__value">{{ ongoing.length }}</strong>
      </el-card>
      <el-card shadow="never" class="stat">
        <span class="stat__label">等待医生接诊</span>
        <strong class="stat__value">{{ waiting.length }}</strong>
      </el-card>
      <el-card shadow="never" class="stat">
        <span class="stat__label">历史问诊</span>
        <strong class="stat__value">{{ sessions.length }}</strong>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>最近问诊</span>
          <el-button link type="primary" @click="router.push('/patient/consultations')">
            查看全部
          </el-button>
        </div>
      </template>

      <el-skeleton v-if="loading" :rows="3" animated />
      <el-empty v-else-if="!recent.length" description="还没有问诊记录，先发起一次图文问诊吧">
        <el-button type="primary" @click="router.push('/patient/consultations/new')">
          发起问诊
        </el-button>
      </el-empty>
      <el-table
        v-else
        :data="recent"
        row-key="id"
        @row-click="(row: ConsultationSession) => openSession(row.id)"
      >
        <el-table-column label="医生" width="100">
          <template #default="{ row }">{{ doctorLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="department" label="科室" width="90" />
        <el-table-column prop="diseaseName" label="复诊疾病" min-width="120" show-overflow-tooltip />
        <el-table-column prop="chiefComplaint" label="主诉" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="SESSION_STATUS_TAG[row.status as keyof typeof SESSION_STATUS_TAG]" effect="light">
              {{ SESSION_STATUS_LABELS[row.status as keyof typeof SESSION_STATUS_LABELS] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openSession(row.id)">进入会话</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.home__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.home__title {
  margin: 0;
  font-size: 20px;
  color: var(--mp-color-primary);
}

.home__summary {
  margin: 6px 0 0;
  color: var(--mp-text-secondary);
}

.home__stats {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat__label {
  color: var(--mp-text-secondary);
  font-size: 13px;
}

.stat__value {
  font-size: 26px;
  color: var(--mp-color-primary);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
