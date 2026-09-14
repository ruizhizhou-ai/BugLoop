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
  fetchBugDetail,
  fetchBugs,
  rejectBug,
  saveFixDescription,
  setBugAcceptor,
  startBug,
  submitBug,
  updateBug,
} from './bugApi'
import type {
  BugCreated,
  BugDetail,
  BugListQuery,
  BugSummary,
  CreateBugPayload,
  UpdateBugPayload,
} from './bugApi'

const DEFAULT_PAGE_SIZE = 20

function createDefaultQuery(): BugListQuery {
  return { page: 1, pageSize: DEFAULT_PAGE_SIZE }
}

export const useBugStore = defineStore('bug', () => {
  const list = ref<BugSummary[]>([])
  const total = ref(0)
  const query = reactive<BugListQuery>(createDefaultQuery())
  const current = ref<BugDetail | null>(null)
  const loading = ref(false)
  const submitting = ref(false)

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
    loading.value = true
    try {
      current.value = await fetchBugDetail(bugId)
    } finally {
      loading.value = false
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

  /** 清空全部状态，账号切换时调用。 */
  function reset(): void {
    resetQuery()
    loading.value = false
    submitting.value = false
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
    submitting,
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
    reset,
    isVersionConflict,
  }
})
