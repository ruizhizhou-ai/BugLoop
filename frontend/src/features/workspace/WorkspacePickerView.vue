<!-- 本文件在用户没有任何工作空间时展示引导页，创建入口按角色权限控制。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'

import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from './workspaceStore'
import { isApiError } from '@/shared/api/types'
import ThemeToggle from '@/shared/components/ThemeToggle.vue'

const router = useRouter()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const errorMessage = ref('')
const submitting = ref(false)
const createDialogVisible = ref(false)
const createForm = reactive({ name: '', description: '' })

const isSystemAdmin = computed(() => auth.user?.systemRole === 'SYSTEM_ADMIN')
const canCreate = computed(
  () =>
    isSystemAdmin.value ||
    workspaceStore.workspaces.length === 0 ||
    workspaceStore.workspaces.some(
      (workspace) => workspace.currentUserRole === 'OWNER' || workspace.currentUserRole === 'ADMIN',
    ),
)

onMounted(async () => {
  errorMessage.value = ''
  try {
    await workspaceStore.loadWorkspaces()
    const current = workspaceStore.currentWorkspaceId
    if (current) {
      await router.replace({ name: 'workspace-dashboard', params: { workspaceId: current } })
    }
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载工作空间失败'
  }
})

async function handleCreateWorkspace(): Promise<void> {
  const name = createForm.name.trim()
  if (!name) {
    errorMessage.value = '请输入工作空间名称'
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    await workspaceStore.create({ name, description: createForm.description.trim() || null })
    createDialogVisible.value = false
    const created = workspaceStore.currentWorkspaceId
    if (created) {
      await router.push({ name: 'workspace-dashboard', params: { workspaceId: created } })
    }
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '创建工作空间失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="workspace-picker">
    <div class="workspace-picker__brand">
      <span class="workspace-picker__mark"><i /></span><strong>BugLoop</strong>
    </div>
    <div class="workspace-picker__theme"><ThemeToggle /></div>
    <el-card shadow="never">
      <template #header>
        <div class="workspace-picker__header">
          <h1>BugLoop</h1>
          <span>{{ auth.user?.displayName }}</span>
        </div>
      </template>

      <el-alert
        v-if="errorMessage"
        class="workspace-picker__alert"
        :title="errorMessage"
        type="error"
        :closable="true"
        show-icon
        @close="errorMessage = ''"
      />

      <el-empty
        :description="
          canCreate
            ? '你还没有加入工作空间，可以先创建一个'
            : '你还没有加入工作空间，请联系系统管理员或空间负责人邀请你加入'
        "
      >
        <el-button v-if="canCreate" type="primary" @click="createDialogVisible = true">
          创建工作空间
        </el-button>
      </el-empty>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="创建工作空间" width="min(92vw, 520px)">
      <el-form label-position="top" @submit.prevent="handleCreateWorkspace">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="createForm.description"
            type="textarea"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreateWorkspace"
          >创建</el-button
        >
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.workspace-picker {
  position: relative;
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  background:
    radial-gradient(circle at 20% 20%, rgb(27 113 214 / 18%), transparent 28%),
    radial-gradient(circle at 80% 80%, rgb(29 85 151 / 12%), transparent 30%), #0d1218;
}

.workspace-picker__brand {
  position: absolute;
  top: 28px;
  left: 32px;
  display: flex;
  align-items: center;
  gap: 11px;
  color: #f3f7fc;
  font-size: 20px;
}

.workspace-picker__theme {
  position: absolute;
  top: 25px;
  right: 32px;
  z-index: 2;
}

.workspace-picker__mark {
  position: relative;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  transform: rotate(-32deg);
  border: 6px solid #318cff;
  border-radius: 50%;
}

.workspace-picker__mark::after {
  position: absolute;
  right: -7px;
  width: 10px;
  height: 6px;
  content: '';
  background: #318cff;
  border-radius: 2px;
}

.workspace-picker__mark i {
  width: 6px;
  height: 6px;
  border: 2px solid #61c0ff;
  border-radius: 50%;
}

.workspace-picker :deep(.el-card) {
  width: min(100%, 560px);
  border-color: #313c48;
  border-radius: 12px;
  box-shadow: 0 28px 80px rgb(0 0 0 / 38%);
}

.workspace-picker__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.workspace-picker__header h1 {
  margin: 0;
  font-size: 18px;
}

.workspace-picker__header span {
  color: var(--bl-text-secondary);
}

.workspace-picker__alert {
  margin-bottom: 16px;
}
</style>
