<!-- 本文件实现登录页，成功登录后进入首页。 -->
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

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
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
    await auth.login({ username: form.username, password: form.password })
    await router.push({ name: 'home' })
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <el-card class="auth-card" shadow="never">
      <template #header>
        <div class="auth-card__header">
          <h1>BugLoop</h1>
          <span>内部 Bug 追踪系统</span>
        </div>
      </template>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

      <el-form
        ref="formRef"
        class="auth-card__form"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="auth-card__submit" :loading="submitting" @click="handleSubmit">
          登录
        </el-button>
      </el-form>

      <p class="auth-card__footer">
        还没有账号？
        <router-link :to="{ name: 'register' }">立即注册</router-link>
      </p>
    </el-card>
  </main>
</template>

<style scoped>
.auth-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
}

.auth-card {
  width: min(100%, 420px);
}

.auth-card__header {
  text-align: center;
}

.auth-card__header h1 {
  margin: 0 0 4px;
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
  color: #606266;
}
</style>
