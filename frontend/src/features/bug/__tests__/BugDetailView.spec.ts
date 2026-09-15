/**
 * 本文件验证 Bug 详情页按角色与状态显示操作按钮，并遵守关闭后只读规则。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import BugDetailView from '../BugDetailView.vue'
import type { BugDetail } from '../bugApi'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { workspaceId: '1', bugId: '101' } }),
  useRouter: () => ({ push: vi.fn<(location: unknown) => void>() }),
}))

const CURRENT_USER = { id: 10, username: 'owner', displayName: '张三', systemRole: 'USER' as const }
vi.mock('@/features/auth/authStore', () => ({
  useAuthStore: () => ({ user: CURRENT_USER, isLoggedIn: true }),
}))

vi.mock('@/features/workspace/workspaceStore', () => ({
  useWorkspaceStore: () => ({
    isEnabled: true,
    members: [],
    currentWorkspaceId: 1,
    currentWorkspace: { id: 1, name: '研发中心', status: 'ENABLED', currentUserRole: 'MEMBER' },
  }),
}))

const detailState = { current: null as BugDetail | null }
const storeMocks = {
  loadDetail: vi.fn<() => Promise<void>>(),
  updateBasic: vi.fn<() => Promise<void>>(),
  isVersionConflict: vi.fn<(error: unknown) => boolean>(() => false),
}
vi.mock('../bugStore', () => ({
  useBugStore: () => ({
    get current() {
      return detailState.current
    },
    submitting: false,
    loadDetail: () => storeMocks.loadDetail(),
    start: vi.fn<(bugId: number) => Promise<void>>(),
    saveFix: vi.fn<(bugId: number, fixDescriptionMd: string) => Promise<void>>(),
    submit: vi.fn<(bugId: number) => Promise<void>>(),
    accept: vi.fn<(bugId: number, commentMd: string | null) => Promise<void>>(),
    reject: vi.fn<(bugId: number, commentMd: string) => Promise<void>>(),
    updateBasic: () => storeMocks.updateBasic(),
    assign: vi.fn<(bugId: number, assigneeId: number) => Promise<void>>(),
    setAcceptor: vi.fn<(bugId: number, acceptorId: number) => Promise<void>>(),
    isVersionConflict: (error: unknown) => storeMocks.isVersionConflict(error),
  }),
}))

vi.mock('md-editor-v3', () => ({
  MdEditor: { template: '<div class="md-editor" />' },
  MdPreview: { template: '<div class="md-preview" />' },
}))

const BUG_BASE: BugDetail = {
  id: 101,
  workspaceId: 1,
  bugNo: 'BUG-000101',
  title: '登录页样式错位',
  priority: 'P2',
  status: 'TODO',
  creatorId: 10,
  assigneeId: null,
  acceptorId: 10,
  creator: { id: 10, username: 'owner', displayName: '张三' },
  assignee: null,
  acceptor: { id: 10, username: 'owner', displayName: '张三' },
  reopenCount: 0,
  version: 0,
  createdAt: '2026-09-14T10:00:00',
  updatedAt: '2026-09-14T10:00:00',
  closedAt: null,
  descriptionMd: '# 问题现象',
  fixDescriptionMd: null,
  workspace: {
    id: 1,
    name: '研发中心',
    description: null,
    ownerId: 10,
    status: 'ENABLED',
    currentUserRole: 'MEMBER',
    createdAt: '2026-09-14T09:00:00',
    updatedAt: '2026-09-14T09:00:00',
  },
  attachments: [],
  latestAcceptance: null,
}

const stubs = {
  ElAlert: { props: ['title'], template: '<div class="alert-stub">{{ title }}<slot /></div>' },
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElButton: { template: '<button><slot /></button>' },
  ElInput: { template: '<input />' },
  ElSelect: { template: '<select><slot /></select>' },
  ElOption: { template: '<option />' },
  ElTag: { template: '<span class="tag"><slot /></span>' },
  ElEmpty: { template: '<div><slot /></div>' },
  ElDescriptions: { template: '<div><slot /></div>' },
  ElDescriptionsItem: { template: '<div><slot /></div>' },
  ElTable: { props: ['data'], template: '<table><slot /></table>' },
  ElTableColumn: { template: '<td><slot /></td>' },
  ElPopconfirm: { template: '<span><slot name="reference" /></span>' },
  ElDialog: { template: '<div><slot /><slot name="footer" /></div>' },
  ElForm: { template: '<form @submit.prevent="$emit(\'submit\')"><slot /></form>' },
  ElFormItem: { template: '<label><slot /></label>' },
}

async function mountDetail(bug: BugDetail) {
  detailState.current = bug
  const wrapper = mount(BugDetailView, { global: { stubs } })
  await nextTick()
  return wrapper
}

describe('BugDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    storeMocks.loadDetail.mockResolvedValue(undefined)
    storeMocks.updateBasic.mockResolvedValue(undefined)
    storeMocks.isVersionConflict.mockReturnValue(false)
  })

  it('待处理且当前用户是负责人时应显示开始处理', async () => {
    const wrapper = await mountDetail({ ...BUG_BASE, assigneeId: CURRENT_USER.id })

    expect(wrapper.text()).toContain('开始处理')
    expect(wrapper.text()).not.toContain('提交验收')
    expect(wrapper.text()).not.toContain('验收通过')
  })

  it('当前用户既不是负责人也不是验收人时不应显示状态操作', async () => {
    const wrapper = await mountDetail({
      ...BUG_BASE,
      assigneeId: 99,
      acceptorId: 99,
      status: 'PROCESSING',
    })

    expect(wrapper.text()).not.toContain('开始处理')
    expect(wrapper.text()).not.toContain('提交验收')
    expect(wrapper.text()).not.toContain('验收通过')
    // 创建者仍可编辑基础信息
    expect(wrapper.text()).toContain('编辑信息')
  })

  it('待验收且当前用户是验收人时应显示验收通过与驳回', async () => {
    const wrapper = await mountDetail({ ...BUG_BASE, status: 'WAIT_ACCEPTANCE' })

    expect(wrapper.text()).toContain('验收通过')
    expect(wrapper.text()).toContain('验收驳回')
    expect(wrapper.text()).not.toContain('开始处理')
  })

  it('已关闭的 Bug 不显示任何状态操作按钮', async () => {
    const wrapper = await mountDetail({
      ...BUG_BASE,
      status: 'CLOSED',
      closedAt: '2026-09-14T12:00:00',
    })

    expect(wrapper.text()).not.toContain('开始处理')
    expect(wrapper.text()).not.toContain('编辑修复说明')
    expect(wrapper.text()).not.toContain('提交验收')
    expect(wrapper.text()).not.toContain('验收通过')
    expect(wrapper.text()).not.toContain('编辑信息')
  })

  it('旧版本保存冲突时应先刷新最新内容再提示', async () => {
    storeMocks.updateBasic.mockRejectedValue(new Error('conflict'))
    storeMocks.isVersionConflict.mockReturnValue(true)

    const wrapper = await mountDetail({ ...BUG_BASE, assigneeId: CURRENT_USER.id })
    const editButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('编辑信息'))
    await editButton?.trigger('click')
    await nextTick()

    // 模板里四个弹窗都会渲染「保存」按钮，信息编辑弹窗是最后一个
    const saveButtons = wrapper
      .findAll('button')
      .filter((button) => button.text().trim() === '保存')
    await saveButtons[saveButtons.length - 1]?.trigger('click')
    await nextTick()
    await nextTick()

    expect(storeMocks.updateBasic).toHaveBeenCalled()
    expect(storeMocks.loadDetail).toHaveBeenCalled()
    expect(wrapper.text()).toContain('数据已被其他用户修改')
  })

  it('展示最近验收记录的状态变化', async () => {
    const wrapper = await mountDetail({
      ...BUG_BASE,
      status: 'CLOSED',
      latestAcceptance: {
        id: 1,
        acceptorId: 10,
        result: 'PASS',
        commentMd: '验证通过',
        fromStatus: 'WAIT_ACCEPTANCE',
        toStatus: 'CLOSED',
        createdAt: '2026-09-14T12:00:00',
      },
    })

    expect(wrapper.text()).toContain('最近验收记录')
    expect(wrapper.text()).toContain('待验收 → 已关闭')
    expect(wrapper.text()).toContain('验证通过')
  })
})
