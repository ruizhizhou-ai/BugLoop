<!-- 本文件在用户没有任何工作空间时展示引导页，创建入口按角色权限控制。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCard, ElDialog, ElEmpty, ElForm, ElFormItem, ElInput } from 'element-plus'
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
      await router.replace({ name: 'bug-list', params: { workspaceId: current } })
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
      await router.push({ name: 'bug-list', params: { workspaceId: created } })
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
          <el-input v-model="createForm.description" type="textarea" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreateWorkspace">创建</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.workspace-picker {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
}

.workspace-picker :deep(.el-card) {
  width: min(100%, 560px);
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
  color: #606266;
}

.workspace-picker__alert {
  margin-bottom: 16px;
}
</style>
