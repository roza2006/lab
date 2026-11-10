<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import PageStub from '@/components/PageStub.vue'

const route = useRoute()
const prescriptionId = computed(() => String(route.params.id ?? ''))
</script>

<template>
  <PageStub
    title="处方详情与流转追踪"
    use-case="UC-P06"
    :summary="`处方 ${prescriptionId}：展示药品明细、用法用量、审核签名与流转/核销轨迹；R4 接入处方详情，R5 接入追溯接口。`"
    :blocks="['处方明细', '审核信息', '流转与核销轨迹']"
  />
</template>
