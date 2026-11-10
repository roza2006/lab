<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="历史问诊"
    use-case="UC-D07"
    summary="查看本人接诊过的会话、病历与处方记录，支持按时间与患者检索；R3 接入查询接口。"
    :blocks="['历史会话', '病历与处方', '检索条件']"
  />
</template>
