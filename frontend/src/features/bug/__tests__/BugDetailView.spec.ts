/**
 * 本文件验证 Bug 详情页按角色与状态显示操作按钮，并遵守关闭后只读规则。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import BugDetailView from '../BugDetailView.vue'
import type {
  BugAcceptanceRecord,
  BugComment,
  BugDescriptionHistoryDetail,
  BugDescriptionHistoryItem,
  BugDetail,
  BugOperationLog,
} from '../bugApi'

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

const detailState = {
  current: null as BugDetail | null,
  comments: [] as BugComment[],
  commentsTotal: 0,
  commentsPage: 1,
  logs: [] as BugOperationLog[],
  history: [] as BugDescriptionHistoryItem[],
  historyDetail: null as BugDescriptionHistoryDetail | null,
  acceptances: [] as BugAcceptanceRecord[],
  traceLoading: false,
}
const storeMocks = {
  loadDetail: vi.fn<() => Promise<void>>(),
  updateBasic: vi.fn<() => Promise<void>>(),
  loadComments: vi.fn<() => Promise<void>>(),
  loadTrace: vi.fn<() => Promise<void>>(),
  addComment: vi.fn<() => Promise<void>>(),
  reject: vi.fn<(bugId: number, commentMd: string) => Promise<void>>(),
  uploadAttachment: vi.fn<
    (bugId: number, file: File, business?: { bizType: string; bizId: number }) => Promise<void>
  >(),
  isVersionConflict: vi.fn<(error: unknown) => boolean>(() => false),
}
vi.mock('../bugStore', () => ({
  useBugStore: () => ({
    get current() {
      return detailState.current
    },
    get comments() {
      return detailState.comments
    },
    get commentsTotal() {
      return detailState.commentsTotal
    },
    get commentsPage() {
      return detailState.commentsPage
    },
    get logs() {
      return detailState.logs
    },
    get history() {
      return detailState.history
    },
    get historyDetail() {
      return detailState.historyDetail
    },
    get acceptances() {
      return detailState.acceptances
    },
    get traceLoading() {
      return detailState.traceLoading
    },
    submitting: false,
    loadDetail: () => storeMocks.loadDetail(),
    loadComments: () => storeMocks.loadComments(),
    loadTrace: () => storeMocks.loadTrace(),
    addComment: () => storeMocks.addComment(),
    start: vi.fn<(bugId: number) => Promise<void>>(),
    saveFix: vi.fn<(bugId: number, fixDescriptionMd: string) => Promise<void>>(),
    submit: vi.fn<(bugId: number) => Promise<void>>(),
    accept: vi.fn<(bugId: number, commentMd: string | null) => Promise<void>>(),
    reject: (bugId: number, commentMd: string) => storeMocks.reject(bugId, commentMd),
    updateBasic: () => storeMocks.updateBasic(),
    assign: vi.fn<(bugId: number, assigneeId: number) => Promise<void>>(),
    setAcceptor: vi.fn<(bugId: number, acceptorId: number) => Promise<void>>(),
    uploadAttachment: (bugId: number, file: File, business?: { bizType: string; bizId: number }) =>
      storeMocks.uploadAttachment(bugId, file, business),
    removeAttachment: vi.fn<(bugId: number, attachmentId: number) => Promise<void>>(),
    openHistoryDetail: vi.fn<(bugId: number, versionNo: number) => Promise<void>>(),
    closeHistoryDetail: vi.fn<() => void>(),
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
  ElInput: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElSelect: { template: '<select><slot /></select>' },
  ElOption: { template: '<option />' },
  ElTag: { template: '<span class="tag"><slot /></span>' },
  ElEmpty: { template: '<div><slot /></div>' },
  ElDescriptions: { template: '<div><slot /></div>' },
  ElDescriptionsItem: { template: '<div><slot /></div>' },
  ElTable: { props: ['data'], template: '<table><slot /></table>' },
  ElTableColumn: { template: '<td><slot /></td>' },
  ElPopconfirm: { template: '<span><slot name="reference" /></span>' },
  ElTimeline: { template: '<div class="timeline"><slot /></div>' },
  ElTimelineItem: {
    props: ['timestamp'],
    template: '<div class="timeline-item">{{ timestamp }}<slot /></div>',
  },
  ElDialog: { template: '<div><slot /><slot name="footer" /></div>' },
  ElForm: { template: '<form @submit.prevent="$emit(\'submit\')"><slot /></form>' },
  ElFormItem: { template: '<label><slot /></label>' },
  AppIcon: { template: '<span class="app-icon-stub" />' },
  BugCommentPanel: true,
}

async function mountDetail(bug: BugDetail, props: Record<string, unknown> = {}) {
  detailState.current = bug
  const wrapper = mount(BugDetailView, { props, global: { stubs } })
  await nextTick()
  return wrapper
}

describe('BugDetailView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    detailState.comments = []
    detailState.commentsTotal = 0
    detailState.commentsPage = 1
    detailState.logs = []
    detailState.history = []
    detailState.historyDetail = null
    detailState.acceptances = []
    detailState.traceLoading = false
    storeMocks.loadDetail.mockResolvedValue(undefined)
    storeMocks.updateBasic.mockResolvedValue(undefined)
    storeMocks.loadComments.mockResolvedValue(undefined)
    storeMocks.loadTrace.mockResolvedValue(undefined)
    storeMocks.addComment.mockResolvedValue(undefined)
    storeMocks.reject.mockResolvedValue(undefined)
    storeMocks.uploadAttachment.mockResolvedValue(undefined)
    storeMocks.isVersionConflict.mockReturnValue(false)
  })

  it('抽屉模式应按属性行展示 Bug 的核心字段', async () => {
    const wrapper = await mountDetail(
      { ...BUG_BASE, assignee: { id: 11, username: 'dev', displayName: '李四' } },
      { drawerMode: true },
    )

    expect(wrapper.find('.bug-properties').exists()).toBe(true)
    expect(wrapper.text()).toContain('属性')
    expect(wrapper.text()).toContain('状态')
    expect(wrapper.text()).toContain('负责人')
    expect(wrapper.text()).toContain('优先级')
    expect(wrapper.text()).toContain('提交人')
    expect(wrapper.text()).toContain('验收人')
    expect(wrapper.text()).toContain('创建时间')
    expect(wrapper.text()).toContain('更新时间')
    expect(wrapper.text()).toContain('李四')
  })

  it('抽屉模式应在属性前展示编号、标题和问题描述摘要', async () => {
    const wrapper = await mountDetail(BUG_BASE, { drawerMode: true })

    expect(wrapper.find('.bug-detail__drawer-overview').exists()).toBe(true)
    expect(wrapper.text()).toContain(BUG_BASE.bugNo)
    expect(wrapper.text()).toContain(BUG_BASE.title)
    expect(wrapper.find('.bug-detail__description-trigger').text()).toContain('问题现象')
    expect(wrapper.text()).toContain('最后更新于')
  })

  it('待处理且当前用户是负责人时应显示开始处理', async () => {
    const wrapper = await mountDetail({ ...BUG_BASE, assigneeId: CURRENT_USER.id })

    expect(wrapper.text()).toContain('开始处理')
    expect(wrapper.text()).not.toContain('提交验收')
    expect(wrapper.text()).not.toContain('验收通过')
  })

  it('处理中应锁定负责人，但空间管理员仍可调整验收人', async () => {
    const wrapper = await mountDetail({
      ...BUG_BASE,
      status: 'PROCESSING',
      workspace: { ...BUG_BASE.workspace, currentUserRole: 'OWNER' },
    })

    expect(wrapper.text()).not.toContain('指派')
    expect(wrapper.text()).toContain('修改验收人')
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

  it('验收记录应展示完整历史而不是只显示最近一条', async () => {
    detailState.acceptances = [
      {
        id: 2,
        acceptorId: 11,
        acceptorUsername: 'tester',
        acceptorDisplayName: '李四',
        result: 'PASS',
        commentMd: null,
        fromStatus: 'WAIT_ACCEPTANCE',
        toStatus: 'CLOSED',
        createdAt: '2026-09-15T12:00:00',
        attachments: [],
      },
      {
        id: 1,
        acceptorId: 11,
        acceptorUsername: 'tester',
        acceptorDisplayName: '李四',
        result: 'REJECT',
        commentMd: null,
        fromStatus: 'WAIT_ACCEPTANCE',
        toStatus: 'REOPENED',
        createdAt: '2026-09-14T12:00:00',
        attachments: [],
      },
    ]
    const wrapper = await mountDetail({ ...BUG_BASE, status: 'CLOSED' })

    expect(wrapper.text()).toContain('验收记录')
    expect(wrapper.text()).toContain('待验收 → 已关闭')
    expect(wrapper.text()).toContain('待验收 → 重新打开')
    expect(wrapper.text()).toContain('李四')
  })

  it('操作日志页签展示操作者和业务描述', async () => {
    detailState.logs = [
      {
        id: 1,
        operatorId: 10,
        operatorUsername: 'owner',
        operatorDisplayName: '张三',
        operationType: 'CREATE_BUG',
        fieldName: null,
        oldValue: null,
        newValue: 'BUG-000101',
        description: '创建了 BUG-000101',
        createdAt: '2026-09-14T10:00:00',
      },
    ]
    const wrapper = await mountDetail(BUG_BASE)

    const logsTab = wrapper.findAll('button').find((button) => button.text().includes('操作日志'))
    await logsTab?.trigger('click')

    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('创建了 BUG-000101')
  })

  it('关闭后的 Bug 不再显示附件附加入口', async () => {
    const open = await mountDetail({ ...BUG_BASE, assigneeId: CURRENT_USER.id })
    expect(open.find('.attachment-section__add').exists()).toBe(true)

    const closed = await mountDetail({ ...BUG_BASE, status: 'CLOSED' })
    expect(closed.find('.attachment-section__add').exists()).toBe(false)
  })

  it('验收驳回时应在原因提交成功后上传暂存的问题截图', async () => {
    const wrapper = await mountDetail({
      ...BUG_BASE,
      status: 'WAIT_ACCEPTANCE',
      // Store 的真实实现会用驳回响应刷新 current；此轻量 Mock 预置本次记录主键以验证绑定参数。
      latestAcceptance: {
        id: 88,
        acceptorId: CURRENT_USER.id,
        result: 'REJECT',
        commentMd: '登录页仍出现空白区域',
        fromStatus: 'WAIT_ACCEPTANCE',
        toStatus: 'REOPENED',
        createdAt: '2026-09-16T10:00:00',
      },
    })
    const rejectButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('验收驳回'))
    await rejectButton?.trigger('click')

    const screenshot = new File(['screen-image'], 'rejection-screen.png', { type: 'image/png' })
    const screenshotInput = wrapper.findAll('input[type="file"]')[1]!
    Object.defineProperty(screenshotInput.element, 'files', { configurable: true, value: [screenshot] })
    await screenshotInput.trigger('change')

    expect(wrapper.text()).toContain('rejection-screen.png')

    const reasonInput = wrapper.findAll('input').find((input) => input.attributes('type') !== 'file')
    await reasonInput?.setValue('登录页仍出现空白区域')
    const confirmButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认驳回'))
    await confirmButton?.trigger('click')
    await flushPromises()

    expect(storeMocks.reject).toHaveBeenCalledWith(101, '登录页仍出现空白区域')
    expect(storeMocks.uploadAttachment).toHaveBeenCalledWith(101, screenshot, {
      bizType: 'ACCEPT_REJECT',
      bizId: 88,
    })
  })

  it('附件列表默认收起，点击附件标题后才展开文件行', async () => {
    const wrapper = await mountDetail({
      ...BUG_BASE,
      attachments: [
        {
          id: 1,
          bugId: 101,
          bizType: 'BUG_PROCESS',
          bizId: 101,
          originalName: 'console-error.png',
          fileSize: 199_680,
          contentType: 'image/png',
          uploaderId: CURRENT_USER.id,
          uploaderName: '张三',
          uploaderAvatar: null,
          createdAt: '2026-09-14T10:10:00',
          canDelete: true,
        },
      ],
    })

    const content = wrapper.find('.attachment-section__content')
    const contentElement = content.element as HTMLElement
    const toggle = wrapper.find('.attachment-section__toggle')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(contentElement.style.display).toBe('none')

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(contentElement.style.display).not.toBe('none')
    expect(wrapper.text()).toContain('console-error.png')
  })
})
