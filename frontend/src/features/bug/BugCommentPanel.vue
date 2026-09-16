<!-- 本文件实现 Bug 详情下方的评论区域，负责顶级评论、一级回复和逻辑删除的交互展示。 -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElEmpty, ElPopconfirm } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/popper/style/css'
import { MdEditor, MdPreview } from 'md-editor-v3'
import type { ToolbarNames } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { useAuthStore } from '@/features/auth/authStore'
import { isApiError } from '@/shared/api/types'
import AppNotice from '@/shared/components/AppNotice.vue'
import { ATTACHMENT_ACCEPT, attachmentValidationError, formatDateTime, formatFileSize } from './bugMeta'
import { useBugStore } from './bugStore'
import type { BugComment } from './bugApi'

/** 评论面板由详情页提供 Bug、空间可写状态与管理员判定，避免组件重复读取 Bug 详情。 */
interface BugCommentPanelProps {
  bugId: number
  writable: boolean
}

/** 一个讨论线程由一条顶级父评论及其全部后代组成；界面把全部后代压缩显示为同级子回复。 */
interface CommentThread {
  key: string
  parent: BugComment
  children: BugComment[]
}

const props = defineProps<BugCommentPanelProps>()
const auth = useAuthStore()
const bugStore = useBugStore()

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

const draft = ref('')
const errorMessage = ref('')
const replyTarget = ref<BugComment | null>(null)
const attachmentInput = ref<HTMLInputElement | null>(null)
const pendingAttachments = ref<File[]>([])
// 评论在详情中可能很多，默认收起，用户主动展开后再阅读或参与讨论。
const commentsExpanded = ref(false)
// 每条父评论单独维护折叠状态，收起子回复不会影响其他讨论线程。
const collapsedParentIds = ref<Set<number>>(new Set())
const hasMoreComments = computed(() => bugStore.comments.length < bugStore.commentsTotal)

/**
 * 将接口平铺记录按完整 parentId 链定位顶级父评论。数据可无限层回复，但界面仅展示两层：
 * 顶级父评论 + 全部后代子回复；每个子项仍保留其直接回复对象，保证“回复谁”的语义准确。
 */
const commentThreads = computed<CommentThread[]>(() => {
  const byId = new Map(bugStore.comments.map((comment) => [comment.commentId, comment]))
  const threads = new Map<number, CommentThread>()

  for (const comment of bugStore.comments) {
    let root = comment
    const visited = new Set<number>([comment.commentId])
    // 容错处理异常循环或跨页缺失祖先：无法继续向上追溯时把当前节点当作独立线程根。
    while (root.parentId !== null) {
      const parent = byId.get(root.parentId)
      if (!parent || visited.has(parent.commentId)) break
      visited.add(parent.commentId)
      root = parent
    }
    const thread = threads.get(root.commentId) ?? {
      key: `parent-${root.commentId}`,
      parent: root,
      children: [],
    }
    if (comment.commentId !== root.commentId) {
      thread.children.push(comment)
    }
    threads.set(root.commentId, thread)
  }
  return [...threads.values()]
})

/** 用于子评论展示其直接父评论的上下文；不存在时视为已删除或暂未加载。 */
const commentsById = computed(() => new Map(bugStore.comments.map((comment) => [comment.commentId, comment])))

/** 切换详情抽屉中的 Bug 时清空回复草稿并加载该 Bug 的独立评论列表。 */
watch(
  () => props.bugId,
  (bugId) => {
    draft.value = ''
    replyTarget.value = null
    pendingAttachments.value = []
    errorMessage.value = ''
    commentsExpanded.value = false
    collapsedParentIds.value = new Set()
    void loadComments(bugId)
  },
  { immediate: true },
)

/** 当前账号本人或平台 SYSTEM_ADMIN 才可看到删除操作；空间角色不会扩大删除他人发言的范围。 */
function canDelete(comment: BugComment): boolean {
  return comment.userId === auth.user?.id || auth.user?.systemRole === 'SYSTEM_ADMIN'
}

/**
 * 任何可写成员都能回复任意评论，包括原评论作者本人。服务端保存完整多层关系，
 * 本组件只将其压缩为两层展示，不限制用户继续针对某条子评论回复。
 */
function canReply(): boolean {
  return props.writable
}

/** 评论作者无头像资料时使用显示名称首字母，维持当前 BugLoop 的头像视觉语言。 */
function avatarInitial(comment: BugComment): string {
  return (comment.displayName || comment.username || 'U').slice(0, 1).toUpperCase()
}

/** 将父评论 Markdown 压缩为简短上下文，避免回复提示占据正文阅读空间。 */
function parentSummary(contentMd: string | null): string {
  if (!contentMd) return ''
  const plainText = contentMd
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/[>*_`~]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return plainText.length > 42 ? `${plainText.slice(0, 42)}…` : plainText
}

/** 生成子评论的弱化回复上下文；删除父评论时不泄露原内容。 */
function replyContext(comment: BugComment, parent: BugComment | null): string {
  if (comment.parentDeleted || !parent) {
    return '↪ 回复内容已删除'
  }
  const summary = parentSummary(parent.contentMd)
  const username = comment.replyUsername || '用户'
  return summary ? `↪ 回复 @${username}：${summary}` : `↪ 回复 @${username}`
}

/** 请求评论列表，并将接口错误转换为页面内提示。 */
async function loadComments(bugId: number): Promise<void> {
  try {
    await bugStore.loadComments(bugId, 1)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载评论失败，请稍后重试'
  }
}

/** 进入回复状态，提交时统一使用同一个编辑器，避免维护两份 Markdown 输入逻辑。 */
function beginReply(comment: BugComment): void {
  replyTarget.value = comment
}

/** 切换指定父评论下所有同级回复的可见状态，不改变整块评论面板的折叠状态。 */
function toggleThread(parentCommentId: number): void {
  const next = new Set(collapsedParentIds.value)
  if (next.has(parentCommentId)) {
    next.delete(parentCommentId)
  } else {
    next.add(parentCommentId)
  }
  collapsedParentIds.value = next
}

/** 判断某条父评论的子回复是否被收起。 */
function isThreadCollapsed(parentCommentId: number): boolean {
  return collapsedParentIds.value.has(parentCommentId)
}

/** 取消回复不清空已输入内容，方便用户改为顶级评论后继续编辑。 */
function cancelReply(): void {
  replyTarget.value = null
}

/** 打开评论附件选择框；文件会等评论记录创建成功后再绑定，避免产生孤立附件。 */
function pickAttachments(): void {
  attachmentInput.value?.click()
}

/** 校验并暂存评论附件，数量限制与服务端单 Bug 附件上限保持一致。 */
function handleAttachmentsPicked(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  const existingCount = bugStore.current?.attachments.length ?? 0
  for (const file of files) {
    const reason = attachmentValidationError(file, existingCount + pendingAttachments.value.length)
    if (reason) {
      errorMessage.value = reason
      continue
    }
    pendingAttachments.value.push(file)
  }
}

/** 移除尚未写入服务端的评论附件草稿。 */
function removeAttachment(index: number): void {
  pendingAttachments.value.splice(index, 1)
}

/** 根据是否存在回复目标调用顶级评论或一级回复接口；成功后由 Store 刷新当前列表。 */
async function submitComment(): Promise<void> {
  const content = draft.value.trim()
  if (!content) {
    errorMessage.value = replyTarget.value ? '回复内容不能为空' : '评论内容不能为空'
    return
  }
  try {
    const comment = replyTarget.value
      ? await bugStore.replyToComment(props.bugId, replyTarget.value.commentId, content)
      : await bugStore.addComment(props.bugId, content)
    const failed: string[] = []
    // 评论先落库才能得到可靠的 bizId；逐个上传可让单文件失败不影响已发布的讨论内容。
    for (const file of pendingAttachments.value) {
      try {
        await bugStore.uploadAttachment(props.bugId, file, {
          bizType: 'COMMENT',
          bizId: comment.commentId,
        })
      } catch {
        failed.push(file.name)
      }
    }
    if (failed.length) {
      errorMessage.value = `评论已发布，但附件上传失败：${failed.join('、')}`
    }
    draft.value = ''
    replyTarget.value = null
    pendingAttachments.value = []
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '发表评论失败，请稍后重试'
  }
}

/** 删除前由 Popconfirm 二次确认，成功后 Store 重新读取评论以同步父评论删除状态。 */
async function removeComment(comment: BugComment): Promise<void> {
  try {
    await bugStore.deleteCommentById(props.bugId, comment.commentId)
    if (replyTarget.value?.commentId === comment.commentId) {
      replyTarget.value = null
    }
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '删除评论失败，请稍后重试'
  }
}

/** 加载下一页时追加既有数据，保留正序时间流的阅读顺序。 */
async function loadMore(): Promise<void> {
  try {
    await bugStore.loadComments(props.bugId, bugStore.commentsPage + 1)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载更多评论失败，请稍后重试'
  }
}
</script>

<template>
  <section class="bug-comment-panel" aria-label="Bug 评论">
    <app-notice v-if="errorMessage" :message="errorMessage" @close="errorMessage = ''" />
    <header class="bug-comment-panel__header">
      <button
        type="button"
        class="bug-comment-panel__toggle"
        :aria-expanded="commentsExpanded"
        aria-controls="bug-comments"
        @click="commentsExpanded = !commentsExpanded"
      >
        <h3>评论</h3>
        <span>{{ bugStore.commentsTotal }} 条讨论</span>
        <span class="bug-comment-panel__chevron" :class="{ 'bug-comment-panel__chevron--expanded': commentsExpanded }">⌄</span>
      </button>
    </header>

    <div v-show="commentsExpanded" id="bug-comments">
      <div v-if="writable" class="comment-composer">
        <input
          ref="attachmentInput"
          class="comment-composer__attachment-input"
          type="file"
          multiple
          :accept="ATTACHMENT_ACCEPT"
          @change="handleAttachmentsPicked"
        />
        <div v-if="replyTarget" class="comment-composer__replying">
          <span>回复 @{{ replyTarget.displayName || replyTarget.username }}</span>
          <button type="button" @click="cancelReply">取消回复</button>
        </div>
        <!-- 评论输入仅保留约两行可见编辑区，工具栏仍可用于常用 Markdown 格式。 -->
        <md-editor
          v-model="draft"
          class="comment-composer__editor"
          :toolbars="COMMENT_TOOLBARS"
          :style="{ height: '126px' }"
        />
        <div class="comment-composer__footer">
          <div class="comment-composer__attachments">
            <el-button text type="primary" :disabled="bugStore.submitting" @click="pickAttachments">附加文件</el-button>
            <span v-if="pendingAttachments.length" class="comment-composer__attachment-names">
              <span v-for="(file, index) in pendingAttachments" :key="file.name + index">
                {{ file.name }}（{{ formatFileSize(file.size) }}）
                <button type="button" :aria-label="`移除 ${file.name}`" @click="removeAttachment(index)">×</button>
              </span>
            </span>
          </div>
          <el-button type="primary" :loading="bugStore.submitting" @click="submitComment">
            {{ replyTarget ? '发布回复' : '发表评论' }}
          </el-button>
        </div>
      </div>
      <p v-else class="bug-comment-panel__hint">工作空间已停用，只允许查看历史评论。</p>

      <div v-if="bugStore.comments.length" class="comment-list">
        <section v-for="thread in commentThreads" :key="thread.key" class="comment-thread">
          <article class="comment-item comment-item--parent">
            <span class="comment-item__avatar">
              <img
                v-if="!thread.parent.deleted && thread.parent.avatar"
                :src="thread.parent.avatar"
                :alt="`${thread.parent.displayName} 的头像`"
              />
              <template v-else>{{ thread.parent.deleted ? '—' : avatarInitial(thread.parent) }}</template>
            </span>
            <div class="comment-item__body">
              <div class="comment-item__head">
                <strong>{{ thread.parent.deleted ? '该评论已删除' : (thread.parent.displayName || thread.parent.username || '已注销用户') }}</strong>
                <span>{{ formatDateTime(thread.parent.createdAt) }}</span>
              </div>
              <template v-if="!thread.parent.deleted">
                <md-preview :model-value="thread.parent.contentMd ?? ''" preview-theme="github" />
                <div class="comment-item__actions">
                  <el-button v-if="canReply()" link type="primary" @click="beginReply(thread.parent)">
                    回复
                  </el-button>
                  <el-popconfirm
                    v-if="canDelete(thread.parent)"
                    title="删除后不可恢复，确认删除该评论吗？"
                    confirm-button-text="删除"
                    cancel-button-text="取消"
                    @confirm="removeComment(thread.parent)"
                  >
                    <template #reference><el-button link type="danger">删除</el-button></template>
                  </el-popconfirm>
                </div>
              </template>
            </div>
          </article>

          <template v-if="thread.children.length">
            <button
              type="button"
              class="comment-thread__toggle"
              :aria-expanded="!isThreadCollapsed(thread.parent.commentId)"
              :aria-controls="`comment-replies-${thread.parent.commentId}`"
              @click="toggleThread(thread.parent.commentId)"
            >
              {{ isThreadCollapsed(thread.parent.commentId) ? `展开 ${thread.children.length} 条回复` : '收起回复' }}
              <span :class="{ 'comment-thread__chevron--collapsed': isThreadCollapsed(thread.parent.commentId) }">⌃</span>
            </button>
          </template>

          <div
            v-show="!isThreadCollapsed(thread.parent.commentId)"
            :id="`comment-replies-${thread.parent.commentId}`"
            class="comment-thread__children"
          >
            <article v-for="comment in thread.children" :key="comment.commentId" class="comment-item comment-item--child">
              <span class="comment-item__avatar">
                <img v-if="comment.avatar" :src="comment.avatar" :alt="`${comment.displayName} 的头像`" />
                <template v-else>{{ avatarInitial(comment) }}</template>
              </span>
              <div class="comment-item__body">
                <div class="comment-item__head">
                  <strong>{{ comment.deleted ? '该评论已删除' : (comment.displayName || comment.username || '已注销用户') }}</strong>
                  <span>{{ formatDateTime(comment.createdAt) }}</span>
                </div>
                <p class="comment-item__reply-context">
                  {{ replyContext(comment, commentsById.get(comment.parentId ?? -1) ?? null) }}
                </p>
                <md-preview v-if="!comment.deleted" :model-value="comment.contentMd ?? ''" preview-theme="github" />
                <div v-if="!comment.deleted" class="comment-item__actions">
                  <el-button v-if="canReply()" link type="primary" @click="beginReply(comment)">
                    回复
                  </el-button>
                  <el-popconfirm
                    v-if="canDelete(comment)"
                    title="删除后不可恢复，确认删除该评论吗？"
                    confirm-button-text="删除"
                    cancel-button-text="取消"
                    @confirm="removeComment(comment)"
                  >
                    <template #reference><el-button link type="danger">删除</el-button></template>
                  </el-popconfirm>
                </div>
              </div>
            </article>
          </div>
        </section>
        <div v-if="hasMoreComments" class="comment-list__more">
          <el-button text type="primary" @click="loadMore">
            加载更多评论（{{ bugStore.comments.length }}/{{ bugStore.commentsTotal }}）
          </el-button>
        </div>
      </div>
      <el-empty v-else description="暂无评论" :image-size="64" />
    </div>
  </section>
</template>

<style scoped>
.bug-comment-panel {
  padding: 20px 22px;
  border-top: 1px solid var(--bl-border);
}

.bug-comment-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0;
}

.bug-comment-panel__toggle {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 0;
  color: var(--bl-text);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.bug-comment-panel__header h3 {
  display: inline;
  margin: 0 10px 0 0;
  color: var(--bl-text);
  font-size: 16px;
}

.bug-comment-panel__header span,
.bug-comment-panel__hint {
  color: var(--bl-muted);
  font-size: 12px;
}

.bug-comment-panel__chevron {
  display: inline-block;
  color: var(--bl-text-secondary);
  font-size: 20px;
  line-height: 1;
  transform: rotate(0deg);
  transition: transform 0.18s ease;
}

.bug-comment-panel__chevron--expanded {
  transform: rotate(180deg);
}

.comment-composer {
  margin: 16px 0 18px;
}

.comment-composer__replying {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 34px;
  padding: 0 10px;
  color: var(--bl-primary);
  font-size: 13px;
  background: color-mix(in srgb, var(--bl-primary) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--bl-primary) 22%, var(--bl-border));
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
}

.comment-composer__replying button {
  padding: 0;
  color: var(--bl-text-secondary);
  cursor: pointer;
  background: none;
  border: 0;
}

.comment-composer__editor {
  min-height: 126px;
}

.comment-composer__attachment-input {
  display: none;
}

.comment-composer__footer {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 10px;
}

.comment-composer__attachments {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 8px;
}

.comment-composer__attachment-names {
  display: flex;
  min-width: 0;
  gap: 6px;
  overflow: auto;
}

.comment-composer__attachment-names > span {
  display: inline-flex;
  align-items: center;
  max-width: 230px;
  padding: 3px 7px;
  overflow: hidden;
  color: var(--bl-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: var(--bl-control-bg);
  border: 1px solid var(--bl-border);
  border-radius: 5px;
}

.comment-composer__attachment-names button {
  padding: 0 0 0 5px;
  color: var(--bl-muted);
  font: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--bl-border);
}

.comment-thread {
  border-bottom: 1px solid var(--bl-border);
}

.comment-thread:last-of-type {
  border-bottom: 0;
}

.comment-item--parent {
  border-bottom: 0;
}

.comment-thread__toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 0;
  margin: 0 0 0 38px;
  color: var(--bl-primary);
  font-size: 12px;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.comment-thread__toggle span {
  display: inline-block;
  color: var(--bl-text-secondary);
  transition: transform 0.18s ease;
}

.comment-thread__chevron--collapsed {
  transform: rotate(180deg);
}

.comment-thread__children {
  margin: 0 0 0 38px;
  padding-left: 14px;
  /* 仅用很淡的竖线表达归属，避免把子回复误读成一条独立评论。 */
  border-left: 2px solid color-mix(in srgb, var(--bl-border-strong) 42%, transparent);
}

.comment-item--child {
  padding: 10px 0 12px;
  border-bottom: 0;
}

.comment-thread__children .comment-item:last-child {
  border-bottom: 0;
}

.comment-item__avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  overflow: hidden;
  place-items: center;
  color: white;
  font-size: 13px;
  background: linear-gradient(145deg, #257be8, #4ca0ff);
  border-radius: 50%;
}

.comment-item__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  margin-bottom: 4px;
  color: var(--bl-muted);
  font-size: 12px;
}

.comment-item__head strong {
  color: var(--bl-text);
  font-size: 13px;
}

.comment-item__reply-context {
  margin: 0 0 5px;
  color: var(--bl-muted);
  font-size: 12px;
}

.comment-item__actions {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.comment-list__more {
  padding-top: 10px;
  text-align: center;
}

@media (max-width: 760px) {
  .bug-comment-panel {
    padding: 16px;
  }

  .comment-thread__toggle,
  .comment-thread__children {
    margin-left: 32px;
  }
}
</style>
