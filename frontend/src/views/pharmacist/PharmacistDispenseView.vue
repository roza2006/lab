<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="药房工作台"
    use-case="UC-H05"
    summary="处理本药房的流转单：接方校验、取药人身份核验、调剂发药与幂等核销；R5 接入流转与核销接口（UC-F01、UC-F02）。"
    :blocks="['待接方流转单', '身份核验', '发药与核销']"
  />
</template>
