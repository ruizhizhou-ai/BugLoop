/**
 * 本文件保存当前空间的 Bug 列表、筛选条件和详情，所有写操作以服务端返回的详情为准。
 */
import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'

import { isApiError } from '@/shared/api/types'

import {
  acceptBug,
  assignBug,
  createBug,
  createComment,
  deleteComment,
  deleteBugAttachment,
  fetchAcceptances,
  fetchBugDetail,
  fetchBugs,
  fetchComments,
  fetchDescriptionHistory,
  fetchDescriptionHistoryDetail,
  fetchOperationLogs,
  rejectBug,
  replyComment,
  saveFixDescription,
  setBugAcceptor,
  startBug,
  submitBug,
  updateBug,
  uploadBugAttachment,
  uploadBugAttachmentForBusiness,
} from './bugApi'
import type {
  BugAcceptanceRecord,
  BugComment,
  BugCreated,
  BugDescriptionHistoryDetail,
  BugDescriptionHistoryItem,
  BugDetail,
  BugListQuery,
  BugOperationLog,
  BugSummary,
  CreateBugPayload,
  UpdateBugPayload,
  AttachmentBizType,
} from './bugApi'

const DEFAULT_PAGE_SIZE = 20

function createDefaultQuery(): BugListQuery {
  return { page: 1, pageSize: DEFAULT_PAGE_SIZE }
}

const COMMENT_PAGE_SIZE = 20

export const useBugStore = defineStore('bug', () => {
  const list = ref<BugSummary[]>([])
  const total = ref(0)
  const query = reactive<BugListQuery>(createDefaultQuery())
  const current = ref<BugDetail | null>(null)
  const loading = ref(false)
  const detailLoading = ref(false)
  const submitting = ref(false)
  const comments = ref<BugComment[]>([])
  const commentsTotal = ref(0)
  const commentsPage = ref(1)
  const logs = ref<BugOperationLog[]>([])
  const history = ref<BugDescriptionHistoryItem[]>([])
  const historyDetail = ref<BugDescriptionHistoryDetail | null>(null)
  const acceptances = ref<BugAcceptanceRecord[]>([])
  const traceLoading = ref(false)

  /** 按当前筛选条件加载列表，页码越界由服务端返回空集时不再自动纠正。 */
  async function loadBugs(workspaceId: number): Promise<void> {
    loading.value = true
    try {
      const page = await fetchBugs(workspaceId, { ...query })
      list.value = page.records
      total.value = page.total
      query.page = page.page
      query.pageSize = page.pageSize
    } finally {
      loading.value = false
    }
  }

  /** 重置筛选条件，切换工作空间时调用，避免把上一个空间的筛选带到新空间。 */
  function resetQuery(): void {
    for (const key of Object.keys(query) as (keyof BugListQuery)[]) {
      delete query[key]
    }
    Object.assign(query, createDefaultQuery())
    list.value = []
    total.value = 0
    current.value = null
  }

  /** 创建 Bug，返回主键和业务编号供跳转详情页。 */
  async function create(workspaceId: number, payload: CreateBugPayload): Promise<BugCreated> {
    return withSubmitting(() => createBug(workspaceId, payload))
  }

  /** 判断错误是否为版本冲突，供页面触发"刷新后重试"提示。 */
  function isVersionConflict(error: unknown): boolean {
    return isApiError(error) && error.code === 40902
  }

  /** 加载详情，进入详情页时调用。 */
  async function loadDetail(bugId: number): Promise<void> {
    // 详情抽屉与列表同时存在，使用独立加载态避免打开抽屉时遮挡整张列表。
    detailLoading.value = true
    try {
      current.value = await fetchBugDetail(bugId)
    } finally {
      detailLoading.value = false
    }
  }

  /** 更新基础信息，需携带当前 version，冲突时由调用方处理 40902。 */
  async function updateBasic(bugId: number, payload: UpdateBugPayload): Promise<void> {
    await withSubmitting(async () => applyDetail(await updateBug(bugId, payload)))
  }

  async function assign(bugId: number, assigneeId: number): Promise<void> {
    await withSubmitting(async () => applyDetail(await assignBug(bugId, assigneeId)))
  }

  async function setAcceptor(bugId: number, acceptorId: number): Promise<void> {
    await withSubmitting(async () => applyDetail(await setBugAcceptor(bugId, acceptorId)))
  }

  async function start(bugId: number): Promise<void> {
    await withSubmitting(async () => applyDetail(await startBug(bugId)))
  }

  async function saveFix(bugId: number, fixDescriptionMd: string): Promise<void> {
    await withSubmitting(async () => applyDetail(await saveFixDescription(bugId, fixDescriptionMd)))
  }

  async function submit(bugId: number): Promise<void> {
    await withSubmitting(async () => applyDetail(await submitBug(bugId)))
  }

  async function accept(bugId: number, commentMd: string | null): Promise<void> {
    await withSubmitting(async () => applyDetail(await acceptBug(bugId, commentMd)))
  }

  async function reject(bugId: number, commentMd: string): Promise<void> {
    await withSubmitting(async () => applyDetail(await rejectBug(bugId, commentMd)))
  }

  /** 加载评论；page 大于 1 时追加到已有列表，供"加载更多"使用。 */
  async function loadComments(bugId: number, page = 1): Promise<void> {
    const pageData = await fetchComments(bugId, page, COMMENT_PAGE_SIZE)
    comments.value = page === 1 ? pageData.records : [...comments.value, ...pageData.records]
    commentsTotal.value = pageData.total
    commentsPage.value = page
  }

  /** 发表评论后回到第一页重新加载，保证最新评论立即可见。 */
  async function addComment(bugId: number, contentMd: string): Promise<BugComment> {
    return withSubmitting(async () => {
      const comment = await createComment(bugId, contentMd)
      await loadComments(bugId, 1)
      return comment
    })
  }

  /** 回复后重新加载第一页，保证完整评论树节点按时间正序立即同步到界面。 */
  async function replyToComment(bugId: number, parentCommentId: number, contentMd: string): Promise<BugComment> {
    return withSubmitting(async () => {
      const comment = await replyComment(bugId, parentCommentId, contentMd)
      await loadComments(bugId, 1)
      return comment
    })
  }

  /** 删除成功后重新加载，服务端逻辑删除的父评论状态会同步反映到其回复项。 */
  async function deleteCommentById(bugId: number, commentId: number): Promise<void> {
    await withSubmitting(async () => {
      await deleteComment(bugId, commentId)
      await loadComments(bugId, 1)
    })
  }

  /** 并行加载操作日志、描述历史和验收历史，三者都是只读的追溯数据。 */
  async function loadTrace(bugId: number): Promise<void> {
    traceLoading.value = true
    try {
      const [logList, historyList, acceptanceList] = await Promise.all([
        fetchOperationLogs(bugId),
        fetchDescriptionHistory(bugId),
        fetchAcceptances(bugId),
      ])
      logs.value = logList
      history.value = historyList
      acceptances.value = acceptanceList
    } finally {
      traceLoading.value = false
    }
  }

  /** 按需读取单个历史版本正文，不写入列表状态。 */
  async function openHistoryDetail(bugId: number, versionNo: number): Promise<void> {
    historyDetail.value = await fetchDescriptionHistoryDetail(bugId, versionNo)
  }

  function closeHistoryDetail(): void {
    historyDetail.value = null
  }

  /** 上传附件后重新读取详情，附件列表以服务端为准。 */
  async function uploadAttachment(
    bugId: number,
    file: File,
    business?: { bizType: AttachmentBizType; bizId: number },
  ): Promise<void> {
    await withSubmitting(async () => {
      // 未传业务上下文时保留旧上传调用，兼容仍在运行的旧客户端和既有单元测试。
      await (business
        ? uploadBugAttachmentForBusiness(bugId, file, business)
        : uploadBugAttachment(bugId, file))
      await loadDetail(bugId)
    })
  }

  /** 逻辑删除附件后同步详情中的附件列表。 */
  async function removeAttachment(bugId: number, attachmentId: number): Promise<void> {
    await withSubmitting(async () => {
      await deleteBugAttachment(attachmentId)
      await loadDetail(bugId)
    })
  }

  /** 创建后补传附件，返回失败文件名清单，单个失败不中断其余文件。 */
  async function uploadAttachments(
    bugId: number,
    files: File[],
    business?: { bizType: AttachmentBizType; bizId: number },
  ): Promise<string[]> {
    const failed: string[] = []
    await withSubmitting(async () => {
      for (const file of files) {
        try {
          await (business
            ? uploadBugAttachmentForBusiness(bugId, file, business)
            : uploadBugAttachment(bugId, file))
        } catch {
          failed.push(file.name)
        }
      }
    })
    return failed
  }

  /** 清空全部状态，账号切换时调用。 */
  function reset(): void {
    resetQuery()
    loading.value = false
    detailLoading.value = false
    submitting.value = false
    comments.value = []
    commentsTotal.value = 0
    commentsPage.value = 1
    logs.value = []
    history.value = []
    historyDetail.value = null
    acceptances.value = []
    traceLoading.value = false
  }

  function applyDetail(detail: BugDetail): void {
    current.value = detail
  }

  async function withSubmitting<T>(action: () => Promise<T>): Promise<T> {
    submitting.value = true
    try {
      return await action()
    } finally {
      submitting.value = false
    }
  }

  return {
    list,
    total,
    query,
    current,
    loading,
    detailLoading,
    submitting,
    comments,
    commentsTotal,
    commentsPage,
    logs,
    history,
    historyDetail,
    acceptances,
    traceLoading,
    loadBugs,
    resetQuery,
    create,
    loadDetail,
    updateBasic,
    assign,
    setAcceptor,
    start,
    saveFix,
    submit,
    accept,
    reject,
    loadComments,
    addComment,
    replyToComment,
    deleteCommentById,
    loadTrace,
    openHistoryDetail,
    closeHistoryDetail,
    uploadAttachment,
    removeAttachment,
    uploadAttachments,
    reset,
    isVersionConflict,
  }
})
