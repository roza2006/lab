<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="待审处方"
    use-case="UC-H01"
    summary="按药房、时间与风险等级查看待审处方，展示患者过敏史摘要与风险标记；R4 接入审核队列。"
    :blocks="['待审列表', '风险标记', '筛选条件']"
  />
</template>
