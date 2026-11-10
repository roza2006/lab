<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="患者首页"
    use-case="UC-P03"
    summary="汇总进行中的问诊、待取药的处方与常用入口，让患者 3 步内发起问诊；R3 接入问诊与处方查询接口后展示真实数据。"
    :blocks="['进行中的问诊', '待取药处方', '快捷入口']"
  />
</template>
