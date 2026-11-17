<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { consultationApi, SESSION_STATUS_LABELS, type ConsultationSession } from '@/api/consultation'
import {
  DRUG_CATALOG,
  PRESCRIPTION_STATUS_LABELS,
  PRESCRIPTION_STATUS_TAG,
  prescriptionApi,
  type Prescription,
  type PrescriptionItem,
  type PrescriptionStatus,
} from '@/api/prescription'
import { formatDateTime } from '@/utils/format'

interface EditableItem {
  key: string
  drugCode: string
  drugName: string
  specification: string
  dosage: string
  frequency: string
  duration: string
  quantity: number
  unitPrice: number
}

const route = useRoute()
const router = useRouter()

const consultationId = computed(() => Number(route.params.id))
const loading = ref(true)
const saving = ref(false)
const submitting = ref(false)
const session = ref<ConsultationSession | null>(null)
const prescription = ref<Prescription | null>(null)
const selectedDrug = ref('')

const form = reactive({
  diagnosis: '',
  items: [] as EditableItem[],
})

const status = computed<PrescriptionStatus | null>(() => prescription.value?.status ?? null)
const editable = computed(
  () => !status.value || status.value === 'DRAFT' || status.value === 'REJECTED',
)
const sessionReady = computed(() => session.value?.status === 'IN_PROGRESS')
const totalAmount = computed(() =>
  form.items.reduce((sum, item) => sum + item.quantity * (item.unitPrice || 0), 0),
)

function newKey() {
  return `item-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function toEditable(item: PrescriptionItem): EditableItem {
  return {
    key: newKey(),
    drugCode: item.drugCode,
    drugName: item.drugName,
    specification: item.specification ?? '',
    dosage: item.dosage,
    frequency: item.frequency,
    duration: item.duration,
    quantity: item.quantity,
    unitPrice: Number(item.unitPrice ?? 0),
  }
}

function addDrug() {
  if (!selectedDrug.value) {
    return
  }
  if (form.items.length >= 5) {
    ElMessage.warning('单张处方最多 5 条明细')
    return
  }
  if (form.items.some((item) => item.drugCode === selectedDrug.value)) {
    ElMessage.warning('该药品已在处方中')
    return
  }
  const drug = DRUG_CATALOG.find((item) => item.drugCode === selectedDrug.value)
  if (!drug) {
    return
  }
  form.items.push({
    key: newKey(),
    drugCode: drug.drugCode,
    drugName: drug.drugName,
    specification: drug.specification,
    dosage: '',
    frequency: '每日 2 次',
    duration: '7 天',
    quantity: 1,
    unitPrice: drug.unitPrice,
  })
  selectedDrug.value = ''
}

function removeItem(key: string) {
  form.items = form.items.filter((item) => item.key !== key)
}

async function load() {
  loading.value = true
  try {
    session.value = await consultationApi.detail(consultationId.value)
    const mine = await prescriptionApi.list({ size: 50 })
    const existing = mine.find(
      (item) =>
        item.consultationId === consultationId.value &&
        ['DRAFT', 'REJECTED', 'PENDING_REVIEW', 'APPROVED'].includes(item.status),
    )
    if (existing) {
      prescription.value = existing
      form.diagnosis = existing.diagnosis
      form.items = existing.items.map(toEditable)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '开方数据加载失败')
  } finally {
    loading.value = false
  }
}

function validate(): boolean {
  if (!form.diagnosis.trim()) {
    ElMessage.warning('请填写诊断')
    return false
  }
  if (!form.items.length) {
    ElMessage.warning('请至少添加一条药品明细')
    return false
  }
  for (const item of form.items) {
    if (!item.dosage.trim() || !item.frequency.trim() || !item.duration.trim()) {
      ElMessage.warning(`请补全「${item.drugName}」的剂量、频次与疗程`)
      return false
    }
    if (item.quantity < 1) {
      ElMessage.warning(`「${item.drugName}」数量必须大于 0`)
      return false
    }
  }
  return true
}

async function persist(): Promise<Prescription | null> {
  if (!sessionReady.value) {
    ElMessage.warning('会话不在进行中状态，暂不能开方')
    return null
  }
  if (!validate()) {
    return null
  }
  const payload = {
    diagnosis: form.diagnosis.trim(),
    items: form.items.map((item) => ({
      drugCode: item.drugCode,
      drugName: item.drugName,
      specification: item.specification || undefined,
      dosage: item.dosage.trim(),
      frequency: item.frequency.trim(),
      duration: item.duration.trim(),
      quantity: item.quantity,
      unitPrice: item.unitPrice,
    })),
  }

  saving.value = true
  try {
    const saved = prescription.value
      ? await prescriptionApi.update(prescription.value.id, payload)
      : await prescriptionApi.create(consultationId.value, payload)
    prescription.value = saved
    form.items = saved.items.map(toEditable)
    return saved
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
    return null
  } finally {
    saving.value = false
  }
}

async function saveDraft() {
  const saved = await persist()
  if (saved) {
    ElMessage.success('草稿已保存')
  }
}

async function submit() {
  try {
    await ElMessageBox.confirm('提交后处方内容将锁定并进入药师审核，确认提交？', '提交处方', {
      type: 'warning',
      confirmButtonText: '确认提交',
      cancelButtonText: '再检查一下',
    })
  } catch {
    return
  }

  const saved = await persist()
  if (!saved) {
    return
  }
  submitting.value = true
  try {
    prescription.value = await prescriptionApi.submit(saved.id)
    ElMessage.success('已提交药师审核')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

async function cancelPrescription() {
  if (!prescription.value) {
    return
  }
  try {
    await ElMessageBox.confirm('撤销后处方不可恢复，确认撤销？', '撤销处方', {
      type: 'warning',
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    prescription.value = await prescriptionApi.cancel(prescription.value.id)
    ElMessage.success('处方已撤销')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤销失败')
  }
}

onMounted(load)
</script>

<template>
  <section class="rx" v-loading="loading">
    <header class="rx__head">
      <div>
        <h2 class="rx__title">开方工作台</h2>
        <p class="rx__summary">填写诊断与药品明细，保存草稿后提交药师审核；提交后内容锁定。</p>
      </div>
      <el-button link type="primary" @click="router.push(`/doctor/consultations/${consultationId}`)">
        ← 返回会话
      </el-button>
    </header>

    <el-alert
      v-if="session && !sessionReady"
      type="warning"
      show-icon
      :closable="false"
      title="会话不在进行中状态，需医生接诊后才能开方。"
    />

    <el-card v-if="session" shadow="never">
      <template #header>
        <div class="rx__card-head">
          <span>会话信息</span>
          <el-tag v-if="status" :type="PRESCRIPTION_STATUS_TAG[status]" effect="light">
            处方{{ PRESCRIPTION_STATUS_LABELS[status] }}
          </el-tag>
        </div>
      </template>
      <div class="rx__meta">
        <span>患者 #{{ session.patientId }}</span>
        <span>科室：{{ session.department }}</span>
        <span>复诊疾病：{{ session.diseaseName }}</span>
        <span>会话状态：{{ SESSION_STATUS_LABELS[session.status] }}</span>
        <span v-if="prescription">处方号：{{ prescription.rxNo || prescription.id }}</span>
        <span v-if="prescription">版本：v{{ prescription.version }}</span>
        <span v-if="prescription">更新：{{ formatDateTime(prescription.updatedAt) }}</span>
      </div>
      <p class="rx__complaint">主诉：{{ session.chiefComplaint }}</p>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="rx__card-head">
          <span>处方内容</span>
          <div v-if="editable">
            <el-select v-model="selectedDrug" placeholder="选择药品" class="rx__drug-select">
              <el-option
                v-for="drug in DRUG_CATALOG"
                :key="drug.drugCode"
                :label="`${drug.drugName}（${drug.specification}）`"
                :value="drug.drugCode"
              />
            </el-select>
            <el-button :disabled="!selectedDrug" @click="addDrug">添加药品</el-button>
          </div>
        </div>
      </template>

      <el-form label-width="72px" label-position="left">
        <el-form-item label="诊断" required>
          <el-input
            v-model="form.diagnosis"
            maxlength="500"
            :disabled="!editable"
            placeholder="例如：2 型糖尿病（血糖控制不佳）"
          />
        </el-form-item>
      </el-form>

      <el-table :data="form.items" row-key="key" empty-text="尚未添加药品">
        <el-table-column label="药品" min-width="200">
          <template #default="{ row }">
            <div class="rx__drug">
              <strong>{{ row.drugName }}</strong>
              <small>{{ row.specification }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单次剂量" width="130">
          <template #default="{ row }">
            <el-input v-model="row.dosage" :disabled="!editable" placeholder="0.5g" />
          </template>
        </el-table-column>
        <el-table-column label="频次" width="140">
          <template #default="{ row }">
            <el-input v-model="row.frequency" :disabled="!editable" />
          </template>
        </el-table-column>
        <el-table-column label="疗程" width="120">
          <template #default="{ row }">
            <el-input v-model="row.duration" :disabled="!editable" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="110">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="1"
              :max="99"
              :disabled="!editable"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="90">
          <template #default="{ row }">¥{{ Number(row.unitPrice).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="小计" width="100">
          <template #default="{ row }">¥{{ (row.quantity * row.unitPrice).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column v-if="editable" label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeItem(row.key)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="rx__total">
        合计：<strong>¥{{ totalAmount.toFixed(2) }}</strong>
      </div>
    </el-card>

    <div v-if="editable" class="rx__actions">
      <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交审核</el-button>
      <el-button
        v-if="prescription && ['DRAFT', 'PENDING_REVIEW'].includes(prescription.status)"
        type="danger"
        plain
        @click="cancelPrescription"
      >
        撤销处方
      </el-button>
    </div>

    <el-alert
      v-else
      type="info"
      show-icon
      :closable="false"
      title="当前处方已提交或已审核，内容已锁定；如需修改请联系药师驳回后重新开方。"
    />
  </section>
</template>

<style scoped>
.rx {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rx__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.rx__title {
  margin: 0;
  font-size: 20px;
  color: var(--mp-color-primary);
}

.rx__summary {
  margin: 6px 0 0;
  color: var(--mp-text-secondary);
}

.rx__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rx__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--mp-text-secondary);
  font-size: 13px;
}

.rx__complaint {
  margin: 10px 0 0;
  line-height: 1.7;
}

.rx__drug {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.rx__drug small {
  color: var(--mp-text-secondary);
}

.rx__drug-select {
  width: 260px;
  margin-right: 8px;
}

.rx__total {
  margin-top: 12px;
  text-align: right;
  color: var(--mp-text-secondary);
}

.rx__total strong {
  color: var(--mp-color-primary);
  font-size: 18px;
}

.rx__actions {
  display: flex;
  gap: 8px;
}
</style>
