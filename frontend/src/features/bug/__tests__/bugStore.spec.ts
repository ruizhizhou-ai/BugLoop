/**
 * 本文件验证 Bug Store 的列表加载、筛选重置、写操作替换详情和版本冲突识别。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useBugStore } from '../bugStore'
import * as bugApi from '../bugApi'
import type { BugComment, BugDetail, BugSummary } from '../bugApi'
import { ApiError } from '@/shared/api/types'

vi.mock('../bugApi', () => ({
  acceptBug: vi.fn<typeof bugApi.acceptBug>(),
  assignBug: vi.fn<typeof bugApi.assignBug>(),
  createBug: vi.fn<typeof bugApi.createBug>(),
  createComment: vi.fn<typeof bugApi.createComment>(),
  deleteComment: vi.fn<typeof bugApi.deleteComment>(),
  deleteBugAttachment: vi.fn<typeof bugApi.deleteBugAttachment>(),
  fetchAcceptances: vi.fn<typeof bugApi.fetchAcceptances>(),
  fetchBugDetail: vi.fn<typeof bugApi.fetchBugDetail>(),
  fetchBugs: vi.fn<typeof bugApi.fetchBugs>(),
  fetchComments: vi.fn<typeof bugApi.fetchComments>(),
  fetchDescriptionHistory: vi.fn<typeof bugApi.fetchDescriptionHistory>(),
  fetchDescriptionHistoryDetail: vi.fn<typeof bugApi.fetchDescriptionHistoryDetail>(),
  fetchOperationLogs: vi.fn<typeof bugApi.fetchOperationLogs>(),
  rejectBug: vi.fn<typeof bugApi.rejectBug>(),
  replyComment: vi.fn<typeof bugApi.replyComment>(),
  saveFixDescription: vi.fn<typeof bugApi.saveFixDescription>(),
  setBugAcceptor: vi.fn<typeof bugApi.setBugAcceptor>(),
  startBug: vi.fn<typeof bugApi.startBug>(),
  submitBug: vi.fn<typeof bugApi.submitBug>(),
  updateBug: vi.fn<typeof bugApi.updateBug>(),
  uploadBugAttachment: vi.fn<typeof bugApi.uploadBugAttachment>(),
  uploadBugAttachmentForBusiness: vi.fn<typeof bugApi.uploadBugAttachmentForBusiness>(),
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

  it('评论分页应追加历史页，发表评论后应回到第一页刷新', async () => {
    const first: BugComment = {
      commentId: 1,
      bugId: 101,
      userId: 10,
      username: 'owner',
      displayName: '负责人',
      avatar: null,
      contentMd: '第一条',
      parentId: null,
      replyUserId: null,
      replyUsername: null,
      parentDeleted: false,
      deleted: false,
      createdAt: '2026-09-14T10:00:00',
    }
    const second: BugComment = { ...first, commentId: 2, contentMd: '第二条' }
    vi.mocked(bugApi.fetchComments)
      .mockResolvedValueOnce({ records: [first], total: 2, page: 1, pageSize: 20 })
      .mockResolvedValueOnce({ records: [second], total: 2, page: 2, pageSize: 20 })
      .mockResolvedValueOnce({ records: [first, second], total: 2, page: 1, pageSize: 20 })
    vi.mocked(bugApi.createComment).mockResolvedValue(second)

    const store = useBugStore()
    await store.loadComments(101)
    await store.loadComments(101, 2)
    expect(store.comments.map((comment) => comment.commentId)).toEqual([1, 2])
    expect(store.commentsPage).toBe(2)

    await store.addComment(101, '第二条')
    expect(bugApi.createComment).toHaveBeenCalledWith(101, '第二条')
    expect(bugApi.fetchComments).toHaveBeenLastCalledWith(101, 1, 20)
    expect(store.comments.map((comment) => comment.commentId)).toEqual([1, 2])
    expect(store.commentsPage).toBe(1)
    expect(store.submitting).toBe(false)
  })

  it('加载追溯数据应填充日志、历史和验收记录', async () => {
    vi.mocked(bugApi.fetchOperationLogs).mockResolvedValue([
      {
        id: 1,
        operatorId: 10,
        operatorUsername: 'owner',
        operatorDisplayName: '负责人',
        operationType: 'CREATE_BUG',
        fieldName: null,
        oldValue: null,
        newValue: 'BUG-000101',
        description: '创建了 BUG-000101',
        createdAt: '2026-09-14T10:00:00',
      },
    ])
    vi.mocked(bugApi.fetchDescriptionHistory).mockResolvedValue([
      {
        id: 1,
        versionNo: 1,
        operatorId: 10,
        operatorUsername: 'owner',
        operatorDisplayName: '负责人',
        createdAt: '2026-09-14T10:00:00',
      },
    ])
    vi.mocked(bugApi.fetchAcceptances).mockResolvedValue([
      {
        id: 1,
        acceptorId: 10,
        acceptorUsername: 'owner',
        acceptorDisplayName: '负责人',
        result: 'PASS',
        commentMd: null,
        fromStatus: 'WAIT_ACCEPTANCE',
        toStatus: 'CLOSED',
        createdAt: '2026-09-14T12:00:00',
        attachments: [],
      },
    ])

    const store = useBugStore()
    await store.loadTrace(101)

    expect(store.logs).toHaveLength(1)
    expect(store.history[0]?.versionNo).toBe(1)
    expect(store.acceptances[0]?.result).toBe('PASS')
    expect(store.traceLoading).toBe(false)
  })

  it('上传和删除附件后应重新读取详情', async () => {
    const detail: BugDetail = {
      ...BUG_DETAIL,
      attachments: [
        {
          id: 9,
          bugId: 101,
          bizType: 'BUG_PROCESS',
          bizId: 101,
          originalName: 'log.txt',
          fileSize: 12,
          contentType: 'text/plain',
          uploaderId: 10,
          uploaderName: '负责人',
          uploaderAvatar: null,
          createdAt: '2026-09-14T10:00:00',
          canDelete: true,
        },
      ],
    }
    vi.mocked(bugApi.uploadBugAttachment).mockResolvedValue(detail.attachments[0]!)
    vi.mocked(bugApi.deleteBugAttachment).mockResolvedValue(undefined)
    vi.mocked(bugApi.fetchBugDetail).mockResolvedValue(detail)

    const store = useBugStore()
    await store.uploadAttachment(101, new File(['内容'], 'log.txt', { type: 'text/plain' }))
    expect(store.current?.attachments).toHaveLength(1)

    await store.removeAttachment(101, 9)
    expect(bugApi.deleteBugAttachment).toHaveBeenCalledWith(9)
    expect(bugApi.fetchBugDetail).toHaveBeenCalledTimes(2)
  })
})
