<!-- 本文件为工作空间内的页面提供统一顶部导航：空间切换、账号信息与退出登录。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElOption, ElSelect, ElTag } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/tag/style/css'

import { useAuthStore } from '@/features/auth/authStore'
import { useBugStore } from '@/features/bug/bugStore'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()
const bugStore = useBugStore()

const errorMessage = ref('')
const isSystemAdmin = computed(() => auth.user?.systemRole === 'SYSTEM_ADMIN')
const onSettingsPage = computed(() => route.name === 'workspace-settings')

onMounted(() => {
  if (workspaceStore.workspaces.length === 0) {
    void loadWorkspaces()
  }
})

async function loadWorkspaces(): Promise<void> {
  try {
    await workspaceStore.loadWorkspaces()
  } catch {
    errorMessage.value = '加载工作空间失败，请刷新重试'
  }
}

/** 切换空间时同步刷新当前页面的 workspaceId，保证 URL 与数据一致。 */
async function handleWorkspaceChange(value: number): Promise<void> {
  try {
    await workspaceStore.selectWorkspace(value)
    bugStore.resetQuery()
    const name = route.name
    if (name === 'bug-detail' && route.params.bugId) {
      await router.push({ name: 'bug-list', params: { workspaceId: value } })
      return
    }
    if (name === 'bug-list' || name === 'bug-create' || name === 'workspace-settings') {
      await router.push({ name: String(name), params: { workspaceId: value } })
    }
  } catch {
    errorMessage.value = '切换工作空间失败，请稍后重试'
  }
}

function goToList(): void {
  void router.push({ name: 'bug-list', params: { workspaceId: workspaceStore.currentWorkspaceId } })
}

async function handleLogout(): Promise<void> {
  bugStore.reset()
  workspaceStore.reset()
  await auth.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <div class="workspace-layout">
    <header class="workspace-layout__header">
      <div class="workspace-layout__brand">
        <h1>BugLoop</h1>
        <el-select
          v-if="workspaceStore.workspaces.length"
          class="workspace-switcher"
          :model-value="workspaceStore.currentWorkspaceId"
          placeholder="选择工作空间"
          :loading="workspaceStore.loading"
          @change="handleWorkspaceChange"
        >
          <el-option
            v-for="workspace in workspaceStore.workspaces"
            :key="workspace.id"
            :label="workspace.name"
            :value="workspace.id"
          >
            <span>{{ workspace.name }}</span>
            <span v-if="workspace.status === 'DISABLED'" class="workspace-option__status">已停用</span>
          </el-option>
        </el-select>
        <el-button
          v-if="!onSettingsPage"
          link
          type="primary"
          @click="router.push({ name: 'workspace-settings', params: { workspaceId: workspaceStore.currentWorkspaceId } })"
        >
          工作空间设置
        </el-button>
        <el-button v-else link type="primary" @click="goToList">返回 Bug 列表</el-button>
      </div>

      <div class="workspace-layout__account">
        <span>{{ auth.user?.displayName }}</span>
        <el-tag :type="isSystemAdmin ? 'danger' : 'info'" size="small">
          {{ isSystemAdmin ? '系统管理员' : '普通用户' }}
        </el-tag>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <div v-if="errorMessage" class="workspace-layout__error">{{ errorMessage }}</div>

    <router-view />
  </div>
</template>

<style scoped>
.workspace-layout {
  min-height: 100vh;
}

.workspace-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 24px;
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.workspace-layout__brand {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.workspace-layout__brand h1 {
  margin: 0;
  font-size: 18px;
  flex-shrink: 0;
}

.workspace-switcher {
  width: 220px;
}

.workspace-option__status {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.workspace-layout__account {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.workspace-layout__error {
  padding: 8px 24px;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 13px;
}

@media (max-width: 760px) {
  .workspace-layout__header {
    height: auto;
    flex-direction: column;
    align-items: flex-start;
    padding: 12px 16px;
    gap: 8px;
  }
}
</style>
