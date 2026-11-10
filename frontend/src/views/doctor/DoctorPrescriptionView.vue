<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import PageStub from '@/components/PageStub.vue'

const route = useRoute()
const sessionId = computed(() => String(route.params.id ?? ''))
</script>

<template>
  <PageStub
    title="开方工作台"
    use-case="UC-D04"
    :summary="`会话 ${sessionId} 开方：检索药品、填写剂量/频次/疗程，实时校验目录与过敏史，保存草稿并提交审核；R4 接入处方聚合与状态机。`"
    :blocks="['药品检索与明细', '用法用量与校验', '保存草稿与提交']"
  />
</template>
