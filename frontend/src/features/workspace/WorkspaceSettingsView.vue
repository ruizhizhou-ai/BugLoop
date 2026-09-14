<!-- 本文件实现工作空间设置页：空间信息维护与成员管理，顶部导航由 WorkspaceLayout 提供。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
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
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from './workspaceStore'
import { searchAvailableWorkspaceUsers } from './workspaceApi'
import type { AvailableWorkspaceUser, WorkspaceRole } from './workspaceApi'
import { isApiError } from '@/shared/api/types'

const route = useRoute()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const errorMessage = ref('')
const submitting = ref(false)
const editDialogVisible = ref(false)
const memberDialogVisible = ref(false)
const availableUsers = ref<AvailableWorkspaceUser[]>([])
const userSearchLoading = ref(false)
let userSearchRequestId = 0

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
const canEnableWorkspace = computed(
  () =>
    workspaceStore.currentWorkspace?.status === 'DISABLED' &&
    (isSystemAdmin.value || currentRole.value === 'OWNER'),
)

watch(
  () => route.params.workspaceId,
  async (workspaceId) => {
    const id = Number(workspaceId)
    if (!Number.isNaN(id) && id > 0 && workspaceStore.currentWorkspaceId !== id) {
      await runAction(() => workspaceStore.selectWorkspace(id))
    }
  },
  { immediate: true },
)

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

/** 打开成员添加窗口并加载首批可选用户，降低管理员记忆随机 ID 的成本。 */
async function openMemberDialog(): Promise<void> {
  memberForm.userId = undefined
  memberForm.role = 'MEMBER'
  memberDialogVisible.value = true
  await searchAvailableUsers('')
}

/** 搜索可加入当前空间的启用用户，只采用最后一次请求的结果避免快速输入时列表倒退。 */
async function searchAvailableUsers(keyword: string): Promise<void> {
  const workspaceId = workspaceStore.currentWorkspaceId
  if (!workspaceId) {
    return
  }
  const requestId = ++userSearchRequestId
  userSearchLoading.value = true
  try {
    const users = await searchAvailableWorkspaceUsers(workspaceId, keyword.trim())
    // 远程搜索可能乱序返回，过期结果不能覆盖用户刚输入关键词对应的候选项。
    if (requestId === userSearchRequestId) {
      availableUsers.value = users
    }
  } catch (error) {
    if (requestId === userSearchRequestId) {
      availableUsers.value = []
      errorMessage.value = isApiError(error) ? error.message : '查询用户失败，请稍后重试'
    }
  } finally {
    if (requestId === userSearchRequestId) {
      userSearchLoading.value = false
    }
  }
}

/** 添加已选择的系统用户，提交时仍由服务端校验用户状态和成员重复关系。 */
async function handleAddMember(): Promise<void> {
  if (!memberForm.userId || memberForm.userId <= 0) {
    errorMessage.value = '请选择要添加的用户'
    return
  }
  const succeeded = await runAction(() =>
    workspaceStore.addMember({ userId: memberForm.userId!, role: memberForm.role }),
  )
  if (succeeded) {
    memberDialogVisible.value = false
    memberForm.userId = undefined
    memberForm.role = 'MEMBER'
    availableUsers.value = []
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

/** 重新启用工作空间并立即恢复当前页面的管理入口。 */
async function handleEnableWorkspace(): Promise<void> {
  await runAction(() => workspaceStore.enable())
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
  <main class="workspace-settings">
    <el-alert
      v-if="errorMessage"
      class="workspace-settings__alert"
      :title="errorMessage"
      type="error"
      :closable="true"
      show-icon
      @close="errorMessage = ''"
    />

    <el-card v-if="workspaceStore.loading" shadow="never">正在加载工作空间…</el-card>

    <el-card v-else-if="!workspaceStore.currentWorkspace" class="empty-card" shadow="never">
      <el-empty description="工作空间不存在或你已不是成员" />
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
              <el-button
                v-if="canEnableWorkspace"
                type="success"
                :loading="submitting"
                @click="handleEnableWorkspace"
              >
                重新启用
              </el-button>
            </div>
          </div>
        </template>
        <p class="workspace-card__hint">
          工作空间 ID：{{ workspaceStore.currentWorkspace.id }}。Bug 数据严格限定在该空间内。
        </p>
      </el-card>

      <el-card class="member-card" shadow="never">
        <template #header>
          <div class="card-header card-header--center">
            <div>
              <h2>成员管理</h2>
              <span class="card-subtitle">共 {{ workspaceStore.members.length }} 位成员</span>
            </div>
            <el-button v-if="canManageMembers" type="primary" @click="openMemberDialog">
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
        <el-form-item label="选择用户" required>
          <el-select
            v-model="memberForm.userId"
            filterable
            remote
            :remote-method="searchAvailableUsers"
            :loading="userSearchLoading"
            placeholder="输入用户名或显示名称搜索"
            no-data-text="没有可添加的启用用户"
          >
            <el-option
              v-for="user in availableUsers"
              :key="user.id"
              :label="`${user.displayName}（${user.username}）`"
              :value="user.id"
            />
          </el-select>
          <span class="form-hint">仅显示已启用且尚未加入当前工作空间的用户。</span>
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
  </main>
</template>

<style scoped>
.workspace-settings {
  padding: 24px;
}

.workspace-settings__alert {
  margin-bottom: 16px;
}

.workspace-card,
.member-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.card-header--center {
  align-items: center;
}

.card-header h2 {
  margin: 0;
  font-size: 16px;
}

.card-subtitle {
  color: #909399;
  font-size: 13px;
}

.workspace-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workspace-title h2 {
  margin: 0;
}

.card-header p {
  margin: 8px 0 0;
  color: #606266;
}

.workspace-card__hint {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.form-hint {
  color: #909399;
  font-size: 12px;
}

@media (max-width: 760px) {
  .workspace-settings {
    padding: 16px;
  }
}
</style>
