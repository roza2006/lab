<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="审计与统计"
    use-case="UC-A04"
    summary="按操作人、时间与处方号检索敏感操作，校验审计哈希链并导出；同时查看问诊量、处方量与驳回率统计（UC-A05）。R6 完善。"
    :blocks="['审计检索', '审计详情', '运营统计']"
  />
</template>
