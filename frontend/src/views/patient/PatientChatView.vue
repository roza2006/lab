<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import PageStub from '@/components/PageStub.vue'

const route = useRoute()
const sessionId = computed(() => String(route.params.id ?? ''))
</script>

<template>
  <PageStub
    title="问诊会话"
    use-case="UC-P04"
    :summary="`会话 ${sessionId}：查看消息历史、发送图文、结束问诊；R3 接入消息接口，发送需带客户端消息 ID 去重。`"
    :blocks="['消息历史', '图文发送', '结束问诊']"
  />
</template>
