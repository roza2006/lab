<script setup lang="ts">
import { reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { HOME_BY_ROLE } from '@/router/menus'
import { ROLE_LABELS, useAuthStore, type Role } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const roleOptions = (Object.keys(ROLE_LABELS) as Role[]).map((role) => ({
  value: role,
  label: ROLE_LABELS[role],
}))

const form = reactive({
  role: 'PATIENT' as Role,
  username: '',
  password: '',
})

function handleSubmit() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }

  // R2：本地模拟登录（不校验密码），R3 接入 POST /api/v1/auth/login。
  auth.loginAs(form.role, form.username)
  ElMessage.success(`已以${ROLE_LABELS[form.role]}身份进入演示环境`)

  const redirect =
    typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : HOME_BY_ROLE[form.role]
  router.replace(redirect)
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
        type="warning"
        show-icon
        :closable="false"
        title="R2 阶段为本地模拟登录：任选角色，密码不校验；R3 接入统一认证。"
        class="login__tip"
      />

      <el-form label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="登录角色">
          <el-select v-model="form.role" class="login__control">
            <el-option
              v-for="option in roleOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="演示账号：patient / doctor / pharmacist / admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="演示密码：123456" />
        </el-form-item>
        <el-button type="primary" class="login__submit" @click="handleSubmit">进入工作台</el-button>
      </el-form>

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
  width: min(420px, 100%);
}

.login__title {
  margin: 0 0 16px;
  font-size: 18px;
  color: var(--mp-color-primary);
}

.login__tip {
  margin-bottom: 16px;
}

.login__control {
  width: 100%;
}

.login__submit {
  width: 100%;
}

.login__foot {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--mp-text-secondary);
  text-align: center;
}
</style>
