<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  PRESCRIPTION_STATUS_LABELS,
  PRESCRIPTION_STATUS_TAG,
  prescriptionApi,
  type Prescription,
  type Review,
} from '@/api/prescription'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const prescriptionId = computed(() => Number(route.params.id))
const loading = ref(true)
const acting = ref(false)
const prescription = ref<Prescription | null>(null)
const reviews = ref<Review[]>([])

const canReview = computed(() => prescription.value?.status === 'PENDING_REVIEW')
const totalAmount = computed(() =>
  Number(prescription.value?.totalAmount ?? 0).toFixed(2),
)

async function load() {
  loading.value = true
  try {
    prescription.value = await prescriptionApi.detail(prescriptionId.value)
    try {
      reviews.value = await prescriptionApi.reviews(prescriptionId.value)
    } catch {
      // 审核记录接口由 R4-B 提供，未就绪时忽略
      reviews.value = []
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '处方加载失败')
  } finally {
    loading.value = false
  }
}

async function approve() {
  let comment = ''
  try {
    const result = await ElMessageBox.prompt('审核意见（至少 2 个字）', '审核通过', {
      confirmButtonText: '通过并签名',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：用法用量适宜，审核通过',
      inputValidator: (value) => (value && value.trim().length >= 2) || '至少填写 2 个字',
    })
    comment = result.value.trim()
  } catch {
    return
  }

  acting.value = true
  try {
    await prescriptionApi.approve(prescriptionId.value, { comment })
    ElMessage.success('已审核通过')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核失败')
  } finally {
    acting.value = false
  }
}

async function reject() {
  let comment = ''
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因（不少于 10 个字）', '驳回处方', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：单次剂量超过说明书上限，请调整后重新提交',
      inputValidator: (value) => (value && value.trim().length >= 10) || '驳回原因不少于 10 个字',
    })
    comment = result.value.trim()
  } catch {
    return
  }

  acting.value = true
  try {
    await prescriptionApi.reject(prescriptionId.value, { comment })
    ElMessage.success('已驳回，医生可修改后重新提交')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '驳回失败')
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="review" v-loading="loading">
    <el-button link type="primary" class="review__back" @click="router.push('/pharmacist/reviews')">
      ← 返回待审队列
    </el-button>

    <template v-if="prescription">
      <el-card shadow="never">
        <template #header>
          <div class="review__head">
            <div>
              <strong>{{ prescription.rxNo || prescription.id }}</strong>
              <span class="review__meta">
                诊断：{{ prescription.diagnosis }} · 版本 v{{ prescription.version }} ·
                {{ formatDateTime(prescription.updatedAt) }}
              </span>
            </div>
            <el-tag :type="PRESCRIPTION_STATUS_TAG[prescription.status]" effect="light">
              {{ PRESCRIPTION_STATUS_LABELS[prescription.status] }}
            </el-tag>
          </div>
        </template>

        <div class="review__meta-row">
          <span>患者 #{{ prescription.patientId }}</span>
          <span>医生 #{{ prescription.doctorId }}</span>
          <span>会话 #{{ prescription.consultationId }}</span>
          <span>金额 ¥{{ totalAmount }}</span>
        </div>

        <el-table :data="prescription.items" row-key="drugCode" class="review__items">
          <el-table-column prop="drugName" label="药品" min-width="180">
            <template #default="{ row }">
              <div class="review__drug">
                <strong>{{ row.drugName }}</strong>
                <small>{{ row.specification }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="dosage" label="单次剂量" width="110" />
          <el-table-column prop="frequency" label="频次" width="120" />
          <el-table-column prop="duration" label="疗程" width="100" />
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column label="单价" width="90">
            <template #default="{ row }">¥{{ Number(row.unitPrice ?? 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="小计" width="100">
            <template #default="{ row }">¥{{ Number(row.subtotal ?? 0).toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <div class="review__total">合计：<strong>¥{{ totalAmount }}</strong></div>
      </el-card>

      <el-card shadow="never">
        <template #header>审核记录</template>
        <el-empty v-if="!reviews.length" description="暂无审核记录" :image-size="70" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="item in reviews"
            :key="item.id"
            :timestamp="formatDateTime(item.signedAt)"
            :type="item.action === 'APPROVED' ? 'success' : 'danger'"
          >
            <strong>{{ item.action === 'APPROVED' ? '审核通过' : '驳回' }}</strong>
            <span class="review__comment">药师 #{{ item.pharmacistId }}：{{ item.comment }}</span>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <div v-if="canReview" class="review__actions">
        <el-button type="success" :loading="acting" @click="approve">审核通过</el-button>
        <el-button type="danger" plain :loading="acting" @click="reject">驳回</el-button>
      </div>
      <el-alert
        v-else
        type="info"
        show-icon
        :closable="false"
        title="该处方不在待审核状态，仅可查看。"
      />
    </template>

    <el-card v-else shadow="never">
      <el-empty description="处方不存在或无权访问">
        <el-button type="primary" @click="router.push('/pharmacist/reviews')">返回队列</el-button>
      </el-empty>
    </el-card>
  </section>
</template>

<style scoped>
.review {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review__back {
  align-self: flex-start;
}

.review__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review__meta {
  margin-left: 10px;
  color: var(--mp-text-secondary);
  font-size: 13px;
}

.review__meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
  color: var(--mp-text-secondary);
  font-size: 13px;
}

.review__drug {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.review__drug small {
  color: var(--mp-text-secondary);
}

.review__total {
  margin-top: 12px;
  text-align: right;
  color: var(--mp-text-secondary);
}

.review__total strong {
  color: var(--mp-color-primary);
  font-size: 18px;
}

.review__comment {
  margin-left: 8px;
  color: var(--mp-text-secondary);
}

.review__actions {
  display: flex;
  gap: 8px;
}
</style>
