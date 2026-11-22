<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import {
  PRESCRIPTION_STATUS_LABELS,
  PRESCRIPTION_STATUS_TAG,
  prescriptionApi,
  type Prescription,
} from '@/api/prescription'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(true)
const prescriptions = ref<Prescription[]>([])

async function load() {
  loading.value = true
  try {
    prescriptions.value = await prescriptionApi.reviewQueue({ size: 50 })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '待审队列加载失败')
  } finally {
    loading.value = false
  }
}

function openReview(id: number) {
  router.push(`/pharmacist/reviews/${id}`)
}

onMounted(load)
</script>

<template>
  <section class="queue">
    <header class="queue__head">
      <div>
        <h2 class="queue__title">待审处方</h2>
        <p class="queue__summary">
          按提交时间倒序展示待审核处方；审核前请核对诊断、用法用量与禁忌，审核动作全程留痕。
        </p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-card shadow="never">
      <el-skeleton v-if="loading" :rows="4" animated />
      <el-empty v-else-if="!prescriptions.length" description="暂无待审核处方" />
      <el-table
        v-else
        :data="prescriptions"
        row-key="id"
        @row-click="(row: Prescription) => openReview(row.id)"
      >
        <el-table-column label="处方号" width="170">
          <template #default="{ row }">{{ row.rxNo || row.id }}</template>
        </el-table-column>
        <el-table-column prop="diagnosis" label="诊断" min-width="200" show-overflow-tooltip />
        <el-table-column label="药品明细" width="100">
          <template #default="{ row }">{{ row.items.length }} 条</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ Number(row.totalAmount ?? 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="患者" width="90">
          <template #default="{ row }">#{{ row.patientId }}</template>
        </el-table-column>
        <el-table-column label="医生" width="90">
          <template #default="{ row }">#{{ row.doctorId }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="PRESCRIPTION_STATUS_TAG[row.status as keyof typeof PRESCRIPTION_STATUS_TAG]" effect="light">
              {{ PRESCRIPTION_STATUS_LABELS[row.status as keyof typeof PRESCRIPTION_STATUS_LABELS] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openReview(row.id)">审核</el-button>
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

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
