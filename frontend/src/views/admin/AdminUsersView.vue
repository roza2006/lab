<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="账号与角色"
    use-case="UC-A01"
    summary="新增/禁用账号、重置密码并分配角色，变更即时生效；管理员不得查看问诊消息与病历正文。R3 接入管理接口。"
    :blocks="['账号列表', '角色分配', '禁用 / 启用']"
  />
</template>
