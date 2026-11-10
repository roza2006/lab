<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { HOME_BY_ROLE } from '@/router/menus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const form = reactive({ username: '', password: '' })

const demoAccounts = [
  { username: 'patient', label: '患者' },
  { username: 'doctor', label: '医生' },
  { username: 'pharmacist', label: '药师' },
  { username: 'admin', label: '管理员' },
]

function fill(username: string) {
  form.username = username
  form.password = '123456'
}

async function handleSubmit() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }

  loading.value = true
  try {
    await auth.login(form.username, form.password)
    const role = auth.role
    if (!role) {
      throw new Error('登录状态异常，请重试')
    }
    ElMessage.success(`欢迎回来，${auth.displayName}`)

    const redirect =
      typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
        ? route.query.redirect
        : HOME_BY_ROLE[role]
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="login__brand">
      <span class="login__mark">医</span>
      <div>
        <h1>智慧医疗在线问诊与处方流转平台</h1>
        <p>软件工程课程实验 · 患者 / 医生 / 药师 / 管理员四端</p>
      </div>
    </div>

    <el-card class="login__card" shadow="always">
      <h2 class="login__title">登录</h2>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="已接入后端 POST /api/v1/auth/login；未启动后端时可运行 pnpm mock 使用本地演示数据。"
        class="login__tip"
      />

      <el-form label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入账号" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="login__submit" :loading="loading" @click="handleSubmit">
          登录
        </el-button>
      </el-form>

      <div class="login__demo">
        <span class="login__demo-label">演示账号（密码 123456）：</span>
        <el-button
          v-for="account in demoAccounts"
          :key="account.username"
          link
          type="primary"
          @click="fill(account.username)"
        >
          {{ account.label }}
        </el-button>
      </div>

      <p class="login__foot">演示数据均为虚构，请勿录入真实患者信息。</p>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28px;
  min-height: 100vh;
  padding: 32px 16px;
  background: linear-gradient(135deg, #eaf1f9 0%, #f7f9fc 55%, #e6f1ee 100%);
}

.login__brand {
  display: flex;
  align-items: center;
  gap: 14px;
  text-align: left;
}

.login__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: var(--mp-color-primary);
  color: #fff;
  font-size: 26px;
  font-weight: 700;
}

.login__brand h1 {
  margin: 0;
  font-size: 22px;
  color: var(--mp-color-primary);
}

.login__brand p {
  margin: 6px 0 0;
  color: var(--mp-text-secondary);
}

.login__card {
  width: min(440px, 100%);
}

.login__title {
  margin: 0 0 16px;
  font-size: 18px;
  color: var(--mp-color-primary);
}

.login__tip {
  margin-bottom: 16px;
}

.login__submit {
  width: 100%;
}

.login__demo {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 14px;
}

.login__demo-label {
  font-size: 13px;
  color: var(--mp-text-secondary);
}

.login__foot {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--mp-text-secondary);
  text-align: center;
}
</style>
