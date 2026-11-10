<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="发起图文问诊"
    use-case="UC-P03"
    summary="选择科室与医生，填写主诉（不超过 1000 字）并上传图片/PDF，校验通过后创建会话并生成候诊号；R3 接入 POST /api/v1/consultations。"
    :blocks="['科室与医生', '主诉与资料上传', '提交与候诊号']"
  />
</template>
