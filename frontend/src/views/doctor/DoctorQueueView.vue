<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="待接诊队列"
    use-case="UC-D01"
    summary="查看本人队列中的待接诊会话，按等待时长与风险标记排序，展示患者主诉摘要；R3 接入队列接口。"
    :blocks="['队列列表', '等待时长与风险', '接诊入口']"
  />
</template>
