<!-- 本文件实现独立成员管理页，承载成员搜索、邀请、角色调整和移除操作，并按空间权限切换编辑或只读状态。 -->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElOption,
  ElPopconfirm,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import AppIcon from '@/shared/components/AppIcon.vue'
import { useAuthStore } from '@/features/auth/authStore'
import { isApiError } from '@/shared/api/types'
import { searchAvailableWorkspaceUsers } from './workspaceApi'
import type { AvailableWorkspaceUser, WorkspaceRole } from './workspaceApi'
import { useWorkspaceStore } from './workspaceStore'

const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const errorMessage = ref('')
const submitting = ref(false)
const memberDialogVisible = ref(false)
const availableUsers = ref<AvailableWorkspaceUser[]>([])
const userSearchLoading = ref(false)
let userSearchRequestId = 0

const memberForm = reactive<{ userId: number | undefined; role: WorkspaceRole }>({
  userId: undefined,
  role: 'MEMBER',
})

const currentRole = computed(() => workspaceStore.currentWorkspace?.currentUserRole)
const canManageMembers = computed(
  () =>
    workspaceStore.isEnabled &&
    (auth.user?.systemRole === 'SYSTEM_ADMIN' ||
      currentRole.value === 'OWNER' ||
      currentRole.value === 'ADMIN'),
)

/** 打开邀请弹窗并获取首批可选用户，避免管理员必须提前知道随机用户 ID。 */
async function openMemberDialog(): Promise<void> {
  memberForm.userId = undefined
  memberForm.role = 'MEMBER'
  memberDialogVisible.value = true
  await searchAvailableUsers('')
}

/**
 * 搜索尚未加入当前空间的启用用户，只接受最后一次请求结果，避免快速输入时旧结果覆盖新结果。
 *
 * @param keyword 用户名或显示名称关键词
 */
async function searchAvailableUsers(keyword: string): Promise<void> {
  const workspaceId = workspaceStore.currentWorkspaceId
  if (!workspaceId) return

  const requestId = ++userSearchRequestId
  userSearchLoading.value = true
  try {
    const users = await searchAvailableWorkspaceUsers(workspaceId, keyword.trim())
    if (requestId === userSearchRequestId) availableUsers.value = users
  } catch (error) {
    if (requestId === userSearchRequestId) {
      availableUsers.value = []
      errorMessage.value = isApiError(error) ? error.message : '查询用户失败，请稍后重试'
    }
  } finally {
    if (requestId === userSearchRequestId) userSearchLoading.value = false
  }
}

/** 添加选择的系统用户，成功后由 Store 重新获取服务端成员顺序。 */
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
    availableUsers.value = []
  }
}

/** 修改成员角色，权限与 Owner 边界最终仍由后端校验。 */
async function handleRoleChange(userId: number, role: WorkspaceRole): Promise<void> {
  await runAction(() => workspaceStore.changeMemberRole(userId, role))
}

/** 移除成员，服务端会阻止移除仍承担 Bug 职责或最后一位 Owner 的用户。 */
async function handleRemoveMember(userId: number): Promise<void> {
  await runAction(() => workspaceStore.removeMember(userId))
}

/** 把角色枚举转换为页面中文。 */
function roleLabel(role: WorkspaceRole): string {
  return { OWNER: '负责人', ADMIN: '管理员', MEMBER: '成员' }[role]
}

/** 从显示名称生成无图片依赖的头像字符。 */
function avatarText(displayName: string): string {
  return (displayName || 'U').slice(0, 1).toUpperCase()
}

/** 统一维护提交状态和错误提示，避免多个成员操作出现并行重复提交。 */
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
  <main class="members-page">
    <header class="page-heading">
      <div>
        <h1>成员管理</h1>
        <p>管理 {{ workspaceStore.currentWorkspace?.name }} 的成员和空间角色。</p>
      </div>
      <button
        v-if="canManageMembers"
        type="button"
        class="primary-action"
        @click="openMemberDialog"
      >
        <AppIcon name="plus" :size="18" /> 邀请成员
      </button>
    </header>

    <div v-if="errorMessage" class="page-alert">
      <span>{{ errorMessage }}</span>
      <button type="button" aria-label="关闭" @click="errorMessage = ''">
        <AppIcon name="close" :size="16" />
      </button>
    </div>

    <section class="members-card">
      <header class="members-card__header">
        <div>
          <h2>空间成员</h2>
          <span>共 {{ workspaceStore.members.length }} 位成员</span>
        </div>
        <span v-if="!canManageMembers" class="readonly-badge">只读模式</span>
      </header>

      <el-table v-loading="submitting" :data="workspaceStore.members" row-key="userId">
        <el-table-column label="成员" min-width="220">
          <template #default="{ row }">
            <div class="member-identity">
              <span>{{ avatarText(row.displayName) }}</span>
              <div>
                <strong>{{ row.displayName }}</strong
                ><small>@{{ row.username }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户 ID" min-width="150" />
        <el-table-column label="账号状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{
              row.enabled ? '已启用' : '已禁用'
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="空间角色" width="170">
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
            <span v-else>{{ roleLabel(row.role) }}</span>
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
              <template #reference><el-button link type="danger">移除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="memberDialogVisible" title="添加工作空间成员" width="min(92vw, 500px)">
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
        <el-button type="primary" :loading="submitting" @click="handleAddMember"
          >添加成员</el-button
        >
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.members-page {
  max-width: 1180px;
  margin: 0 auto;
}
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 25px;
}
.page-heading h1 {
  margin: 0 0 7px;
  color: #f2f6fb;
  font-size: 29px;
}
.page-heading p {
  margin: 0;
  color: var(--bl-text-secondary);
  font-size: 14px;
}
.primary-action {
  display: inline-flex;
  height: 43px;
  align-items: center;
  gap: 8px;
  padding: 0 19px;
  color: #fff;
  font: inherit;
  cursor: pointer;
  background: linear-gradient(135deg, #1680f7, #247bff);
  border: 1px solid #3993ff;
  border-radius: 8px;
}
.page-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 11px 14px;
  color: #ffaaa5;
  font-size: 13px;
  background: rgb(117 34 38 / 25%);
  border: 1px solid rgb(226 76 76 / 30%);
  border-radius: 8px;
}
.page-alert button {
  color: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
}
.members-card {
  overflow: hidden;
  background: linear-gradient(145deg, #171e26, #141a21);
  border: 1px solid var(--bl-border);
  border-radius: 9px;
}
.members-card__header {
  display: flex;
  min-height: 68px;
  align-items: center;
  justify-content: space-between;
  padding: 0 21px;
  border-bottom: 1px solid var(--bl-border);
}
.members-card__header h2 {
  margin: 0 0 5px;
  color: #edf3fa;
  font-size: 17px;
}
.members-card__header span {
  color: var(--bl-muted);
  font-size: 12px;
}
.readonly-badge {
  padding: 5px 9px;
  background: #222b35;
  border: 1px solid #333e4a;
  border-radius: 6px;
}
.member-identity {
  display: flex;
  align-items: center;
  gap: 11px;
}
.member-identity > span {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, #236fe1, #569ffc);
  border-radius: 50%;
}
.member-identity div {
  display: grid;
  gap: 3px;
}
.member-identity strong {
  color: #dce4ed;
  font-size: 13px;
}
.member-identity small {
  color: #728194;
  font-size: 11px;
}
.form-hint {
  color: var(--bl-muted);
  font-size: 12px;
}
@media (max-width: 620px) {
  .page-heading {
    flex-direction: column;
  }
  .primary-action {
    width: 100%;
    justify-content: center;
  }
}
</style>
