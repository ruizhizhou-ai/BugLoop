/**
 * 本文件验证 Bug Store 的列表加载、筛选重置、写操作替换详情和版本冲突识别。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useBugStore } from '../bugStore'
import * as bugApi from '../bugApi'
import type { BugDetail, BugSummary } from '../bugApi'
import { ApiError } from '@/shared/api/types'

vi.mock('../bugApi', () => ({
  acceptBug: vi.fn<typeof bugApi.acceptBug>(),
  assignBug: vi.fn<typeof bugApi.assignBug>(),
  createBug: vi.fn<typeof bugApi.createBug>(),
  fetchBugDetail: vi.fn<typeof bugApi.fetchBugDetail>(),
  fetchBugs: vi.fn<typeof bugApi.fetchBugs>(),
  rejectBug: vi.fn<typeof bugApi.rejectBug>(),
  saveFixDescription: vi.fn<typeof bugApi.saveFixDescription>(),
  setBugAcceptor: vi.fn<typeof bugApi.setBugAcceptor>(),
  startBug: vi.fn<typeof bugApi.startBug>(),
  submitBug: vi.fn<typeof bugApi.submitBug>(),
  updateBug: vi.fn<typeof bugApi.updateBug>(),
}))

const BUG_SUMMARY: BugSummary = {
  id: 101,
  workspaceId: 1,
  bugNo: 'BUG-000101',
  title: '登录页样式错位',
  priority: 'P2',
  status: 'TODO',
  creatorId: 10,
  assigneeId: null,
  acceptorId: 10,
  creator: { id: 10, username: 'owner', displayName: '负责人' },
  assignee: null,
  acceptor: { id: 10, username: 'owner', displayName: '负责人' },
  reopenCount: 0,
  version: 0,
  createdAt: '2026-09-14T10:00:00',
  updatedAt: '2026-09-14T10:00:00',
  closedAt: null,
}

const BUG_DETAIL: BugDetail = {
  ...BUG_SUMMARY,
  descriptionMd: '# 问题现象',
  fixDescriptionMd: null,
  workspace: {
    id: 1,
    name: '研发中心',
    description: null,
    ownerId: 10,
    status: 'ENABLED',
    currentUserRole: 'OWNER',
    createdAt: '2026-09-14T09:00:00',
    updatedAt: '2026-09-14T09:00:00',
  },
  attachments: [],
  latestAcceptance: null,
}

describe('bugStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('应按当前筛选加载列表并同步分页回显字段', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [BUG_SUMMARY],
      total: 1,
      page: 2,
      pageSize: 20,
    })

    const store = useBugStore()
    store.query.page = 2
    store.query.status = 'TODO'
    await store.loadBugs(1)

    expect(bugApi.fetchBugs).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ page: 2, status: 'TODO' }),
    )
    expect(store.list).toEqual([BUG_SUMMARY])
    expect(store.total).toBe(1)
    expect(store.loading).toBe(false)
  })

  it('重置筛选后应清空条件与列表数据', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [BUG_SUMMARY],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const store = useBugStore()
    store.query.keyword = '登录'
    store.query.status = 'PROCESSING'
    await store.loadBugs(1)

    store.resetQuery()

    expect(store.query.keyword).toBeUndefined()
    expect(store.query.status).toBeUndefined()
    expect(store.query.page).toBe(1)
    expect(store.list).toEqual([])
    expect(store.total).toBe(0)
  })

  it('写操作应使用服务端返回的详情替换当前数据', async () => {
    const started: BugDetail = { ...BUG_DETAIL, status: 'PROCESSING', version: 1 }
    vi.mocked(bugApi.startBug).mockResolvedValue(started)

    const store = useBugStore()
    store.current = BUG_DETAIL
    await store.start(101)

    expect(store.current?.status).toBe('PROCESSING')
    expect(store.current?.version).toBe(1)
    expect(store.submitting).toBe(false)
  })

  it('应识别 40902 版本冲突并原样向上抛出其他错误', async () => {
    vi.mocked(bugApi.updateBug).mockRejectedValue(
      new ApiError(40902, '数据已被其他用户修改，请刷新后重试'),
    )

    const store = useBugStore()
    store.current = BUG_DETAIL

    await expect(
      store.updateBasic(101, {
        title: '标题',
        descriptionMd: '描述',
        priority: 'P2',
        version: 0,
      }),
    ).rejects.toMatchObject({ code: 40902 })
  })

  it('应把创建结果原样返回供页面跳转', async () => {
    vi.mocked(bugApi.createBug).mockResolvedValue({ id: 102, bugNo: 'BUG-000102' })

    const store = useBugStore()
    const created = await store.create(1, {
      title: '新 Bug',
      descriptionMd: '描述',
      priority: 'P2',
      assigneeId: null,
      acceptorId: 10,
    })

    expect(created).toEqual({ id: 102, bugNo: 'BUG-000102' })
  })
})
