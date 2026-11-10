<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="药品目录"
    use-case="UC-A03"
    summary="维护药品、规格、分类与处方限制，停用药品不可新增到处方；所有变更写审计。R4 接入目录管理接口。"
    :blocks="['药品列表', '规格与处方限制', '停用 / 启用']"
  />
</template>
