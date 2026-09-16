/**
 * 本文件验证 Bug 列表页的渲染、空状态与新建入口的角色和空间状态控制。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type VNode } from 'vue'

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import BugListView from '../BugListView.vue'
import * as bugApi from '../bugApi'
import { useBugStore } from '../bugStore'
import { useAuthStore } from '@/features/auth/authStore'
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

const push = vi.fn<(location: unknown) => void>()
const replace = vi.fn<(location: unknown) => void>()
const routeState = {
  name: 'bug-list',
  path: '/workspaces/1/bugs',
  params: { workspaceId: '1' },
  query: {} as Record<string, string>,
  meta: {},
}
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push, replace }),
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
// 渲染为单一根节点，外层的 class（如筛选项的 filter-item--keyword）才能透传到桩上被断言到。
const RecursiveStub = defineComponent({
  name: 'RecursiveStub',
  setup(_props, { slots }) {
    const renderSlots = (): VNode[] =>
      Object.keys(slots).flatMap((name) => (slots[name]?.() ?? []) as VNode[])
    return () => h('div', renderSlots())
  },
})

// 表格使用真实组件：作用域插槽的行上下文只有真实 el-table 才能提供，
// 其余组件用递归桩保证插槽内容可断言。
const stubs = {
  ElAlert: RecursiveStub,
  ElButton: RecursiveStub,
  ElCard: RecursiveStub,
  ElDatePicker: RecursiveStub,
  // 抽屉在部分渲染模式下会留在列表页 DOM 内，保留真实类名以验证内部点击不会被外部关闭逻辑误判。
  ElDrawer: {
    props: ['modelValue'],
    template: '<aside v-if="modelValue" class="bug-detail-drawer"><slot /></aside>',
  },
  ElEmpty: RecursiveStub,
  ElInput: RecursiveStub,
  ElOption: RecursiveStub,
  ElPagination: RecursiveStub,
  ElSelect: RecursiveStub,
  // 真实弹层内容懒渲染在 body 上，桩掉后可直接断言筛选条件菜单。
  ElPopover: { template: '<div><slot name="reference" /><slot /></div>' },
  BugDetailView: true,
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
    routeState.name = 'bug-list'
    routeState.path = '/workspaces/1/bugs'
    routeState.params = { workspaceId: '1' }
    routeState.query = {}
    routeState.meta = {}
  })

  it('输入关键字后应防抖自动查询，无需按回车', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })
    const store = useBugStore()
    mountList()
    await vi.waitFor(() => expect(bugApi.fetchBugs).toHaveBeenCalledTimes(1))

    vi.useFakeTimers()
    try {
      store.query.keyword = '错位'
      await nextTick()

      vi.advanceTimersByTime(299)
      expect(bugApi.fetchBugs).toHaveBeenCalledTimes(1)

      vi.advanceTimersByTime(1)
      await nextTick()
      await nextTick()
      expect(bugApi.fetchBugs).toHaveBeenCalledTimes(2)
      const calls = vi.mocked(bugApi.fetchBugs).mock.calls
      expect(calls[1]?.[1]).toMatchObject({ page: 1, keyword: '错位' })
    } finally {
      vi.useRealTimers()
    }
  })

  it('进入列表入口时应清空上一个入口遗留的筛选条件', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })
    const store = useBugStore()
    // 模拟从「待我验收」切回：Store 中仍保留上一个入口的预置条件
    store.query.acceptorId = 10
    store.query.status = 'WAIT_ACCEPTANCE'

    mountList()
    await vi.waitFor(() => expect(bugApi.fetchBugs).toHaveBeenCalled())

    expect(store.query.acceptorId).toBeUndefined()
    expect(store.query.status).toBeUndefined()
    const calls = vi.mocked(bugApi.fetchBugs).mock.calls
    const params = calls[calls.length - 1]?.[1]
    expect(params?.acceptorId).toBeUndefined()
    expect(params?.status).toBeUndefined()
  })

  it('进入「待我验收」入口时应应用验收人预置条件', async () => {
    const auth = useAuthStore()
    auth.user = {
      id: 10,
      username: 'owner',
      displayName: '张三',
      systemRole: 'USER',
      createdAt: '2026-09-14T09:00:00',
    }
    routeState.meta = { bugListPreset: 'acceptance' }
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })

    mountList()
    await vi.waitFor(() => expect(bugApi.fetchBugs).toHaveBeenCalled())

    const calls = vi.mocked(bugApi.fetchBugs).mock.calls
    const params = calls[calls.length - 1]?.[1]
    expect(params?.acceptorId).toBe(10)
    expect(params?.status).toBe('WAIT_ACCEPTANCE')
  })

  it('筛选区可以整体收起，只保留列表清单且不清空已选条件', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [BUG_ROW],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const wrapper = mountList()
    await vi.waitFor(() => expect(wrapper.text()).toContain('BUG-000101'))
    const callsBeforeCollapse = vi.mocked(bugApi.fetchBugs).mock.calls.length

    expect(wrapper.find('.bug-list__filters').attributes('style') ?? '').not.toContain(
      'display: none',
    )
    expect(wrapper.get('.filter-panel-toggle').text()).toContain('收起筛选')

    await wrapper.get('.filter-panel-toggle').trigger('click')

    // v-show 折叠筛选区：jsdom 的 computed display 在样式表存在时不看内联值，直接断言行内样式。
    expect(wrapper.find('.bug-list__filters').attributes('style')).toContain('display: none')
    expect(wrapper.find('.filter-panel-toggle').exists()).toBe(true)
    expect(wrapper.get('.filter-panel-toggle').text()).toContain('展开筛选')
    expect(wrapper.find('.el-table__body-wrapper tbody tr').exists()).toBe(true)
    // 收起只保留当前会话状态，不写入本地存储、更不会重新查询或清空结果。
    expect(localStorage.getItem('bugloop.bugListFiltersCollapsed')).toBeNull()
    expect(vi.mocked(bugApi.fetchBugs).mock.calls.length).toBe(callsBeforeCollapse)
    expect(wrapper.text()).toContain('BUG-000101')

    await wrapper.get('.filter-panel-toggle').trigger('click')
    expect(wrapper.find('.bug-list__filters').attributes('style') ?? '').not.toContain(
      'display: none',
    )
    expect(wrapper.get('.filter-panel-toggle').text()).toContain('收起筛选')
  })

  it('筛选条件默认精简展示，当前会话内可展开全部但不跨刷新持久化', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })

    const wrapper = mountList()
    await vi.waitFor(() => expect(bugApi.fetchBugs).toHaveBeenCalled())

    expect(wrapper.find('.filter-item--keyword').exists()).toBe(true)
    expect(wrapper.find('.filter-item--status').exists()).toBe(false)
    expect(wrapper.find('.filter-item--date').exists()).toBe(false)
    expect(wrapper.find('.filter-customize').exists()).toBe(true)

    await wrapper.find('.filter-customize__toggle-all input').setValue(true)

    expect(wrapper.find('.filter-item--keyword').exists()).toBe(true)
    expect(wrapper.find('.filter-item--status').exists()).toBe(true)
    expect(wrapper.find('.filter-item--date').exists()).toBe(true)
    expect(localStorage.getItem('bugloop.bugListFilters')).toBeNull()
  })

  it('不同列表入口在同一会话中保存各自筛选，新的 Pinia 实例则恢复默认条件', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })
    const store = useBugStore()
    const allList = mountList()
    await vi.waitFor(() => expect(bugApi.fetchBugs).toHaveBeenCalled())
    store.query.keyword = '登录'
    allList.unmount()

    routeState.name = 'assigned-bugs'
    routeState.path = '/workspaces/1/bugs/assigned'
    routeState.meta = { bugListPreset: 'assigned' }
    const assignedList = mountList()
    await vi.waitFor(() => expect(useBugStore().query.assigneeId).toBeUndefined())
    assignedList.unmount()

    routeState.name = 'bug-list'
    routeState.path = '/workspaces/1/bugs'
    routeState.meta = {}
    const restoredAllList = mountList()
    await vi.waitFor(() => expect(useBugStore().query.keyword).toBe('登录'))
    restoredAllList.unmount()

    // 新 Pinia 实例等同浏览器刷新：内存快照被清空，只保留默认筛选。
    setActivePinia(createPinia())
    const refreshedAllList = mountList()
    await vi.waitFor(() => expect(bugApi.fetchBugs).toHaveBeenCalled())
    expect(useBugStore().query.keyword).toBeUndefined()
    refreshedAllList.unmount()
  })

  it('点击 Bug 后应保留列表路由并用查询参数打开右侧详情', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [BUG_ROW],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const wrapper = mountList()
    await vi.waitFor(() => expect(wrapper.text()).toContain('BUG-000101'))
    // 列表不再保留无实际菜单的三点入口，整行点击是打开右侧详情的唯一入口。
    await wrapper.get('.el-table__body-wrapper tbody tr').trigger('click')

    expect(push).toHaveBeenCalledWith({
      name: 'bug-list',
      params: { workspaceId: '1' },
      query: { bugId: '101' },
    })
  })

  it('打开详情后点击列表空白处应移除查询参数并收起抽屉', async () => {
    routeState.query = { bugId: '101' }
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [BUG_ROW],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const wrapper = mountList()
    await vi.waitFor(() => expect(wrapper.text()).toContain('BUG-000101'))
    await wrapper.get('main.bug-list').trigger('click')

    expect(replace).toHaveBeenCalledWith({
      name: 'bug-list',
      params: { workspaceId: '1' },
      query: {},
    })
  })

  it('点击详情抽屉内部不应关闭当前详情', async () => {
    routeState.query = { bugId: '101' }
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [BUG_ROW],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const wrapper = mountList()
    await vi.waitFor(() => expect(wrapper.find('.bug-detail-drawer').exists()).toBe(true))
    await wrapper.get('.bug-detail-drawer').trigger('click')

    expect(replace).not.toHaveBeenCalled()
  })

  it('详情打开时点击另一条列表行应直接切换详情而非关闭抽屉', async () => {
    routeState.query = { bugId: '101' }
    const anotherBug = { ...BUG_ROW, id: 102, bugNo: 'BUG-000102', title: '导出文件异常' }
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [BUG_ROW, anotherBug],
      total: 2,
      page: 1,
      pageSize: 20,
    })

    const wrapper = mountList()
    await vi.waitFor(() => expect(wrapper.text()).toContain('BUG-000102'))
    const rows = wrapper.findAll('.el-table__body-wrapper tbody tr')
    expect(rows).toHaveLength(2)
    await rows[1]!.trigger('click')

    expect(push).toHaveBeenCalledWith({
      name: 'bug-list',
      params: { workspaceId: '1' },
      query: { bugId: '102' },
    })
    expect(replace).not.toHaveBeenCalled()
  })

  it('应按当前工作空间加载列表并展示编号、标题与中文状态优先级', async () => {
    vi.mocked(bugApi.fetchBugs).mockResolvedValue({
      records: [BUG_ROW],
      total: 1,
      page: 1,
      pageSize: 20,
    })

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
