/**
 * 本文件验证 Bug 列表页的渲染、空状态与新建入口的角色和空间状态控制。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, type VNode } from 'vue'

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import BugListView from '../BugListView.vue'
import * as bugApi from '../bugApi'
import type { BugSummary } from '../bugApi'

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

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { workspaceId: '1' } }),
  useRouter: () => ({ push }),
  RouterLink: { template: '<a><slot /></a>' },
}))

const workspaceState = { isEnabled: true }
vi.mock('@/features/workspace/workspaceStore', () => ({
  useWorkspaceStore: () => ({
    isEnabled: workspaceState.isEnabled,
    members: [],
    currentWorkspaceId: 1,
    workspaces: [],
    loading: false,
  }),
}))

const BUG_ROW: BugSummary = {
  id: 101,
  workspaceId: 1,
  bugNo: 'BUG-000101',
  title: '登录页样式错位',
  priority: 'P1',
  status: 'PROCESSING',
  creatorId: 10,
  assigneeId: 11,
  acceptorId: 10,
  creator: { id: 10, username: 'owner', displayName: '张三' },
  assignee: { id: 11, username: 'dev', displayName: '李四' },
  acceptor: { id: 10, username: 'owner', displayName: '张三' },
  reopenCount: 0,
  version: 1,
  createdAt: '2026-09-14T10:00:00',
  updatedAt: '2026-09-14T11:30:00',
  closedAt: null,
}

// 递归渲染所有插槽：Element Plus 的卡片、下拉等组件都是嵌套插槽，逐层透传才能断言到内容。
const RecursiveStub = defineComponent({
  name: 'RecursiveStub',
  setup(_props, { slots }) {
    const renderSlots = (): VNode[] =>
      Object.keys(slots).flatMap((name) => (slots[name]?.() ?? []) as VNode[])
    return renderSlots
  },
})

// 表格使用真实组件：作用域插槽的行上下文只有真实 el-table 才能提供，
// 其余组件用递归桩保证插槽内容可断言。
const stubs = {
  ElAlert: RecursiveStub,
  ElButton: RecursiveStub,
  ElCard: RecursiveStub,
  ElDatePicker: RecursiveStub,
  ElEmpty: RecursiveStub,
  ElInput: RecursiveStub,
  ElOption: RecursiveStub,
  ElPagination: RecursiveStub,
  ElSelect: RecursiveStub,
}

function mountList() {
  return mount(BugListView, {
    global: { stubs },
  })
}

describe('BugListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    workspaceState.isEnabled = true
  })

  it('应按当前工作空间加载列表并展示编号、标题与中文状态优先级', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({ records: [BUG_ROW], total: 1, page: 1, pageSize: 20 })

    const wrapper = mountList()
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('BUG-000101')
    })

    expect(bugApi.fetchBugs).toHaveBeenCalledWith(1, expect.objectContaining({ page: 1 }))
    const text = wrapper.text()
    expect(text).toContain('登录页样式错位')
    expect(text).toContain('高')
    expect(text).toContain('处理中')
    expect(text).toContain('李四')
    expect(text).toContain('新建 Bug')
  })

  it('没有数据时应展示空状态并保留创建入口', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({ records: [], total: 0, page: 1, pageSize: 20 })

    const wrapper = mountList()
    await vi.waitFor(() => {
      expect(bugApi.fetchBugs).toHaveBeenCalled()
    })

    expect(wrapper.text()).toContain('创建第一个 Bug')
  })

  it('工作空间停用时应隐藏新建入口', async () => {
    workspaceState.isEnabled = false
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({ records: [], total: 0, page: 1, pageSize: 20 })

    const wrapper = mountList()
    await vi.waitFor(() => {
      expect(bugApi.fetchBugs).toHaveBeenCalled()
    })

    expect(wrapper.text()).not.toContain('新建 Bug')
    expect(wrapper.text()).not.toContain('创建第一个 Bug')
  })
})
