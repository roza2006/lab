<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { MENUS } from '@/router/menus'
import { ROLE_LABELS, useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const menu = computed(() => (auth.role ? MENUS[auth.role] : []))
const roleLabel = computed(() => (auth.role ? ROLE_LABELS[auth.role] : ''))
const workspaceTitle = computed(() => (auth.role ? `${ROLE_LABELS[auth.role]}工作台` : '工作台'))

function handleLogout() {
  auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <el-container class="layout">
    <el-aside class="layout__aside" width="220px">
      <div class="brand">
        <span class="brand__mark">医</span>
        <div class="brand__text">
          <strong>智慧医疗平台</strong>
          <small>在线问诊 · 处方流转</small>
        </div>
      </div>
      <el-menu :default-active="route.path" router class="layout__menu">
        <el-menu-item v-for="item in menu" :key="item.path" :index="item.path">
          {{ item.title }}
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout__header" height="60px">
        <div class="layout__title">{{ workspaceTitle }}</div>
        <div class="layout__account">
          <el-tag type="info" effect="plain">{{ roleLabel }}</el-tag>
          <span class="layout__name">{{ auth.displayName }}</span>
          <el-button link type="primary" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.layout__aside {
  display: flex;
  flex-direction: column;
  background: #1f2d3d;
  color: #fff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #2e75b6;
  font-weight: 700;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.brand__text small {
  color: rgba(255, 255, 255, 0.6);
}

.layout__menu {
  flex: 1;
  border-right: none;
  background: transparent;
  --el-menu-text-color: rgba(255, 255, 255, 0.78);
  --el-menu-hover-bg-color: rgba(46, 117, 182, 0.35);
  --el-menu-active-color: #fff;
}

.layout__menu :deep(.el-menu-item.is-active) {
  background: #2e75b6;
}

.layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--mp-color-border);
}

.layout__title {
  font-size: 17px;
  font-weight: 600;
  color: var(--mp-color-primary);
}

.layout__account {
  display: flex;
  align-items: center;
  gap: 10px;
}

.layout__name {
  color: var(--mp-text-secondary);
}

.layout__main {
  padding: 20px;
  background: var(--mp-color-bg);
}
</style>
