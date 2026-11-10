<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="个人中心"
    use-case="UC-P07"
    summary="维护本人实名信息与联系方式，查看知情同意版本并撤回非必要授权（撤回后保留历史记录）；R3 接入账号与同意记录接口。"
    :blocks="['实名信息', '知情同意记录', '授权撤回']"
  />
</template>
