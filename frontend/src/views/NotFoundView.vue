<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <el-result icon="info" title="404" sub-title="页面不存在，或链接已失效。">
    <template #extra>
      <el-button type="primary" @click="router.replace('/')">返回我的首页</el-button>
    </template>
  </el-result>
</template>
