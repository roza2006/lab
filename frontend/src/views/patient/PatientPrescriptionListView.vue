<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="我的处方"
    use-case="UC-P06"
    summary="查看本人处方状态（待审核 / 待流转 / 已核销 / 已驳回等）与用药指导；R4 接入处方查询接口。"
    :blocks="['状态筛选', '处方列表', '流转进度']"
  />
</template>
