/**
 * 本文件验证工作空间 Store 的选择恢复、创建切换和成员刷新行为，避免跨空间残留旧数据。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useWorkspaceStore } from '../workspaceStore'
import * as workspaceApi from '../workspaceApi'
import type { Workspace, WorkspaceMember } from '../workspaceApi'

vi.mock('../workspaceApi', () => ({
  addWorkspaceMember: vi.fn<typeof workspaceApi.addWorkspaceMember>(),
  createWorkspace: vi.fn<typeof workspaceApi.createWorkspace>(),
  disableWorkspace: vi.fn<typeof workspaceApi.disableWorkspace>(),
  fetchMyWorkspaces: vi.fn<typeof workspaceApi.fetchMyWorkspaces>(),
  fetchWorkspace: vi.fn<typeof workspaceApi.fetchWorkspace>(),
  fetchWorkspaceMembers: vi.fn<typeof workspaceApi.fetchWorkspaceMembers>(),
  removeWorkspaceMember: vi.fn<typeof workspaceApi.removeWorkspaceMember>(),
  updateWorkspace: vi.fn<typeof workspaceApi.updateWorkspace>(),
  updateWorkspaceMemberRole: vi.fn<typeof workspaceApi.updateWorkspaceMemberRole>(),
}))

const FIRST_WORKSPACE: Workspace = {
  id: 1,
  name: '研发中心',
  description: '研发空间',
  ownerId: 10,
  status: 'ENABLED',
  currentUserRole: 'OWNER',
  createdAt: '2026-09-14T10:00:00',
  updatedAt: '2026-09-14T10:00:00',
}

const SECOND_WORKSPACE: Workspace = {
  ...FIRST_WORKSPACE,
  id: 2,
  name: '质量中心',
  currentUserRole: 'MEMBER',
}

const SECOND_MEMBERS: WorkspaceMember[] = [
  {
    userId: 20,
    username: 'tester',
    displayName: '测试员',
    role: 'MEMBER',
    enabled: true,
    joinedAt: '2026-09-14T11:00:00',
  },
]

describe('workspaceStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.resetAllMocks()
  })

  it('应恢复仍有权限的最近工作空间并同步刷新成员', async () => {
    localStorage.setItem('bugloop.currentWorkspaceId', '2')
    vi.mocked(workspaceApi.fetchMyWorkspaces).mockResolvedValue([FIRST_WORKSPACE, SECOND_WORKSPACE])
    vi.mocked(workspaceApi.fetchWorkspace).mockResolvedValue(SECOND_WORKSPACE)
    vi.mocked(workspaceApi.fetchWorkspaceMembers).mockResolvedValue(SECOND_MEMBERS)

    const store = useWorkspaceStore()
    await store.loadWorkspaces()

    expect(store.currentWorkspaceId).toBe(2)
    expect(store.members).toEqual(SECOND_MEMBERS)
    expect(workspaceApi.fetchWorkspace).toHaveBeenCalledWith(2)
    expect(localStorage.getItem('bugloop.currentWorkspaceId')).toBe('2')
  })

  it('创建工作空间后应立即切换到新空间', async () => {
    vi.mocked(workspaceApi.createWorkspace).mockResolvedValue(FIRST_WORKSPACE)
    vi.mocked(workspaceApi.fetchWorkspace).mockResolvedValue(FIRST_WORKSPACE)
    vi.mocked(workspaceApi.fetchWorkspaceMembers).mockResolvedValue([])

    const store = useWorkspaceStore()
    await store.create({ name: '研发中心', description: '研发空间' })

    expect(store.workspaces).toEqual([FIRST_WORKSPACE])
    expect(store.currentWorkspace).toEqual(FIRST_WORKSPACE)
    expect(localStorage.getItem('bugloop.currentWorkspaceId')).toBe('1')
  })

  it('无可用工作空间时应清除上一账号的选择和成员', async () => {
    localStorage.setItem('bugloop.currentWorkspaceId', '99')
    vi.mocked(workspaceApi.fetchMyWorkspaces).mockResolvedValue([])

    const store = useWorkspaceStore()
    await store.loadWorkspaces()

    expect(store.currentWorkspace).toBeNull()
    expect(store.members).toEqual([])
    expect(localStorage.getItem('bugloop.currentWorkspaceId')).toBeNull()
  })
})
