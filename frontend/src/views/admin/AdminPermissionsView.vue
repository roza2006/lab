<script setup lang="ts">
import PageStub from '@/components/PageStub.vue'
</script>

<template>
  <PageStub
    title="权限策略"
    use-case="UC-A02"
    summary="配置角色权限点与数据范围（本人 / 科室 / 本药房 / 全部），策略版本化并记录审计；R4 实现权限矩阵编辑。"
    :blocks="['角色列表', '权限点矩阵', '数据范围']"
  />
</template>
