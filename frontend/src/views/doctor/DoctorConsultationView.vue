<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import PageStub from '@/components/PageStub.vue'

const route = useRoute()
const sessionId = computed(() => String(route.params.id ?? ''))
</script>

<template>
  <PageStub
    title="接诊与病历"
    use-case="UC-D02"
    :summary="`会话 ${sessionId}：查看患者资料、接诊或拒诊、图文沟通并书写病历与诊断；R3 接入接诊（乐观锁）与病历签名。`"
    :blocks="['患者资料', '图文会话', '病历与诊断']"
  />
</template>
