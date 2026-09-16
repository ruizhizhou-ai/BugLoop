<!-- 本文件提供系统管理员专属的用户列表和创建入口，普通用户由路由守卫与后端权限双重拦截。 -->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElPopconfirm,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import AppIcon from '@/shared/components/AppIcon.vue'
import { isApiError } from '@/shared/api/types'
import { createSystemUser, disableSystemUser, enableSystemUser, fetchSystemUsers } from './userApi'
import type { SystemUser } from './userApi'

const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,64}$/

const users = ref<SystemUser[]>([])
const loading = ref(false)
const submitting = ref(false)
const togglingUserId = ref<number | null>(null)
const dialogVisible = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const formRef = ref<FormInstance>()

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
    { required: true, message: '请输入初始密码', trigger: 'blur' },
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

onMounted(() => void loadUsers())

/** 获取系统用户列表，失败时保留页面结构并提供可关闭的错误提示。 */
async function loadUsers(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    users.value = await fetchSystemUsers()
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载用户列表失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 校验账号资料后创建普通用户，成功时直接把服务端结果加入列表。 */
async function handleCreateUser(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const created = await createSystemUser({
      username: form.username,
      displayName: form.displayName,
      password: form.password,
    })
    users.value = [created, ...users.value]
    dialogVisible.value = false
    successMessage.value = `用户 ${created.displayName} 创建成功`
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '创建用户失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

/** 停用或启用普通用户，成功后用服务端返回结果就地替换列表行。 */
async function handleToggleEnabled(user: SystemUser): Promise<void> {
  togglingUserId.value = user.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = user.enabled ? await disableSystemUser(user.id) : await enableSystemUser(user.id)
    users.value = users.value.map((item) => (item.id === updated.id ? updated : item))
    successMessage.value = updated.enabled
      ? `用户 ${updated.displayName} 已启用，可正常登录`
      : `用户 ${updated.displayName} 已停用，将无法登录`
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '操作失败，请稍后重试'
  } finally {
    togglingUserId.value = null
  }
}

/** 关闭弹窗后移除敏感密码输入，避免再次打开时残留。 */
function resetForm(): void {
  form.username = ''
  form.displayName = ''
  form.password = ''
  form.confirmPassword = ''
  formRef.value?.clearValidate()
}

/** 将系统角色转换为页面文案。 */
function roleLabel(role: SystemUser['systemRole']): string {
  return role === 'SYSTEM_ADMIN' ? '系统管理员' : '普通用户'
}

/** 使用显示名称首字符生成与现有成员页一致的轻量头像。 */
function avatarText(displayName: string): string {
  return (displayName || 'U').slice(0, 1).toUpperCase()
}

/** 按当前中文界面格式化创建时间。 */
function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}
</script>

<template>
  <main class="system-users-page">
    <header class="page-heading">
      <div>
        <span class="page-eyebrow">系统管理</span>
        <h1>用户管理</h1>
        <p>创建可登录 BugLoop 的普通用户，并查看当前系统账号。</p>
      </div>
      <button type="button" class="primary-action" @click="dialogVisible = true">
        <AppIcon name="plus" :size="18" /> 创建用户
      </button>
    </header>

    <div v-if="errorMessage" class="page-alert page-alert--error">
      <span>{{ errorMessage }}</span>
      <button type="button" aria-label="关闭" @click="errorMessage = ''">
        <AppIcon name="close" :size="16" />
      </button>
    </div>
    <div v-if="successMessage" class="page-alert page-alert--success">
      <span>{{ successMessage }}</span>
      <button type="button" aria-label="关闭" @click="successMessage = ''">
        <AppIcon name="close" :size="16" />
      </button>
    </div>

    <section class="members-card system-users-card">
      <header class="system-users-card__header">
        <div>
          <h2>系统用户</h2>
          <span>共 {{ users.length }} 个账号</span>
        </div>
        <span class="security-note">新建账号固定为普通用户</span>
      </header>

      <el-table v-loading="loading" :data="users" row-key="id">
        <el-table-column label="用户" min-width="230">
          <template #default="{ row }">
            <div class="user-identity">
              <span>{{ avatarText(row.displayName) }}</span>
              <div>
                <strong>{{ row.displayName }}</strong>
                <small>@{{ row.username }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="用户 ID" min-width="170" />
        <el-table-column label="系统角色" width="150">
          <template #default="{ row }">
            <el-tag :type="row.systemRole === 'SYSTEM_ADMIN' ? 'primary' : 'info'" size="small">
              {{ roleLabel(row.systemRole) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-popconfirm
              v-if="row.systemRole === 'USER' && row.enabled"
              title="停用后该账号将无法登录，确认继续吗？"
              confirm-button-text="确认停用"
              cancel-button-text="取消"
              width="240"
              @confirm="handleToggleEnabled(row as SystemUser)"
            >
              <template #reference>
                <el-button link type="danger" :loading="togglingUserId === row.id">停用</el-button>
              </template>
            </el-popconfirm>
            <el-button
              v-else-if="row.systemRole === 'USER'"
              link
              type="primary"
              :loading="togglingUserId === row.id"
              @click="handleToggleEnabled(row as SystemUser)"
            >
              启用
            </el-button>
            <span v-else class="row-hint">—</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      title="创建新用户"
      width="min(92vw, 520px)"
      append-to-body
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleCreateUser"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="3-64 位字母、数字或下划线"
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" placeholder="展示给其他成员的名字" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
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
            @keyup.enter="handleCreateUser"
          />
        </el-form-item>
      </el-form>
      <p class="dialog-hint">创建后账号立即启用，用户可使用该用户名和初始密码登录。</p>
      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreateUser">
          创建用户
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.system-users-page {
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

.page-eyebrow {
  display: block;
  margin-bottom: 7px;
  color: var(--bl-primary-light);
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.08em;
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
  box-shadow: 0 8px 22px rgb(32 126 245 / 16%);
}

.page-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 11px 14px;
  font-size: 13px;
  border: 1px solid;
  border-radius: 8px;
}

.page-alert--error {
  color: #ffaaa5;
  background: rgb(117 34 38 / 25%);
  border-color: rgb(226 76 76 / 30%);
}

.page-alert--success {
  color: #75dea4;
  background: rgb(26 101 64 / 22%);
  border-color: rgb(55 187 113 / 28%);
}

.page-alert button {
  color: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.system-users-card {
  overflow: hidden;
  background: linear-gradient(145deg, #171e26, #141a21);
  border: 1px solid var(--bl-border);
  border-radius: 9px;
}

.system-users-card__header {
  display: flex;
  min-height: 68px;
  align-items: center;
  justify-content: space-between;
  padding: 0 21px;
  border-bottom: 1px solid var(--bl-border);
}

.system-users-card__header h2 {
  margin: 0 0 5px;
  color: #edf3fa;
  font-size: 17px;
}

.system-users-card__header span {
  color: var(--bl-muted);
  font-size: 12px;
}

.security-note {
  padding: 5px 9px;
  color: #7eb9f7 !important;
  background: rgb(39 126 216 / 12%);
  border: 1px solid rgb(57 144 235 / 18%);
  border-radius: 999px;
}

.user-identity {
  display: flex;
  align-items: center;
  gap: 11px;
}

.user-identity > span {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  color: #fff;
  background: linear-gradient(145deg, #1976e9, #3a99ff);
  border-radius: 50%;
}

.user-identity div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.user-identity strong {
  color: var(--bl-text);
  font-size: 13px;
}

.user-identity small {
  color: var(--bl-muted);
  font-size: 11px;
}

.row-hint {
  color: var(--bl-muted);
  font-size: 12px;
}

.dialog-hint {
  margin: 0;
  color: var(--bl-muted);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 700px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .primary-action {
    align-self: flex-start;
  }

  .security-note {
    display: none;
  }
}
</style>
