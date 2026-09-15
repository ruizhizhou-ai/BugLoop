<!-- 本文件实现独立空间设置页，负责工作空间基础信息维护、状态停用与重新启用，成员操作由成员管理页承载。 -->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElPopconfirm, ElTag } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/tag/style/css'

import AppIcon from '@/shared/components/AppIcon.vue'
import { useAuthStore } from '@/features/auth/authStore'
import { isApiError } from '@/shared/api/types'
import { formatDateTime } from '@/features/bug/bugMeta'
import type { WorkspaceRole } from './workspaceApi'
import { useWorkspaceStore } from './workspaceStore'

const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const errorMessage = ref('')
const submitting = ref(false)
const editDialogVisible = ref(false)
const editForm = reactive({ name: '', description: '' })

const workspace = computed(() => workspaceStore.currentWorkspace)
const currentRole = computed(() => workspace.value?.currentUserRole)
const isSystemAdmin = computed(() => auth.user?.systemRole === 'SYSTEM_ADMIN')
const canEditWorkspace = computed(
  () =>
    workspaceStore.isEnabled &&
    (isSystemAdmin.value || currentRole.value === 'OWNER' || currentRole.value === 'ADMIN'),
)
const canDisableWorkspace = computed(
  () => workspaceStore.isEnabled && (isSystemAdmin.value || currentRole.value === 'OWNER'),
)
const canEnableWorkspace = computed(
  () =>
    workspace.value?.status === 'DISABLED' &&
    (isSystemAdmin.value || currentRole.value === 'OWNER'),
)

/** 使用服务端最新空间信息回填编辑弹窗。 */
function openEditDialog(): void {
  if (!workspace.value) return
  editForm.name = workspace.value.name
  editForm.description = workspace.value.description ?? ''
  editDialogVisible.value = true
}

/** 保存空间名称和说明，空说明统一转为 null 与后端协议保持一致。 */
async function handleUpdateWorkspace(): Promise<void> {
  const name = editForm.name.trim()
  if (!name) {
    errorMessage.value = '请输入工作空间名称'
    return
  }
  const succeeded = await runAction(() =>
    workspaceStore.update({ name, description: editForm.description.trim() || null }),
  )
  if (succeeded) editDialogVisible.value = false
}

/** 停用空间后保留历史数据只读能力，页面会立即隐藏写操作。 */
async function handleDisableWorkspace(): Promise<void> {
  await runAction(() => workspaceStore.disable())
}

/** 重新启用空间并恢复业务写入入口。 */
async function handleEnableWorkspace(): Promise<void> {
  await runAction(() => workspaceStore.enable())
}

/** 把角色枚举转换为界面中文。 */
function roleLabel(role: WorkspaceRole | null | undefined): string {
  return { OWNER: '负责人', ADMIN: '管理员', MEMBER: '成员' }[role ?? 'MEMBER']
}

/** 统一执行空间写操作并向用户展示后端业务错误。 */
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
  <main class="settings-page">
    <header class="page-heading">
      <div>
        <h1>空间设置</h1>
        <p>维护工作空间的名称、说明和可用状态。</p>
      </div>
      <button v-if="canEditWorkspace" type="button" class="primary-action" @click="openEditDialog">
        <AppIcon name="edit" :size="17" /> 编辑设置
      </button>
    </header>

    <div v-if="errorMessage" class="page-alert">
      <span>{{ errorMessage }}</span>
      <button type="button" aria-label="关闭" @click="errorMessage = ''">
        <AppIcon name="close" :size="16" />
      </button>
    </div>

    <section v-if="workspace" class="settings-card workspace-overview">
      <div class="workspace-overview__identity">
        <span>{{ workspace.name.slice(0, 1).toUpperCase() }}</span>
        <div>
          <div class="workspace-overview__title">
            <h2>{{ workspace.name }}</h2>
            <el-tag :type="workspaceStore.isEnabled ? 'success' : 'info'" size="small">
              {{ workspaceStore.isEnabled ? '运行中' : '已停用' }}
            </el-tag>
          </div>
          <p>{{ workspace.description || '暂无工作空间说明' }}</p>
        </div>
      </div>
      <dl>
        <div>
          <dt>空间 ID</dt>
          <dd>{{ workspace.id }}</dd>
        </div>
        <div>
          <dt>我的角色</dt>
          <dd>{{ roleLabel(currentRole) }}</dd>
        </div>
        <div>
          <dt>成员数量</dt>
          <dd>{{ workspaceStore.members.length }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ formatDateTime(workspace.createdAt) }}</dd>
        </div>
      </dl>
    </section>

    <section class="settings-card settings-section">
      <header>
        <div>
          <h2>基础信息</h2>
          <p>工作空间名称和说明会显示在成员的首页概览中。</p>
        </div>
      </header>
      <div class="settings-row">
        <span>空间名称</span><strong>{{ workspace?.name || '-' }}</strong>
      </div>
      <div class="settings-row">
        <span>空间说明</span><strong>{{ workspace?.description || '未填写' }}</strong>
      </div>
    </section>

    <section class="settings-card settings-section danger-zone">
      <header>
        <div>
          <h2>空间状态</h2>
          <p>停用后所有 Bug 和成员数据仍可查看，但不允许任何写入操作。</p>
        </div>
      </header>
      <div class="danger-zone__action">
        <div>
          <strong>{{ workspaceStore.isEnabled ? '停用工作空间' : '重新启用工作空间' }}</strong>
          <p>
            {{
              workspaceStore.isEnabled
                ? '适用于项目暂停或归档，历史记录不会被删除。'
                : '恢复创建、编辑、指派及验收等业务能力。'
            }}
          </p>
        </div>
        <el-popconfirm
          v-if="canDisableWorkspace"
          title="停用后将只允许查看历史数据，确认继续吗？"
          confirm-button-text="确认停用"
          cancel-button-text="取消"
          @confirm="handleDisableWorkspace"
        >
          <template #reference><el-button type="danger" plain>停用工作空间</el-button></template>
        </el-popconfirm>
        <el-button
          v-if="canEnableWorkspace"
          type="success"
          :loading="submitting"
          @click="handleEnableWorkspace"
        >
          重新启用
        </el-button>
        <span v-if="!canDisableWorkspace && !canEnableWorkspace" class="permission-hint"
          >仅空间负责人可操作</span
        >
      </div>
    </section>

    <el-dialog v-model="editDialogVisible" title="编辑工作空间" width="min(92vw, 520px)">
      <el-form label-position="top" @submit.prevent="handleUpdateWorkspace">
        <el-form-item label="名称" required
          ><el-input v-model="editForm.name" maxlength="100" show-word-limit
        /></el-form-item>
        <el-form-item label="说明"
          ><el-input
            v-model="editForm.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleUpdateWorkspace"
          >保存</el-button
        >
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.settings-page {
  max-width: 1060px;
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
  font-size: 13px;
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
.settings-card {
  margin-bottom: 17px;
  background: linear-gradient(145deg, #171e26, #141a21);
  border: 1px solid var(--bl-border);
  border-radius: 9px;
}
.workspace-overview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 22px 30px;
  padding: 24px;
}
.workspace-overview__identity {
  display: flex;
  flex: 1 1 260px;
  min-width: 260px;
  align-items: center;
  gap: 15px;
}
.workspace-overview__identity > span {
  display: grid;
  width: 52px;
  height: 52px;
  flex: 0 0 auto;
  place-items: center;
  color: white;
  font-size: 20px;
  background: linear-gradient(145deg, #1f80e9, #23a7dd);
  border-radius: 9px;
}
.workspace-overview__title {
  display: flex;
  align-items: center;
  gap: 9px;
}
.workspace-overview h2 {
  margin: 0;
  color: #edf3fa;
  font-size: 19px;
}
.workspace-overview p {
  margin: 7px 0 0;
  color: var(--bl-muted);
  font-size: 12px;
}
/* 四个字段并排需要约 520px，空间不足时整组换到下一行，避免被逐列压成竖排文字或时间折行。 */
.workspace-overview dl {
  display: grid;
  flex: 1 1 520px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
}
.workspace-overview dl div {
  padding-left: 20px;
  border-left: 1px solid var(--bl-border);
}
.workspace-overview dt {
  color: var(--bl-muted);
  font-size: 11px;
}
.workspace-overview dd {
  margin: 7px 0 0;
  color: #d9e1eb;
  font-size: 13px;
}
.settings-section > header {
  display: flex;
  min-height: 76px;
  align-items: center;
  padding: 0 23px;
  border-bottom: 1px solid var(--bl-border);
}
.settings-section h2 {
  margin: 0;
  color: #eaf0f7;
  font-size: 16px;
}
.settings-section header p {
  margin: 7px 0 0;
  color: var(--bl-muted);
  font-size: 12px;
}
.settings-row {
  display: grid;
  grid-template-columns: 190px 1fr;
  gap: 18px;
  padding: 17px 23px;
  border-bottom: 1px solid rgb(43 52 63 / 70%);
}
.settings-row:last-child {
  border-bottom: 0;
}
.settings-row span {
  color: var(--bl-text-secondary);
  font-size: 13px;
}
.settings-row strong {
  color: #d9e1eb;
  font-size: 13px;
  font-weight: 500;
}
.danger-zone {
  border-color: rgb(154 59 59 / 48%);
}
.danger-zone h2 {
  color: #ff8881;
}
.danger-zone__action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 25px;
  padding: 20px 23px;
}
.danger-zone__action strong {
  color: #e3eaf3;
  font-size: 13px;
}
.danger-zone__action p {
  margin: 6px 0 0;
  color: var(--bl-muted);
  font-size: 11px;
}
.permission-hint {
  color: var(--bl-muted);
  font-size: 12px;
}
@media (max-width: 850px) {
  .workspace-overview {
    align-items: flex-start;
    flex-direction: column;
  }
  .workspace-overview dl {
    width: 100%;
    flex-basis: auto;
  }
}
@media (max-width: 620px) {
  .page-heading,
  .danger-zone__action {
    align-items: stretch;
    flex-direction: column;
  }
  .primary-action {
    justify-content: center;
  }
  .workspace-overview dl {
    grid-template-columns: repeat(2, 1fr);
    gap: 20px 0;
  }
  .workspace-overview dl div:nth-child(odd) {
    border-left: 0;
    padding-left: 0;
  }
  .settings-row {
    grid-template-columns: 1fr;
    gap: 7px;
  }
}
</style>
