<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  CIRCULATION_STATUS_LABELS,
  CIRCULATION_STATUS_TAG,
  circulationApi,
  newIdempotencyKey,
  type CirculationOrder,
  type CirculationStatus,
} from '@/api/circulation'
import { formatDateTime } from '@/utils/format'

const loading = ref(true)
const orders = ref<CirculationOrder[]>([])
const activeTab = ref<CirculationStatus>('CREATED')

const redeemDialog = reactive({
  visible: false,
  order: null as CirculationOrder | null,
  idempotencyKey: '',
  submitting: false,
  form: {
    batchNo: '',
    evidence: '',
  },
})

const counts = computed(() => {
  const base: Record<CirculationStatus, number> = { CREATED: 0, ACCEPTED: 0, REDEEMED: 0, REJECTED: 0 }
  for (const order of orders.value) {
    base[order.status] += 1
  }
  return base
})

const filtered = computed(() => orders.value.filter((order) => order.status === activeTab.value))

async function load() {
  loading.value = true
  try {
    orders.value = await circulationApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '流转单加载失败')
  } finally {
    loading.value = false
  }
}

async function accept(order: CirculationOrder) {
  try {
    await ElMessageBox.confirm(
      `确认接收处方 ${order.prescription?.rxNo || order.rxId} 的流转单？`,
      '接方确认',
      { type: 'warning', confirmButtonText: '确认接方', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await circulationApi.accept(order.id, newIdempotencyKey())
    ElMessage.success('已接方，等待患者取药')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '接方失败')
  }
}

async function reject(order: CirculationOrder) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请填写拒接原因（至少 2 个字）', '拒接流转单', {
      confirmButtonText: '确认拒接',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (value) => (value && value.trim().length >= 2) || '至少填写 2 个字',
    })
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    await circulationApi.reject(order.id, reason, newIdempotencyKey())
    ElMessage.success('已拒接，患者可改选其他药房')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '拒接失败')
  }
}

function openRedeem(order: CirculationOrder) {
  redeemDialog.order = order
  redeemDialog.idempotencyKey = newIdempotencyKey()
  redeemDialog.form.batchNo = ''
  redeemDialog.form.evidence = ''
  redeemDialog.visible = true
}

async function submitRedeem() {
  if (!redeemDialog.order) {
    return
  }
  if (!redeemDialog.form.batchNo.trim()) {
    ElMessage.warning('请填写药品批号')
    return
  }
  if (!redeemDialog.form.evidence.trim()) {
    ElMessage.warning('请核验取药码或身份凭证')
    return
  }

  redeemDialog.submitting = true
  try {
    await circulationApi.redeem(
      redeemDialog.order.id,
      {
        batchNo: redeemDialog.form.batchNo.trim(),
        evidence: redeemDialog.form.evidence.trim(),
      },
      redeemDialog.idempotencyKey,
    )
    ElMessage.success('核销成功，发药记录已生成')
    redeemDialog.visible = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '核销失败')
  } finally {
    redeemDialog.submitting = false
  }
}

onMounted(load)
</script>

<template>
  <section class="dispense">
    <header class="dispense__head">
      <div>
        <h2 class="dispense__title">药房工作台</h2>
        <p class="dispense__summary">
          接收本药房流转单，核验取药人身份后调剂发药并核销；核销带幂等键，重复提交不会重复发药。
        </p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="dispense__stats">
      <el-card v-for="item in (['CREATED', 'ACCEPTED', 'REDEEMED', 'REJECTED'] as CirculationStatus[])" :key="item" shadow="never" class="stat">
        <span class="stat__label">{{ CIRCULATION_STATUS_LABELS[item] }}</span>
        <strong class="stat__value">{{ counts[item] }}</strong>
      </el-card>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane
          v-for="item in (['CREATED', 'ACCEPTED', 'REDEEMED', 'REJECTED'] as CirculationStatus[])"
          :key="item"
          :label="`${CIRCULATION_STATUS_LABELS[item]}（${counts[item]}）`"
          :name="item"
        />
      </el-tabs>

      <el-skeleton v-if="loading" :rows="4" animated />
      <el-empty v-else-if="!filtered.length" description="暂无流转单" />
      <el-table v-else :data="filtered" row-key="id">
        <el-table-column label="流转单号" width="160">
          <template #default="{ row }">{{ row.circulationNo || row.id }}</template>
        </el-table-column>
        <el-table-column label="处方号" width="160">
          <template #default="{ row }">{{ row.prescription?.rxNo || row.rxId }}</template>
        </el-table-column>
        <el-table-column label="诊断" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.prescription?.diagnosis || '-' }}</template>
        </el-table-column>
        <el-table-column label="患者" width="90">
          <template #default="{ row }">{{ row.patientName || `#${row.prescription?.patientId ?? '-'}` }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="CIRCULATION_STATUS_TAG[row.status as keyof typeof CIRCULATION_STATUS_TAG]" effect="light">
              {{ CIRCULATION_STATUS_LABELS[row.status as keyof typeof CIRCULATION_STATUS_LABELS] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="流转时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.circulatedAt) }}</template>
        </el-table-column>
        <el-table-column label="核销信息" min-width="160">
          <template #default="{ row }">
            <span v-if="row.dispenseRecord">
              {{ row.dispenseRecord.batchNo }} · {{ formatDateTime(row.dispenseRecord.redeemedAt) }}
            </span>
            <span v-else-if="row.rejectReason" class="dispense__muted">{{ row.rejectReason }}</span>
            <span v-else class="dispense__muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <template v-if="row.status === 'CREATED'">
              <el-button link type="primary" @click="accept(row)">接方</el-button>
              <el-button link type="danger" @click="reject(row)">拒接</el-button>
            </template>
            <el-button v-else-if="row.status === 'ACCEPTED'" link type="primary" @click="openRedeem(row)">
              核销
            </el-button>
            <span v-else class="dispense__muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="redeemDialog.visible" title="核销发药" width="460px">
      <el-form label-width="90px" label-position="left">
        <el-form-item label="处方号">
          <span>{{ redeemDialog.order?.prescription?.rxNo || redeemDialog.order?.rxId }}</span>
        </el-form-item>
        <el-form-item label="药品批号" required>
          <el-input v-model="redeemDialog.form.batchNo" placeholder="例如：B20261122001" />
        </el-form-item>
        <el-form-item label="取药凭证" required>
          <el-input v-model="redeemDialog.form.evidence" placeholder="取药码或身份核验流水号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="redeemDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="redeemDialog.submitting" @click="submitRedeem">
          确认核销
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.dispense {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dispense__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.dispense__title {
  margin: 0;
  font-size: 20px;
  color: var(--mp-color-primary);
}

.dispense__summary {
  margin: 6px 0 0;
  color: var(--mp-text-secondary);
}

.dispense__stats {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
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

.dispense__muted {
  color: var(--mp-text-secondary);
}
</style>
