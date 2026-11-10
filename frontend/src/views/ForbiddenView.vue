<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <el-result icon="warning" title="403" sub-title="当前账号没有访问该页面的权限。">
    <template #extra>
      <el-button type="primary" @click="router.replace('/')">返回我的首页</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </template>
  </el-result>
</template>
