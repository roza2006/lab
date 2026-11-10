<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import {
  consultationApi,
  SESSION_STATUS_LABELS,
  SESSION_STATUS_TAG,
  type ConsultationSession,
  type SessionStatus,
} from '@/api/consultation'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(true)
const sessions = ref<ConsultationSession[]>([])
const statusFilter = ref<'' | SessionStatus>('')

const filtered = computed(() =>
  statusFilter.value
    ? sessions.value.filter((item) => item.status === statusFilter.value)
    : sessions.value,
)

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

function openSession(id: number) {
  router.push(`/patient/consultations/${id}`)
}

onMounted(load)
</script>

<template>
  <section class="list">
    <header class="list__head">
      <div>
        <h2 class="list__title">我的问诊</h2>
        <p class="list__summary">按时间倒序查看本人问诊，点击行进入会话继续沟通。</p>
      </div>
      <div class="list__actions">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable class="list__filter">
          <el-option label="待接诊" value="WAITING" />
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="已结束" value="ENDED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="router.push('/patient/consultations/new')">发起问诊</el-button>
      </div>
    </header>

    <el-card shadow="never">
      <el-skeleton v-if="loading" :rows="4" animated />
      <el-empty v-else-if="!filtered.length" description="没有符合条件的问诊记录" />
      <el-table v-else :data="filtered" row-key="id" @row-click="(row: ConsultationSession) => openSession(row.id)">
        <el-table-column prop="sessionNo" label="会话号" width="150">
          <template #default="{ row }">{{ row.sessionNo || row.id }}</template>
        </el-table-column>
        <el-table-column prop="doctorName" label="医生" width="110">
          <template #default="{ row }">{{ row.doctorName || '待分配' }}</template>
        </el-table-column>
        <el-table-column prop="departmentName" label="科室" width="90" />
        <el-table-column prop="chiefComplaint" label="主诉" min-width="220" show-overflow-tooltip />
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
.list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.list__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.list__title {
  margin: 0;
  font-size: 20px;
  color: var(--mp-color-primary);
}

.list__summary {
  margin: 6px 0 0;
  color: var(--mp-text-secondary);
}

.list__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.list__filter {
  width: 140px;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
