<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { consultationApi, DEPARTMENT_OPTIONS } from '@/api/consultation'

const router = useRouter()

const submitting = ref(false)
const form = reactive({
  department: '',
  diseaseName: '',
  chiefComplaint: '',
})

async function handleSubmit() {
  if (!form.department) {
    ElMessage.warning('请选择就诊科室')
    return
  }
  const diseaseName = form.diseaseName.trim()
  if (!diseaseName) {
    ElMessage.warning('请填写复诊疾病名称')
    return
  }
  const chiefComplaint = form.chiefComplaint.trim()
  if (chiefComplaint.length < 5) {
    ElMessage.warning('请填写至少 5 个字的主诉')
    return
  }

  submitting.value = true
  try {
    const session = await consultationApi.create({
      department: form.department,
      diseaseName,
      chiefComplaint,
    })
    ElMessage.success('问诊已提交，进入会话等待医生接诊')
    await router.replace(`/patient/consultations/${session.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="create">
    <header class="create__head">
      <h2 class="create__title">发起图文问诊</h2>
      <el-tag type="info" effect="plain">UC-P03</el-tag>
    </header>
    <p class="create__summary">
      填写复诊疾病与主诉，提交后生成问诊会话并进入候诊队列，医生接诊后即可图文沟通。
    </p>

    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="R3 暂未接入附件上传服务，图片资料将在 R4 支持；当前可用文字补充说明。"
    />

    <el-card shadow="never">
      <el-form label-width="96px" label-position="left">
        <el-form-item label="就诊科室" required>
          <el-select v-model="form.department" placeholder="请选择科室" class="create__control">
            <el-option v-for="item in DEPARTMENT_OPTIONS" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>

        <el-form-item label="复诊疾病" required>
          <el-input
            v-model="form.diseaseName"
            maxlength="64"
            placeholder="例如：2 型糖尿病、高血压"
            class="create__control"
          />
        </el-form-item>

        <el-form-item label="主诉" required>
          <el-input
            v-model="form.chiefComplaint"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="例如：血糖控制不佳，随访复诊；近一周空腹血糖 7.8 mmol/L 左右"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交问诊</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </section>
</template>

<style scoped>
.create {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 860px;
}

.create__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.create__title {
  margin: 0;
  font-size: 20px;
  color: var(--mp-color-primary);
}

.create__summary {
  margin: 0;
  color: var(--mp-text-secondary);
}

.create__control {
  width: 280px;
}
</style>
