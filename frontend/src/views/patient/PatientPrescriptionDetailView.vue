<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import {
  circulationApi,
  type PrescriptionTrace,
  type TraceEvent,
} from '@/api/circulation'
import {
  PRESCRIPTION_STATUS_LABELS,
  PRESCRIPTION_STATUS_TAG,
  prescriptionApi,
  type Prescription,
} from '@/api/prescription'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const prescriptionId = computed(() => Number(route.params.id))
const loading = ref(true)
const prescription = ref<Prescription | null>(null)
const trace = ref<PrescriptionTrace | null>(null)

const totalAmount = computed(() =>
  Number(prescription.value?.totalAmount ?? 0).toFixed(2),
)

/** 追溯接口未就绪时，用处方本身的关键时间点兜底 */
const events = computed<TraceEvent[]>(() => {
  if (trace.value?.events?.length) {
    return trace.value.events
  }
  const item = prescription.value
  if (!item) {
    return []
  }
  const list: TraceEvent[] = [{ status: 'DRAFT', at: item.createdAt, note: '处方已创建' }]
  const pushed = new Set(['DRAFT'])
  const add = (status: string, note: string) => {
    if (!pushed.has(status)) {
      pushed.add(status)
      list.push({ status, at: item.updatedAt, note })
    }
  }
  if (['PENDING_REVIEW', 'APPROVED', 'REJECTED', 'CIRCULATED', 'REDEEMED', 'EXPIRED'].includes(item.status)) {
    add('PENDING_REVIEW', '已提交药师审核')
  }
  if (['APPROVED', 'CIRCULATED', 'REDEEMED'].includes(item.status)) {
    add('APPROVED', '药师审核通过')
  }
  if (item.status === 'REJECTED') {
    add('REJECTED', '药师审核驳回')
  }
  if (['CIRCULATED', 'REDEEMED'].includes(item.status)) {
    add('CIRCULATED', '处方已流转至合作药房')
  }
  if (item.status === 'REDEEMED') {
    add('REDEEMED', '药房已核销发药')
  }
  return list
})

const eventType = (status: string): 'success' | 'warning' | 'danger' | 'primary' | 'info' => {
  if (status === 'REJECTED' || status === 'CANCELLED' || status === 'EXPIRED') return 'danger'
  if (status === 'REDEEMED' || status === 'APPROVED') return 'success'
  if (status === 'CIRCULATED') return 'primary'
  if (status === 'PENDING_REVIEW') return 'warning'
  return 'info'
}

const eventLabel = (status: string): string =>
  PRESCRIPTION_STATUS_LABELS[status as keyof typeof PRESCRIPTION_STATUS_LABELS] ?? status

async function load() {
  loading.value = true
  try {
    prescription.value = await prescriptionApi.detail(prescriptionId.value)
    try {
      trace.value = await circulationApi.trace(prescriptionId.value)
    } catch {
      trace.value = null
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '处方加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="rx-detail" v-loading="loading">
    <el-button link type="primary" class="rx-detail__back" @click="router.push('/patient/prescriptions')">
      ← 返回我的处方
    </el-button>

    <template v-if="prescription">
      <el-card shadow="never">
        <template #header>
          <div class="rx-detail__head">
            <div>
              <strong>{{ prescription.rxNo || prescription.id }}</strong>
              <span class="rx-detail__meta">
                诊断：{{ prescription.diagnosis }} · 开具时间：{{ formatDateTime(prescription.createdAt) }}
              </span>
            </div>
            <el-tag :type="PRESCRIPTION_STATUS_TAG[prescription.status]" effect="light">
              {{ PRESCRIPTION_STATUS_LABELS[prescription.status] }}
            </el-tag>
          </div>
        </template>

        <el-table :data="prescription.items" row-key="drugCode">
          <el-table-column prop="drugName" label="药品" min-width="180">
            <template #default="{ row }">
              <div class="rx-detail__drug">
                <strong>{{ row.drugName }}</strong>
                <small>{{ row.specification }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="dosage" label="单次剂量" width="110" />
          <el-table-column prop="frequency" label="频次" width="120" />
          <el-table-column prop="duration" label="疗程" width="100" />
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column label="小计" width="100">
            <template #default="{ row }">¥{{ Number(row.subtotal ?? 0).toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <div class="rx-detail__total">合计：<strong>¥{{ totalAmount }}</strong></div>
        <p class="rx-detail__tip">
          用药请遵医嘱；如出现不适，请及时联系开方医生或就近就医。
        </p>
      </el-card>

      <el-card shadow="never">
        <template #header>流转追踪</template>
        <el-empty v-if="!events.length" description="暂无流转记录" :image-size="70" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="(event, index) in events"
            :key="`${event.status}-${index}`"
            :timestamp="formatDateTime(event.at)"
            :type="eventType(event.status)"
          >
            <strong>{{ event.note || eventLabel(event.status) }}</strong>
            <span v-if="event.actor" class="rx-detail__actor">{{ event.actor }}</span>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </template>

    <el-card v-else shadow="never">
      <el-empty description="处方不存在或无权访问">
        <el-button type="primary" @click="router.push('/patient/prescriptions')">
          返回我的处方
        </el-button>
      </el-empty>
    </el-card>
  </section>
</template>

<style scoped>
.rx-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 960px;
}

.rx-detail__back {
  align-self: flex-start;
}

.rx-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.rx-detail__meta {
  margin-left: 10px;
  color: var(--mp-text-secondary);
  font-size: 13px;
}

.rx-detail__drug {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.rx-detail__drug small {
  color: var(--mp-text-secondary);
}

.rx-detail__total {
  margin-top: 12px;
  text-align: right;
  color: var(--mp-text-secondary);
}

.rx-detail__total strong {
  color: var(--mp-color-primary);
  font-size: 18px;
}

.rx-detail__tip {
  margin: 10px 0 0;
  color: var(--mp-text-secondary);
  font-size: 13px;
}

.rx-detail__actor {
  margin-left: 8px;
  color: var(--mp-text-secondary);
  font-size: 12px;
}
</style>
