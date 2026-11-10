<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="我的问诊"
    use-case="UC-P05"
    summary="按时间倒序查看本人历史会话、病历摘要与处方入口，支持按状态筛选；R3 接入列表查询。"
    :blocks="['状态筛选', '会话列表', '病历摘要']"
  />
</template>
