<!-- 本文件是登录后的首页，工作空间与 Bug 能力将在后续里程碑接入。 -->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElButton, ElCard, ElTag } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/tag/style/css'

import { useAuthStore } from '@/features/auth/authStore'

const router = useRouter()
const auth = useAuthStore()

async function handleLogout(): Promise<void> {
  await auth.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <div class="home">
    <header class="home__header">
      <h1>BugLoop</h1>
      <div class="home__account">
        <span>{{ auth.user?.displayName }}</span>
        <el-tag :type="auth.user?.systemRole === 'SYSTEM_ADMIN' ? 'danger' : 'info'" size="small">
          {{ auth.user?.systemRole === 'SYSTEM_ADMIN' ? '系统管理员' : '普通用户' }}
        </el-tag>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <main class="home__body">
      <el-card shadow="never">
        <template #header>
          <h2>欢迎回来，{{ auth.user?.displayName }}</h2>
        </template>
        <p>登录链路已经打通。</p>
        <p class="home__hint">工作空间、成员与 Bug 能力将在下一个里程碑按 Spec 接入。</p>
      </el-card>
    </main>
  </div>
</template>

<style scoped>
.home {
  min-height: 100vh;
}

.home__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 24px;
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.home__header h1 {
  margin: 0;
  font-size: 18px;
}

.home__account {
  display: flex;
  align-items: center;
  gap: 12px;
}

.home__body {
  padding: 24px;
}

.home__body h2 {
  margin: 0;
  font-size: 16px;
}

.home__hint {
  margin-bottom: 0;
  color: #909399;
}
</style>
