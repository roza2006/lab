<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import PageStub from '@/components/PageStub.vue'

const route = useRoute()
const prescriptionId = computed(() => String(route.params.id ?? ''))
</script>

<template>
  <PageStub
    title="处方审核"
    use-case="UC-H02"
    :summary="`处方 ${prescriptionId}：核对诊断、用法用量、剂量与禁忌，填写审核意见后通过签名或驳回（理由不少于 10 字）；R4 实现，药师不得修改处方内容。`"
    :blocks="['处方与过敏史', '审核意见', '通过 / 驳回']"
  />
</template>
