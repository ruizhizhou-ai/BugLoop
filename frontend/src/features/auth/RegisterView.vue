<!-- 本文件实现注册页，注册成功后直接进入首页。 -->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCard, ElForm, ElFormItem, ElInput } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'

import { useAuthStore } from './authStore'
import { isApiError } from '@/shared/api/types'
import ThemeToggle from '@/shared/components/ThemeToggle.vue'

const router = useRouter()
const auth = useAuthStore()

const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,64}$/

const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: USERNAME_PATTERN, message: '用户名需为 3-64 位字母、数字或下划线', trigger: 'blur' },
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { max: 64, message: '显示名称不能超过 64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码长度需为 8-72 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    await auth.register({
      username: form.username,
      displayName: form.displayName,
      password: form.password,
    })
    await router.push({ name: 'home' })
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '注册失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <div class="auth-page__brand">
      <span class="auth-page__mark"><i /></span><strong>BugLoop</strong>
    </div>
    <div class="auth-page__theme"><ThemeToggle /></div>
    <el-card class="auth-card" shadow="never">
      <template #header>
        <div class="auth-card__header">
          <h1>注册 BugLoop 账号</h1>
          <span>注册后请联系系统管理员将你加入工作空间</span>
        </div>
      </template>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
      />

      <el-form
        ref="formRef"
        class="auth-card__form"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="3-64 位字母、数字或下划线"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" placeholder="展示给其他成员的名字" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="8-72 位"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            autocomplete="new-password"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button
          type="primary"
          class="auth-card__submit"
          :loading="submitting"
          @click="handleSubmit"
        >
          注册并进入
        </el-button>
      </el-form>

      <p class="auth-card__footer">
        已有账号？
        <router-link :to="{ name: 'login' }">返回登录</router-link>
      </p>
    </el-card>
  </main>
</template>

<style scoped>
.auth-page {
  position: relative;
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  background:
    radial-gradient(circle at 20% 20%, rgb(27 113 214 / 18%), transparent 28%),
    radial-gradient(circle at 80% 80%, rgb(29 85 151 / 12%), transparent 30%), #0d1218;
}

.auth-page::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  opacity: 0.25;
  background-image:
    linear-gradient(#26313c 1px, transparent 1px),
    linear-gradient(90deg, #26313c 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(circle, black, transparent 70%);
}

.auth-page__brand {
  position: absolute;
  top: 28px;
  left: 32px;
  display: flex;
  align-items: center;
  gap: 11px;
  color: #f3f7fc;
  font-size: 20px;
}

.auth-page__theme {
  position: absolute;
  top: 25px;
  right: 32px;
  z-index: 2;
}

.auth-page__mark {
  position: relative;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  transform: rotate(-32deg);
  border: 6px solid #318cff;
  border-radius: 50%;
}

.auth-page__mark::after {
  position: absolute;
  right: -7px;
  width: 10px;
  height: 6px;
  content: '';
  background: #318cff;
  border-radius: 2px;
}

.auth-page__mark i {
  width: 6px;
  height: 6px;
  border: 2px solid #61c0ff;
  border-radius: 50%;
}

.auth-card {
  position: relative;
  z-index: 1;
  width: min(100%, 420px);
  border-color: #313c48;
  border-radius: 12px;
  box-shadow: 0 28px 80px rgb(0 0 0 / 38%);
}

.auth-card__header {
  text-align: center;
}

.auth-card__header h1 {
  margin: 0 0 4px;
  color: #f2f6fb;
  font-size: 20px;
}

.auth-card__header span {
  color: #909399;
  font-size: 13px;
}

.auth-card__submit {
  width: 100%;
  margin-top: 8px;
}

.auth-card__footer {
  margin: 16px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--bl-text-secondary);
}
</style>
