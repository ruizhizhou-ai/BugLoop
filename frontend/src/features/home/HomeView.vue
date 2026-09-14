<!-- 本文件是登录后的工作空间首页，负责空间切换、创建、成员管理和空间设置。 -->
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
  ElInputNumber,
  ElOption,
  ElPopconfirm,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/input-number/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'
import type { WorkspaceRole } from '@/features/workspace/workspaceApi'
import { isApiError } from '@/shared/api/types'

const router = useRouter()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const errorMessage = ref('')
const submitting = ref(false)
const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const memberDialogVisible = ref(false)

const createForm = reactive({ name: '', description: '' })
const editForm = reactive({ name: '', description: '' })
const memberForm = reactive<{ userId: number | undefined; role: WorkspaceRole }>({
  userId: undefined,
  role: 'MEMBER',
})

const isSystemAdmin = computed(() => auth.user?.systemRole === 'SYSTEM_ADMIN')
const currentRole = computed(() => workspaceStore.currentWorkspace?.currentUserRole)
const canManageMembers = computed(
  () =>
    workspaceStore.isEnabled &&
    (isSystemAdmin.value || currentRole.value === 'OWNER' || currentRole.value === 'ADMIN'),
)
const canEditWorkspace = canManageMembers
const canDisableWorkspace = computed(
  () => workspaceStore.isEnabled && (isSystemAdmin.value || currentRole.value === 'OWNER'),
)

onMounted(async () => {
  await runAction(() => workspaceStore.loadWorkspaces())
})

/** 退出当前会话并清理工作空间状态，避免下一账号看到旧数据。 */
async function handleLogout(): Promise<void> {
  workspaceStore.reset()
  await auth.logout()
  await router.push({ name: 'login' })
}

/** 切换工作空间并刷新成员列表。 */
async function handleWorkspaceChange(value: number): Promise<void> {
  await runAction(() => workspaceStore.selectWorkspace(value))
}

/** 校验并创建工作空间，成功后自动切换到新空间。 */
async function handleCreateWorkspace(): Promise<void> {
  const name = createForm.name.trim()
  if (!name) {
    errorMessage.value = '请输入工作空间名称'
    return
  }
  const succeeded = await runAction(() =>
    workspaceStore.create({ name, description: createForm.description.trim() || null }),
  )
  if (succeeded) {
    createDialogVisible.value = false
    createForm.name = ''
    createForm.description = ''
  }
}

/** 使用当前空间数据打开编辑窗口。 */
function openEditDialog(): void {
  const workspace = workspaceStore.currentWorkspace
  if (!workspace) {
    return
  }
  editForm.name = workspace.name
  editForm.description = workspace.description ?? ''
  editDialogVisible.value = true
}

/** 保存工作空间名称和描述。 */
async function handleUpdateWorkspace(): Promise<void> {
  const name = editForm.name.trim()
  if (!name) {
    errorMessage.value = '请输入工作空间名称'
    return
  }
  const succeeded = await runAction(() =>
    workspaceStore.update({ name, description: editForm.description.trim() || null }),
  )
  if (succeeded) {
    editDialogVisible.value = false
  }
}

/** 添加已有系统用户，第一期按用户 ID 精确添加。 */
async function handleAddMember(): Promise<void> {
  if (!memberForm.userId || memberForm.userId <= 0) {
    errorMessage.value = '请输入有效的用户 ID'
    return
  }
  const succeeded = await runAction(() =>
    workspaceStore.addMember({ userId: memberForm.userId!, role: memberForm.role }),
  )
  if (succeeded) {
    memberDialogVisible.value = false
    memberForm.userId = undefined
    memberForm.role = 'MEMBER'
  }
}

/** 修改成员角色，本地行只在服务端成功后由 Store 替换。 */
async function handleRoleChange(userId: number, role: WorkspaceRole): Promise<void> {
  await runAction(() => workspaceStore.changeMemberRole(userId, role))
}

/** 移除成员，冲突原因由后端按照 Owner 和 Bug 责任规则返回。 */
async function handleRemoveMember(userId: number): Promise<void> {
  await runAction(() => workspaceStore.removeMember(userId))
}

/** 停用工作空间，停用后页面保留详情和成员只读能力。 */
async function handleDisableWorkspace(): Promise<void> {
  await runAction(() => workspaceStore.disable())
}

/** 把角色枚举转换为界面中文。 */
function roleLabel(role: WorkspaceRole | null | undefined): string {
  return { OWNER: '负责人', ADMIN: '管理员', MEMBER: '成员' }[role ?? 'MEMBER']
}

/**
 * 统一执行异步操作并展示业务错误，避免各按钮重复维护 loading 和异常分支。
 *
 * @param action 待执行异步操作
 * @return 是否执行成功
 */
async function runAction(action: () => Promise<void>): Promise<boolean> {
  submitting.value = true
  errorMessage.value = ''
  try {
    await action()
    return true
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '操作失败，请稍后重试'
    return false
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="home">
    <header class="home__header">
      <div class="home__brand">
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
        <el-button type="primary" plain @click="createDialogVisible = true">创建工作空间</el-button>
      </div>

      <div class="home__account">
        <span>{{ auth.user?.displayName }}</span>
        <el-tag :type="isSystemAdmin ? 'danger' : 'info'" size="small">
          {{ isSystemAdmin ? '系统管理员' : '普通用户' }}
        </el-tag>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <main class="home__body">
      <el-alert
        v-if="errorMessage"
        class="home__alert"
        :title="errorMessage"
        type="error"
        :closable="true"
        show-icon
        @close="errorMessage = ''"
      />

      <el-card v-if="workspaceStore.loading" shadow="never">正在加载工作空间…</el-card>

      <el-card v-else-if="!workspaceStore.currentWorkspace" class="empty-card" shadow="never">
        <el-empty description="你还没有加入工作空间">
          <el-button type="primary" @click="createDialogVisible = true">创建第一个工作空间</el-button>
        </el-empty>
      </el-card>

      <template v-else>
        <el-card class="workspace-card" shadow="never">
          <template #header>
            <div class="card-header">
              <div>
                <div class="workspace-title">
                  <h2>{{ workspaceStore.currentWorkspace.name }}</h2>
                  <el-tag :type="workspaceStore.isEnabled ? 'success' : 'info'" size="small">
                    {{ workspaceStore.isEnabled ? '运行中' : '已停用' }}
                  </el-tag>
                  <el-tag v-if="currentRole" type="primary" size="small">
                    {{ roleLabel(currentRole) }}
                  </el-tag>
                </div>
                <p>{{ workspaceStore.currentWorkspace.description || '暂无工作空间说明' }}</p>
              </div>
              <div class="card-header__actions">
                <el-button v-if="canEditWorkspace" @click="openEditDialog">编辑设置</el-button>
                <el-popconfirm
                  v-if="canDisableWorkspace"
                  title="停用后将只允许查看历史数据，确认继续吗？"
                  confirm-button-text="确认停用"
                  cancel-button-text="取消"
                  @confirm="handleDisableWorkspace"
                >
                  <template #reference>
                    <el-button type="danger" plain>停用工作空间</el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </template>
          <p class="workspace-card__hint">
            工作空间 ID：{{ workspaceStore.currentWorkspace.id }}。后续 Bug 数据将严格限定在该空间内。
          </p>
        </el-card>

        <el-card class="member-card" shadow="never">
          <template #header>
            <div class="card-header card-header--center">
              <div>
                <h2>成员管理</h2>
                <span class="card-subtitle">共 {{ workspaceStore.members.length }} 位成员</span>
              </div>
              <el-button v-if="canManageMembers" type="primary" @click="memberDialogVisible = true">
                添加成员
              </el-button>
            </div>
          </template>

          <el-table :data="workspaceStore.members" row-key="userId">
            <el-table-column prop="displayName" label="显示名称" min-width="150" />
            <el-table-column prop="username" label="用户名" min-width="140" />
            <el-table-column prop="userId" label="用户 ID" width="100" />
            <el-table-column label="账号状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '已启用' : '已禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="空间角色" width="160">
              <template #default="{ row }">
                <el-select
                  v-if="canManageMembers"
                  :model-value="row.role"
                  size="small"
                  @change="(role: WorkspaceRole) => handleRoleChange(row.userId, role)"
                >
                  <el-option label="负责人" value="OWNER" />
                  <el-option label="管理员" value="ADMIN" />
                  <el-option label="成员" value="MEMBER" />
                </el-select>
                <el-tag v-else size="small">{{ roleLabel(row.role) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="canManageMembers" label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-popconfirm
                  title="确认移除该成员吗？"
                  confirm-button-text="确认"
                  cancel-button-text="取消"
                  @confirm="handleRemoveMember(row.userId)"
                >
                  <template #reference>
                    <el-button link type="danger">移除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </template>
    </main>

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

    <el-dialog v-model="editDialogVisible" title="工作空间设置" width="min(92vw, 520px)">
      <el-form label-position="top" @submit.prevent="handleUpdateWorkspace">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" type="textarea" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleUpdateWorkspace">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberDialogVisible" title="添加工作空间成员" width="min(92vw, 480px)">
      <el-form label-position="top" @submit.prevent="handleAddMember">
        <el-form-item label="用户 ID" required>
          <el-input-number v-model="memberForm.userId" :min="1" :precision="0" controls-position="right" />
          <span class="form-hint">只能添加已注册且处于启用状态的系统用户。</span>
        </el-form-item>
        <el-form-item label="初始角色" required>
          <el-select v-model="memberForm.role">
            <el-option label="负责人" value="OWNER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="成员" value="MEMBER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAddMember">添加</el-button>
      </template>
    </el-dialog>
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
  gap: 20px;
  min-height: 64px;
  padding: 8px 24px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.home__brand,
.home__account,
.workspace-title,
.card-header,
.card-header__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.home__brand h1 {
  margin: 0 12px 0 0;
  font-size: 19px;
}

.workspace-switcher {
  width: 240px;
}

.workspace-option__status {
  float: right;
  margin-left: 20px;
  color: #909399;
  font-size: 12px;
}

.home__body {
  display: grid;
  gap: 20px;
  width: min(1180px, calc(100% - 48px));
  margin: 0 auto;
  padding: 24px 0;
}

.home__alert {
  margin-bottom: 0;
}

.empty-card {
  min-height: 360px;
}

.card-header {
  justify-content: space-between;
  align-items: flex-start;
  width: 100%;
}

.card-header--center {
  align-items: center;
}

.card-header h2,
.workspace-title h2 {
  margin: 0;
  font-size: 17px;
}

.card-header p {
  margin: 8px 0 0;
  color: #606266;
}

.card-subtitle,
.workspace-card__hint,
.form-hint {
  color: #909399;
  font-size: 13px;
}

.workspace-card__hint {
  margin: 0;
}

.form-hint {
  display: block;
  width: 100%;
  margin-top: 6px;
  line-height: 1.5;
}

@media (max-width: 760px) {
  .home__header,
  .home__brand {
    align-items: stretch;
    flex-direction: column;
  }

  .home__header {
    padding: 16px;
  }

  .home__brand h1 {
    margin: 0;
  }

  .workspace-switcher {
    width: 100%;
  }

  .home__account {
    justify-content: space-between;
  }

  .home__body {
    width: min(100% - 24px, 1180px);
    padding: 12px 0;
  }

  .card-header {
    flex-direction: column;
  }
}
</style>
