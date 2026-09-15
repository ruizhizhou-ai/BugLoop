<!-- 本文件实现 Bug 详情页：完整信息展示、按角色与状态控制的操作按钮，以及各项业务弹窗。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/descriptions-item/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'
import { MdEditor, MdPreview } from 'md-editor-v3'
// style.css 同时包含编辑器与预览样式；preview.css 不含编辑器样式，单独引入会导致弹窗内编辑器错乱。
import 'md-editor-v3/lib/style.css'

import { useBugStore } from './bugStore'
import {
  BUG_PRIORITY_OPTIONS,
  PRIORITY_META,
  STATUS_META,
  formatDateTime,
  formatFileSize,
} from './bugMeta'
import type { BugPriority } from './bugApi'
import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'
import { isApiError } from '@/shared/api/types'

const route = useRoute()
const router = useRouter()
const bugStore = useBugStore()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const errorMessage = ref('')
const acceptDialogVisible = ref(false)
const acceptMode = ref<'accept' | 'reject'>('accept')
const fixDialogVisible = ref(false)
const personDialogVisible = ref(false)
const infoDialogVisible = ref(false)
const previewTab = ref<'comments' | 'logs' | 'history'>('comments')

const acceptForm = reactive({ commentMd: '' })
const fixForm = reactive({ fixDescriptionMd: '' })
const personForm = reactive<{ userId: number | undefined }>({ userId: undefined })
const infoForm = reactive({ title: '', descriptionMd: '', priority: 'P2' as BugPriority })
const personTarget = ref<'assignee' | 'acceptor'>('assignee')

const bugId = computed(() => Number(route.params.bugId))
const workspaceId = computed(() => Number(route.params.workspaceId))
const bug = computed(() => bugStore.current)

const currentUserId = computed(() => auth.user?.id)
const isAssignee = computed(() => bug.value?.assigneeId === currentUserId.value)
const isAcceptor = computed(() => bug.value?.acceptorId === currentUserId.value)
const isCreator = computed(() => bug.value?.creatorId === currentUserId.value)
const isManager = computed(() => {
  const role =
    bug.value?.workspace.currentUserRole ?? workspaceStore.currentWorkspace?.currentUserRole
  return auth.user?.systemRole === 'SYSTEM_ADMIN' || role === 'OWNER' || role === 'ADMIN'
})
const mutable = computed(
  () => workspaceStore.isEnabled && bug.value?.workspace.status === 'ENABLED',
)

const canStart = computed(
  () => mutable.value && isAssignee.value && ['TODO', 'REOPENED'].includes(bug.value?.status ?? ''),
)
const canEditFix = computed(
  () =>
    mutable.value &&
    isAssignee.value &&
    ['PROCESSING', 'REOPENED'].includes(bug.value?.status ?? ''),
)
const canSubmit = computed(
  () => mutable.value && isAssignee.value && bug.value?.status === 'PROCESSING',
)
const canAccept = computed(
  () => mutable.value && isAcceptor.value && bug.value?.status === 'WAIT_ACCEPTANCE',
)
const canManagePeople = computed(
  () =>
    mutable.value &&
    isManager.value &&
    ['TODO', 'PROCESSING', 'REOPENED'].includes(bug.value?.status ?? ''),
)
const canEditBasic = computed(
  () => mutable.value && (isManager.value || isCreator.value) && bug.value?.status !== 'CLOSED',
)

onMounted(() => {
  void loadDetail()
})

async function loadDetail(): Promise<void> {
  try {
    await bugStore.loadDetail(bugId.value)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载 Bug 详情失败'
  }
}

/** 统一执行写操作并展示业务错误；版本冲突额外提示刷新。 */
async function runAction(action: () => Promise<void>): Promise<boolean> {
  errorMessage.value = ''
  try {
    await action()
    return true
  } catch (error) {
    if (bugStore.isVersionConflict(error)) {
      // 先刷新服务端最新内容，再提示冲突，避免刷新过程覆盖提示。
      await loadDetail()
      errorMessage.value = '数据已被其他用户修改，已为你刷新最新内容'
      return false
    }
    errorMessage.value = isApiError(error) ? error.message : '操作失败，请稍后重试'
    return false
  }
}

async function handleStart(): Promise<void> {
  await runAction(() => bugStore.start(bugId.value))
}

/** 打开修复说明弹窗并回填当前草稿。 */
function openFixDialog(): void {
  fixForm.fixDescriptionMd = bug.value?.fixDescriptionMd ?? ''
  fixDialogVisible.value = true
}

async function handleSaveFix(): Promise<void> {
  const succeeded = await runAction(() => bugStore.saveFix(bugId.value, fixForm.fixDescriptionMd))
  if (succeeded) {
    fixDialogVisible.value = false
  }
}

async function handleSubmit(): Promise<void> {
  await runAction(() => bugStore.submit(bugId.value))
}

function openAcceptDialog(mode: 'accept' | 'reject'): void {
  acceptMode.value = mode
  acceptForm.commentMd = ''
  acceptDialogVisible.value = true
}

async function handleAcceptance(): Promise<void> {
  const comment = acceptForm.commentMd.trim()
  if (acceptMode.value === 'reject' && !comment) {
    errorMessage.value = '验收驳回原因不能为空'
    return
  }
  const succeeded = await runAction(() =>
    acceptMode.value === 'accept'
      ? bugStore.accept(bugId.value, comment || null)
      : bugStore.reject(bugId.value, comment),
  )
  if (succeeded) {
    acceptDialogVisible.value = false
  }
}

function openInfoDialog(): void {
  if (!bug.value) {
    return
  }
  infoForm.title = bug.value.title
  infoForm.descriptionMd = bug.value.descriptionMd
  infoForm.priority = bug.value.priority
  infoDialogVisible.value = true
}

async function handleUpdateBasic(): Promise<void> {
  if (!bug.value) {
    return
  }
  if (!infoForm.title.trim()) {
    errorMessage.value = '请输入标题'
    return
  }
  const succeeded = await runAction(() =>
    bugStore.updateBasic(bugId.value, {
      title: infoForm.title.trim(),
      descriptionMd: infoForm.descriptionMd,
      priority: infoForm.priority,
      version: bug.value!.version,
    }),
  )
  if (succeeded) {
    infoDialogVisible.value = false
  }
}

function openPersonDialog(assignee: boolean): void {
  if (!bug.value) {
    return
  }
  personTarget.value = assignee ? 'assignee' : 'acceptor'
  personForm.userId = (assignee ? bug.value.assigneeId : bug.value.acceptorId) ?? undefined
  personDialogVisible.value = true
}

async function handleSavePerson(): Promise<void> {
  if (!personForm.userId) {
    errorMessage.value = '请选择成员'
    return
  }
  const succeeded = await runAction(() =>
    personTarget.value === 'assignee'
      ? bugStore.assign(bugId.value, personForm.userId!)
      : bugStore.setAcceptor(bugId.value, personForm.userId!),
  )
  if (succeeded) {
    personDialogVisible.value = false
  }
}

function goBack(): void {
  void router.push({ name: 'bug-list', params: { workspaceId: workspaceId.value } })
}
</script>

<template>
  <main class="bug-detail">
    <el-alert
      v-if="errorMessage"
      class="bug-detail__alert"
      :title="errorMessage"
      type="error"
      :closable="true"
      show-icon
      @close="errorMessage = ''"
    />

    <el-card v-if="!bug" shadow="never">
      <el-empty description="Bug 不存在或无权访问">
        <el-button @click="goBack">返回列表</el-button>
      </el-empty>
    </el-card>

    <template v-else>
      <el-card class="bug-detail__summary" shadow="never">
        <div class="bug-detail__title-row">
          <div class="bug-detail__title-main">
            <span class="bug-detail__no">{{ bug.bugNo }}</span>
            <h2>{{ bug.title }}</h2>
          </div>
          <div class="bug-detail__actions">
            <el-button
              v-if="canStart"
              type="primary"
              :loading="bugStore.submitting"
              @click="handleStart"
            >
              开始处理
            </el-button>
            <el-button
              v-if="canEditFix"
              type="primary"
              :loading="bugStore.submitting"
              @click="openFixDialog"
            >
              {{
                bug.status === 'REOPENED' && !bug.fixDescriptionMd ? '填写修复说明' : '编辑修复说明'
              }}
            </el-button>
            <el-button
              v-if="canSubmit"
              type="warning"
              :loading="bugStore.submitting"
              @click="handleSubmit"
            >
              提交验收
            </el-button>
            <template v-if="canAccept">
              <el-button type="success" @click="openAcceptDialog('accept')">验收通过</el-button>
              <el-button type="danger" plain @click="openAcceptDialog('reject')"
                >验收驳回</el-button
              >
            </template>
            <el-button v-if="canManagePeople" @click="openPersonDialog(true)">指派</el-button>
            <el-button v-if="canManagePeople" @click="openPersonDialog(false)"
              >修改验收人</el-button
            >
            <el-button v-if="canEditBasic" @click="openInfoDialog">编辑信息</el-button>
            <el-button @click="goBack">返回列表</el-button>
          </div>
        </div>

        <div class="bug-detail__meta">
          <el-tag :type="STATUS_META[bug.status].tag">{{ STATUS_META[bug.status].label }}</el-tag>
          <el-tag :type="PRIORITY_META[bug.priority].tag">{{
            PRIORITY_META[bug.priority].label
          }}</el-tag>
          <el-tag v-if="bug.status === 'REOPENED'" type="warning" effect="plain">
            重新打开 {{ bug.reopenCount }} 次
          </el-tag>
          <span class="bug-detail__meta-item">提交人：{{ bug.creator?.displayName ?? '-' }}</span>
          <span class="bug-detail__meta-item"
            >负责人：{{ bug.assignee?.displayName ?? '未指定' }}</span
          >
          <span class="bug-detail__meta-item">验收人：{{ bug.acceptor?.displayName ?? '-' }}</span>
          <span class="bug-detail__meta-item">创建：{{ formatDateTime(bug.createdAt) }}</span>
          <span class="bug-detail__meta-item">更新：{{ formatDateTime(bug.updatedAt) }}</span>
          <span v-if="bug.closedAt" class="bug-detail__meta-item"
            >关闭：{{ formatDateTime(bug.closedAt) }}</span
          >
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header><h3>问题描述</h3></template>
        <md-preview :model-value="bug.descriptionMd" preview-theme="github" />
      </el-card>

      <el-card v-if="bug.fixDescriptionMd" shadow="never">
        <template #header><h3>修复说明</h3></template>
        <md-preview :model-value="bug.fixDescriptionMd" preview-theme="github" />
      </el-card>

      <el-card v-if="bug.latestAcceptance" shadow="never">
        <template #header><h3>最近验收记录</h3></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="结果">
            <el-tag
              :type="bug.latestAcceptance.result === 'PASS' ? 'success' : 'danger'"
              size="small"
            >
              {{ bug.latestAcceptance.result === 'PASS' ? '通过' : '驳回' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="验收时间">
            {{ formatDateTime(bug.latestAcceptance.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态变化" :span="2">
            {{ STATUS_META[bug.latestAcceptance.fromStatus].label }} →
            {{ STATUS_META[bug.latestAcceptance.toStatus].label }}
          </el-descriptions-item>
          <el-descriptions-item v-if="bug.latestAcceptance.commentMd" label="验收意见" :span="2">
            {{ bug.latestAcceptance.commentMd }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header><h3>附件</h3></template>
        <el-table v-if="bug.attachments.length" :data="bug.attachments" row-key="id">
          <el-table-column prop="originalName" label="文件名" min-width="240" />
          <el-table-column label="大小" width="120">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="附件上传能力将在后续版本开放" :image-size="72" />
      </el-card>

      <section class="detail-preview">
        <header class="detail-preview__tabs">
          <button
            type="button"
            :class="{ active: previewTab === 'comments' }"
            @click="previewTab = 'comments'"
          >
            评论
          </button>
          <button
            type="button"
            :class="{ active: previewTab === 'logs' }"
            @click="previewTab = 'logs'"
          >
            操作日志
          </button>
          <button
            type="button"
            :class="{ active: previewTab === 'history' }"
            @click="previewTab = 'history'"
          >
            文档历史
          </button>
          <span>后续能力预览</span>
        </header>
        <div class="detail-preview__body">
          <span class="detail-preview__avatar">{{
            (auth.user?.displayName || 'U').slice(0, 1)
          }}</span>
          <div>
            <strong>{{
              previewTab === 'comments'
                ? '参与问题讨论'
                : previewTab === 'logs'
                  ? '追踪每次状态变化'
                  : '查看描述修订记录'
            }}</strong>
            <p>
              {{
                previewTab === 'comments'
                  ? '评论编辑器与消息提醒将在后续里程碑接入。'
                  : previewTab === 'logs'
                    ? '审计日志接口接入后会按时间线展示操作者、动作和状态变化。'
                    : '历史版本接口接入后可对比并恢复问题描述。'
              }}
            </p>
          </div>
          <button type="button" disabled>
            {{ previewTab === 'comments' ? '发表评论' : '查看完整记录' }}
          </button>
        </div>
      </section>
    </template>

    <el-dialog
      v-model="fixDialogVisible"
      :title="bug?.fixDescriptionMd ? '编辑修复说明' : '填写修复说明'"
      width="min(92vw, 860px)"
    >
      <md-editor v-model="fixForm.fixDescriptionMd" />
      <template #footer>
        <el-button @click="fixDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bugStore.submitting" @click="handleSaveFix"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="acceptDialogVisible"
      :title="acceptMode === 'accept' ? '验收通过' : '验收驳回'"
      width="min(92vw, 520px)"
    >
      <el-form label-position="top" @submit.prevent="handleAcceptance">
        <el-form-item
          :label="acceptMode === 'accept' ? '验收意见（可选）' : '驳回原因（必填）'"
          required
        >
          <el-input
            v-model="acceptForm.commentMd"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptDialogVisible = false">取消</el-button>
        <el-button
          :type="acceptMode === 'accept' ? 'success' : 'danger'"
          :loading="bugStore.submitting"
          @click="handleAcceptance"
        >
          {{ acceptMode === 'accept' ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="personDialogVisible"
      :title="personTarget === 'assignee' ? '指派负责人' : '修改验收人'"
      width="min(92vw, 480px)"
    >
      <el-form label-position="top" @submit.prevent="handleSavePerson">
        <el-form-item label="选择成员" required>
          <el-select v-model="personForm.userId" filterable placeholder="请选择工作空间成员">
            <el-option
              v-for="member in workspaceStore.members"
              :key="member.userId"
              :label="member.displayName"
              :value="member.userId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="personDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bugStore.submitting" @click="handleSavePerson"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog v-model="infoDialogVisible" title="编辑基础信息" width="min(92vw, 860px)">
      <el-form label-position="top" @submit.prevent="handleUpdateBasic">
        <el-form-item label="标题" required>
          <el-input v-model="infoForm.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="详细说明（Markdown）" required>
          <md-editor v-model="infoForm.descriptionMd" />
        </el-form-item>
        <el-form-item label="优先级" required>
          <el-select v-model="infoForm.priority">
            <el-option
              v-for="option in BUG_PRIORITY_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="infoDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bugStore.submitting" @click="handleUpdateBasic"
          >保存</el-button
        >
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.bug-detail {
  max-width: 1260px;
  margin: 0 auto;
}

.bug-detail__alert {
  margin-bottom: 16px;
}

.bug-detail :deep(.el-card) {
  margin-bottom: 16px;
}

.bug-detail :deep(.el-card__header h3) {
  margin: 0;
  font-size: 15px;
}

.bug-detail__title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.bug-detail__title-main {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.bug-detail__title-main h2 {
  margin: 0;
  font-size: 18px;
  word-break: break-word;
}

.bug-detail__no {
  font-family: monospace;
  color: #909399;
  flex-shrink: 0;
}

.bug-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.bug-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  color: var(--bl-text-secondary);
  font-size: 13px;
}

.detail-preview {
  margin-bottom: 16px;
  overflow: hidden;
  background: linear-gradient(145deg, #171e26, #141a21);
  border: 1px solid var(--bl-border);
  border-radius: 9px;
}

.detail-preview__tabs {
  display: flex;
  min-height: 54px;
  align-items: stretch;
  padding: 0 18px;
  border-bottom: 1px solid var(--bl-border);
}

.detail-preview__tabs button {
  position: relative;
  padding: 0 14px;
  color: var(--bl-text-secondary);
  font: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.detail-preview__tabs button.active {
  color: var(--bl-primary-light);
}

.detail-preview__tabs button.active::after {
  position: absolute;
  right: 12px;
  bottom: 0;
  left: 12px;
  height: 2px;
  content: '';
  background: var(--bl-primary);
}

.detail-preview__tabs span {
  align-self: center;
  margin-left: auto;
  color: var(--bl-muted);
  font-size: 11px;
}

.detail-preview__body {
  display: grid;
  grid-template-columns: 42px 1fr auto;
  align-items: center;
  gap: 14px;
  padding: 22px;
}

.detail-preview__avatar {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, #257be8, #4ca0ff);
  border-radius: 50%;
}

.detail-preview__body strong {
  color: var(--bl-text);
  font-size: 13px;
}

.detail-preview__body p {
  margin: 6px 0 0;
  color: var(--bl-muted);
  font-size: 12px;
}

.detail-preview__body > button {
  padding: 8px 13px;
  color: var(--bl-text-secondary);
  background: var(--bl-control-bg);
  border: 1px solid var(--bl-control-border);
  border-radius: 6px;
}

@media (max-width: 650px) {
  .detail-preview__body {
    grid-template-columns: 42px 1fr;
  }

  .detail-preview__body > button {
    grid-column: 1 / -1;
  }
}
</style>
