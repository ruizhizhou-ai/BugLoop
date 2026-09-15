<!-- 本文件实现 Bug 详情页：完整信息展示、按角色与状态控制的操作按钮，以及各项业务弹窗。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
  ElTimeline,
  ElTimelineItem,
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
import 'element-plus/es/components/timeline/style/css'
import 'element-plus/es/components/timeline-item/style/css'
import { MdEditor, MdPreview } from 'md-editor-v3'
import type { ToolbarNames } from 'md-editor-v3'
// style.css 同时包含编辑器与预览样式；preview.css 不含编辑器样式，单独引入会导致弹窗内编辑器错乱。
import 'md-editor-v3/lib/style.css'

import { useBugStore } from './bugStore'
import {
  ATTACHMENT_ACCEPT,
  BUG_PRIORITY_OPTIONS,
  PRIORITY_META,
  STATUS_META,
  attachmentValidationError,
  formatDateTime,
  formatFileSize,
} from './bugMeta'
import type { BugAttachment, BugPriority } from './bugApi'
import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'
import { downloadFile } from '@/shared/api/http'
import { isApiError } from '@/shared/api/types'

interface BugDetailViewProps {
  /** 抽屉场景传入 Bug 主键；独立详情页未传时仍从路由读取。 */
  bugId?: number
  /** 控制详情在右侧抽屉中使用紧凑、无卡片堆叠的排版。 */
  drawerMode?: boolean
}

const props = withDefaults(defineProps<BugDetailViewProps>(), {
  bugId: undefined,
  drawerMode: false,
})
const emit = defineEmits<{
  close: []
}>()

const route = useRoute()
const router = useRouter()
const bugStore = useBugStore()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const COMMENT_TOOLBARS: ToolbarNames[] = [
  'bold',
  'italic',
  'quote',
  'unorderedList',
  'orderedList',
  'code',
  'link',
  'preview',
]

const errorMessage = ref('')
const acceptDialogVisible = ref(false)
const acceptMode = ref<'accept' | 'reject'>('accept')
const fixDialogVisible = ref(false)
const personDialogVisible = ref(false)
const infoDialogVisible = ref(false)
const previewTab = ref<'comments' | 'logs' | 'history'>('comments')
const commentDraft = ref('')
const attachmentInput = ref<HTMLInputElement | null>(null)

const acceptForm = reactive({ commentMd: '' })
const fixForm = reactive({ fixDescriptionMd: '' })
const personForm = reactive<{ userId: number | undefined }>({ userId: undefined })
const infoForm = reactive({ title: '', descriptionMd: '', priority: 'P2' as BugPriority })
const personTarget = ref<'assignee' | 'acceptor'>('assignee')

const resolvedBugId = computed(() => props.bugId ?? Number(route.params.bugId))
const workspaceId = computed(() => Number(route.params.workspaceId))
// 切换抽屉中的 Bug 时不展示上一个详情，等待新请求返回后再渲染。
const bug = computed(() => (bugStore.current?.id === resolvedBugId.value ? bugStore.current : null))

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
const canWriteAttachments = computed(() => mutable.value && bug.value?.status !== 'CLOSED')
const canComment = computed(() => mutable.value)
const hasMoreComments = computed(() => bugStore.comments.length < bugStore.commentsTotal)
const historyDialogVisible = computed({
  get: () => bugStore.historyDetail !== null,
  set: (visible: boolean) => {
    if (!visible) bugStore.closeHistoryDetail()
  },
})

watch(
  resolvedBugId,
  (value) => {
    if (!Number.isFinite(value) || value <= 0) {
      return
    }
    // 同一抽屉可连续切换不同 Bug，每次都重置局部输入并加载对应追溯数据。
    errorMessage.value = ''
    commentDraft.value = ''
    previewTab.value = 'comments'
    void loadDetail()
    void loadComments()
    void loadTrace()
  },
  { immediate: true },
)

async function loadDetail(): Promise<void> {
  try {
    await bugStore.loadDetail(resolvedBugId.value)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载 Bug 详情失败'
  }
}

async function loadComments(): Promise<void> {
  try {
    await bugStore.loadComments(resolvedBugId.value, 1)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载评论失败'
  }
}

async function loadTrace(): Promise<void> {
  try {
    await bugStore.loadTrace(resolvedBugId.value)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载追溯记录失败'
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
  await runAction(() => bugStore.start(resolvedBugId.value))
}

/** 打开修复说明弹窗并回填当前草稿。 */
function openFixDialog(): void {
  fixForm.fixDescriptionMd = bug.value?.fixDescriptionMd ?? ''
  fixDialogVisible.value = true
}

async function handleSaveFix(): Promise<void> {
  const succeeded = await runAction(() =>
    bugStore.saveFix(resolvedBugId.value, fixForm.fixDescriptionMd),
  )
  if (succeeded) {
    fixDialogVisible.value = false
  }
}

async function handleSubmit(): Promise<void> {
  await runAction(() => bugStore.submit(resolvedBugId.value))
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
      ? bugStore.accept(resolvedBugId.value, comment || null)
      : bugStore.reject(resolvedBugId.value, comment),
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
    bugStore.updateBasic(resolvedBugId.value, {
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
      ? bugStore.assign(resolvedBugId.value, personForm.userId!)
      : bugStore.setAcceptor(resolvedBugId.value, personForm.userId!),
  )
  if (succeeded) {
    personDialogVisible.value = false
  }
}

async function handleAddComment(): Promise<void> {
  const content = commentDraft.value.trim()
  if (!content) {
    errorMessage.value = '评论内容不能为空'
    return
  }
  const succeeded = await runAction(() => bugStore.addComment(resolvedBugId.value, content))
  if (succeeded) commentDraft.value = ''
}

async function handleLoadMoreComments(): Promise<void> {
  await runAction(() => bugStore.loadComments(resolvedBugId.value, bugStore.commentsPage + 1))
}

function pickAttachment(): void {
  attachmentInput.value?.click()
}

/** 选择文件后先做与后端一致的体积和扩展名校验，再交给上传接口。 */
async function handleAttachmentPicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空选择，保证连续选择同一个文件也能再次触发 change。
  input.value = ''
  if (!file) {
    return
  }
  const invalidReason = attachmentValidationError(file, bug.value?.attachments.length ?? 0)
  if (invalidReason) {
    errorMessage.value = invalidReason
    return
  }
  await runAction(() => bugStore.uploadAttachment(resolvedBugId.value, file))
}

/** 下载走带鉴权头的二进制请求，成功后用临时链接触发浏览器保存。 */
async function handleDownload(attachment: BugAttachment): Promise<void> {
  errorMessage.value = ''
  try {
    const { blob, fileName } = await downloadFile(`/attachments/${attachment.id}/download`)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName || attachment.originalName
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '附件下载失败，请稍后重试'
  }
}

async function handleDeleteAttachment(attachmentId: number): Promise<void> {
  await runAction(() => bugStore.removeAttachment(resolvedBugId.value, attachmentId))
}

async function openHistoryDetail(versionNo: number): Promise<void> {
  await runAction(() => bugStore.openHistoryDetail(resolvedBugId.value, versionNo))
}

/** 抽屉模式通知父级关闭；独立详情页则返回标准列表路由。 */
function goBack(): void {
  if (props.drawerMode) {
    emit('close')
    return
  }
  void router.push({ name: 'bug-list', params: { workspaceId: workspaceId.value } })
}
</script>

<template>
  <main
    v-loading="bugStore.detailLoading"
    class="bug-detail"
    :class="{ 'bug-detail--drawer': drawerMode }"
  >
    <el-alert
      v-if="errorMessage"
      class="bug-detail__alert"
      :title="errorMessage"
      type="error"
      :closable="true"
      show-icon
      @close="errorMessage = ''"
    />

    <div v-if="bugStore.detailLoading" class="bug-detail__loading" aria-label="正在加载详情" />

    <el-card v-else-if="!bug" shadow="never">
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
            <el-button v-if="!drawerMode" @click="goBack">返回列表</el-button>
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

      <el-card v-if="bugStore.acceptances.length" shadow="never">
        <template #header><h3>验收记录</h3></template>
        <div v-for="record in bugStore.acceptances" :key="record.id" class="acceptance-record">
          <div class="acceptance-record__head">
            <el-tag :type="record.result === 'PASS' ? 'success' : 'danger'" size="small">
              {{ record.result === 'PASS' ? '通过' : '驳回' }}
            </el-tag>
            <strong>{{ record.acceptorDisplayName }}</strong>
            <span class="acceptance-record__flow">
              {{ STATUS_META[record.fromStatus].label }} → {{ STATUS_META[record.toStatus].label }}
            </span>
            <span class="acceptance-record__time">{{ formatDateTime(record.createdAt) }}</span>
          </div>
          <md-preview
            v-if="record.commentMd"
            class="acceptance-record__comment"
            :model-value="record.commentMd"
            preview-theme="github"
          />
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header-row">
            <h3>附件</h3>
            <div v-if="canWriteAttachments" class="attachment-toolbar">
              <span>单个不超过 20MB，最多 20 个</span>
              <el-button
                type="primary"
                plain
                :loading="bugStore.submitting"
                @click="pickAttachment"
              >
                上传附件
              </el-button>
            </div>
          </div>
        </template>
        <input
          ref="attachmentInput"
          class="attachment-input"
          type="file"
          :accept="ATTACHMENT_ACCEPT"
          @change="handleAttachmentPicked"
        />
        <el-table v-if="bug.attachments.length" :data="bug.attachments" row-key="id">
          <el-table-column prop="originalName" label="文件名" min-width="240" />
          <el-table-column label="大小" width="120">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleDownload(row as BugAttachment)"
                >下载</el-button
              >
              <el-popconfirm
                v-if="canWriteAttachments"
                title="删除后不可恢复，确认删除该附件吗？"
                confirm-button-text="删除"
                cancel-button-text="取消"
                @confirm="handleDeleteAttachment(row.id)"
              >
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-else
          :description="canWriteAttachments ? '暂无附件，可点击右上角上传' : '暂无附件'"
          :image-size="72"
        />
      </el-card>

      <section class="detail-trace">
        <header class="detail-trace__tabs">
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
          <span v-if="bugStore.traceLoading">加载中…</span>
        </header>

        <div v-if="previewTab === 'comments'" class="detail-trace__body">
          <div v-if="canComment" class="comment-composer">
            <md-editor v-model="commentDraft" :toolbars="COMMENT_TOOLBARS" />
            <div class="comment-composer__footer">
              <el-button type="primary" :loading="bugStore.submitting" @click="handleAddComment">
                发表评论
              </el-button>
            </div>
          </div>
          <p v-else class="detail-trace__hint">工作空间已停用，只允许查看历史评论。</p>

          <div v-if="bugStore.comments.length" class="comment-list">
            <article v-for="comment in bugStore.comments" :key="comment.id" class="comment-item">
              <span class="comment-item__avatar">{{
                (comment.displayName || comment.username || 'U').slice(0, 1)
              }}</span>
              <div class="comment-item__body">
                <div class="comment-item__head">
                  <strong>{{ comment.displayName }}</strong>
                  <span>{{ formatDateTime(comment.createdAt) }}</span>
                </div>
                <md-preview :model-value="comment.contentMd" preview-theme="github" />
              </div>
            </article>
            <div v-if="hasMoreComments" class="comment-list__more">
              <el-button text type="primary" @click="handleLoadMoreComments">
                加载更多评论（{{ bugStore.comments.length }}/{{ bugStore.commentsTotal }}）
              </el-button>
            </div>
          </div>
          <el-empty v-else description="暂无评论" :image-size="64" />
        </div>

        <div v-else-if="previewTab === 'logs'" class="detail-trace__body">
          <el-timeline v-if="bugStore.logs.length" class="trace-timeline">
            <el-timeline-item
              v-for="log in bugStore.logs"
              :key="log.id"
              :timestamp="formatDateTime(log.createdAt)"
              placement="top"
            >
              <strong>{{ log.operatorDisplayName }}</strong> {{ log.description }}
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无操作日志" :image-size="64" />
        </div>

        <div v-else class="detail-trace__body">
          <div v-if="bugStore.history.length" class="history-list">
            <div v-for="item in bugStore.history" :key="item.id" class="history-item">
              <span class="history-item__version">v{{ item.versionNo }}</span>
              <strong>{{ item.operatorDisplayName }}</strong>
              <span class="history-item__time">{{ formatDateTime(item.createdAt) }}</span>
              <el-button
                link
                type="primary"
                class="history-item__action"
                @click="openHistoryDetail(item.versionNo)"
              >
                查看内容
              </el-button>
            </div>
          </div>
          <el-empty v-else description="暂无描述修订记录" :image-size="64" />
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

    <el-dialog v-model="historyDialogVisible" title="历史版本" width="min(92vw, 860px)">
      <template v-if="bugStore.historyDetail">
        <p class="history-dialog__meta">
          v{{ bugStore.historyDetail.versionNo }} ·
          {{ bugStore.historyDetail.operatorDisplayName }} ·
          {{ formatDateTime(bugStore.historyDetail.createdAt) }}
          <span>（仅查看，不支持恢复）</span>
        </p>
        <md-preview :model-value="bugStore.historyDetail.contentMd" preview-theme="github" />
      </template>
      <template #footer>
        <el-button @click="bugStore.closeHistoryDetail()">关闭</el-button>
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

.bug-detail__loading {
  min-height: 320px;
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

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.attachment-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--bl-muted);
  font-size: 12px;
}

.attachment-input {
  display: none;
}

.acceptance-record {
  padding: 12px 0;
  border-bottom: 1px solid var(--bl-border);
}

.acceptance-record:first-child {
  padding-top: 0;
}

.acceptance-record:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.acceptance-record__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  color: var(--bl-text-secondary);
  font-size: 13px;
}

.acceptance-record__head strong {
  color: var(--bl-text);
  font-weight: 500;
}

.acceptance-record__flow {
  color: var(--bl-muted);
}

.acceptance-record__time {
  margin-left: auto;
  color: var(--bl-muted);
  font-size: 12px;
}

.acceptance-record__comment {
  margin-top: 8px;
}

.detail-trace {
  margin-bottom: 16px;
  overflow: hidden;
  background: linear-gradient(145deg, #171e26, #141a21);
  border: 1px solid var(--bl-border);
  border-radius: 9px;
}

.detail-trace__tabs {
  display: flex;
  min-height: 54px;
  align-items: stretch;
  padding: 0 18px;
  border-bottom: 1px solid var(--bl-border);
}

.detail-trace__tabs button {
  position: relative;
  padding: 0 14px;
  color: var(--bl-text-secondary);
  font: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.detail-trace__tabs button.active {
  color: var(--bl-primary-light);
}

.detail-trace__tabs button.active::after {
  position: absolute;
  right: 12px;
  bottom: 0;
  left: 12px;
  height: 2px;
  content: '';
  background: var(--bl-primary);
}

.detail-trace__tabs span {
  align-self: center;
  margin-left: auto;
  color: var(--bl-muted);
  font-size: 11px;
}

.detail-trace__body {
  padding: 18px 22px;
}

.detail-trace__hint {
  margin: 0 0 14px;
  color: var(--bl-muted);
  font-size: 13px;
}

.comment-composer {
  margin-bottom: 18px;
}

.comment-composer__footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--bl-border);
}

.comment-item:last-child {
  border-bottom: 0;
}

.comment-item__avatar {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  place-items: center;
  color: white;
  font-size: 14px;
  background: linear-gradient(145deg, #257be8, #4ca0ff);
  border-radius: 50%;
}

.comment-item__body {
  flex: 1;
  min-width: 0;
}

.comment-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  color: var(--bl-muted);
  font-size: 12px;
}

.comment-item__head strong {
  color: var(--bl-text);
  font-size: 13px;
}

.comment-list__more {
  padding-top: 10px;
  text-align: center;
}

.trace-timeline {
  padding-top: 4px;
  color: var(--bl-text-secondary);
  font-size: 13px;
}

.trace-timeline strong {
  color: var(--bl-text);
  font-weight: 500;
}

.trace-timeline :deep(.el-timeline-item__timestamp) {
  color: var(--bl-muted);
}

.history-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  color: var(--bl-text-secondary);
  font-size: 13px;
  border-bottom: 1px solid var(--bl-border);
}

.history-item:last-child {
  border-bottom: 0;
}

.history-item strong {
  color: var(--bl-text);
  font-weight: 500;
}

.history-item__version {
  color: var(--bl-primary-light);
  font-family: monospace;
}

.history-item__time {
  color: var(--bl-muted);
  font-size: 12px;
}

.history-item__action {
  margin-left: auto;
}

.history-dialog__meta {
  margin: 0 0 12px;
  color: var(--bl-muted);
  font-size: 12px;
}

/* 抽屉内采用 Plane 式连续内容流，去掉独立详情页的卡片间隙，减少右侧窄栏中的视觉割裂。 */
.bug-detail--drawer {
  width: 100%;
  max-width: none;
  min-height: 100%;
  padding: 22px 30px 42px;
}

.bug-detail--drawer :deep(.el-card) {
  margin-bottom: 0;
  background: transparent !important;
  border: 0;
  border-bottom: 1px solid var(--bl-border);
  border-radius: 0;
  box-shadow: none !important;
}

.bug-detail--drawer :deep(.el-card__header) {
  padding: 22px 4px 12px;
  border-bottom: 0;
}

.bug-detail--drawer :deep(.el-card__body) {
  padding: 18px 4px 24px;
}

.bug-detail--drawer .bug-detail__summary :deep(.el-card__body) {
  padding-top: 6px;
}

.bug-detail--drawer .bug-detail__title-row {
  display: block;
}

.bug-detail--drawer .bug-detail__title-main {
  display: block;
}

.bug-detail--drawer .bug-detail__no {
  display: block;
  margin-bottom: 10px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.bug-detail--drawer .bug-detail__title-main h2 {
  font-size: 24px;
  line-height: 1.35;
}

.bug-detail--drawer .bug-detail__actions {
  margin-top: 20px;
}

.bug-detail--drawer .bug-detail__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 1fr));
  gap: 14px 22px;
  margin-top: 22px;
  padding: 18px 0 4px;
  border-top: 1px solid var(--bl-border);
}

.bug-detail--drawer .detail-trace {
  margin: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
}

.bug-detail--drawer .detail-trace__tabs {
  padding: 0 4px;
}

.bug-detail--drawer .detail-trace__body {
  padding: 20px 4px;
}

@media (max-width: 650px) {
  .card-header-row,
  .acceptance-record__head {
    align-items: flex-start;
    flex-direction: column;
  }

  .acceptance-record__time {
    margin-left: 0;
  }

  .bug-detail--drawer {
    padding: 18px 18px 34px;
  }

  .bug-detail--drawer .bug-detail__meta {
    grid-template-columns: 1fr;
  }
}
</style>
