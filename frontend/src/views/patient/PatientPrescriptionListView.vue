<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import {
  PRESCRIPTION_STATUS_LABELS,
  PRESCRIPTION_STATUS_TAG,
  prescriptionApi,
  type Prescription,
  type PrescriptionStatus,
} from '@/api/prescription'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(true)
const prescriptions = ref<Prescription[]>([])
const statusFilter = ref<'' | PrescriptionStatus>('')

const filtered = computed(() =>
  statusFilter.value
    ? prescriptions.value.filter((item) => item.status === statusFilter.value)
    : prescriptions.value,
)

async function load() {
  loading.value = true
  try {
    const list = await prescriptionApi.list({ size: 50 })
    // 医生尚未提交的草稿不对患者展示
    prescriptions.value = list.filter((item) => item.status !== 'DRAFT')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '处方列表加载失败')
  } finally {
    loading.value = false
  }
}

function openDetail(id: number) {
  router.push(`/patient/prescriptions/${id}`)
}

onMounted(load)
</script>

<template>
  <section class="rx-list">
    <header class="rx-list__head">
      <div>
        <h2 class="rx-list__title">我的处方</h2>
        <p class="rx-list__summary">
          查看处方状态、药品明细与流转进度；处方通过审核后由合作药房接方、核销。
        </p>
      </div>
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="rx-list__filter">
        <el-option label="待审核" value="PENDING_REVIEW" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已流转" value="CIRCULATED" />
        <el-option label="已核销" value="REDEEMED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
    </header>

    <el-card shadow="never">
      <el-skeleton v-if="loading" :rows="4" animated />
      <el-empty v-else-if="!filtered.length" description="暂无处方记录" />
      <el-table
        v-else
        :data="filtered"
        row-key="id"
        @row-click="(row: Prescription) => openDetail(row.id)"
      >
        <el-table-column label="处方号" width="170">
          <template #default="{ row }">{{ row.rxNo || row.id }}</template>
        </el-table-column>
        <el-table-column prop="diagnosis" label="诊断" min-width="220" show-overflow-tooltip />
        <el-table-column label="药品" width="90">
          <template #default="{ row }">{{ row.items.length }} 种</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ Number(row.totalAmount ?? 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="PRESCRIPTION_STATUS_TAG[row.status as keyof typeof PRESCRIPTION_STATUS_TAG]" effect="light">
              {{ PRESCRIPTION_STATUS_LABELS[row.status as keyof typeof PRESCRIPTION_STATUS_LABELS] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开具时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row.id)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped>
.rx-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rx-list__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.rx-list__title {
  margin: 0;
  font-size: 20px;
  color: var(--mp-color-primary);
}

.rx-list__summary {
  margin: 6px 0 0;
  color: var(--mp-text-secondary);
}

.rx-list__filter {
  width: 140px;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
