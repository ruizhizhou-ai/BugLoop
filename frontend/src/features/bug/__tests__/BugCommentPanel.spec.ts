/**
 * 本文件验证评论面板的一级回复、逻辑删除提示与操作权限展示。
 * 接口细节由 Store 单测覆盖，这里聚焦详情页下方评论组件的用户交互。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'

import BugCommentPanel from '../BugCommentPanel.vue'
import type { BugComment } from '../bugApi'

const CURRENT_USER = { id: 10, username: 'owner', displayName: '张三', systemRole: 'USER' as const }
vi.mock('@/features/auth/authStore', () => ({
  useAuthStore: () => ({ user: CURRENT_USER }),
}))

const commentState = {
  comments: [] as BugComment[],
  commentsTotal: 0,
  commentsPage: 1,
  submitting: false,
}
const storeMocks = {
  loadComments: vi.fn<(bugId: number, page?: number) => Promise<void>>(),
  addComment: vi.fn<(bugId: number, content: string) => Promise<void>>(),
  replyToComment: vi.fn<(bugId: number, parentId: number, content: string) => Promise<void>>(),
  deleteCommentById: vi.fn<(bugId: number, commentId: number) => Promise<void>>(),
}
vi.mock('../bugStore', () => ({
  useBugStore: () => ({
    get comments() {
      return commentState.comments
    },
    get commentsTotal() {
      return commentState.commentsTotal
    },
    get commentsPage() {
      return commentState.commentsPage
    },
    get submitting() {
      return commentState.submitting
    },
    loadComments: (...args: [number, number?]) => storeMocks.loadComments(...args),
    addComment: (...args: [number, string]) => storeMocks.addComment(...args),
    replyToComment: (...args: [number, number, string]) => storeMocks.replyToComment(...args),
    deleteCommentById: (...args: [number, number]) => storeMocks.deleteCommentById(...args),
  }),
}))

vi.mock('md-editor-v3', () => ({
  MdEditor: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template:
      '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  MdPreview: { props: ['modelValue'], template: '<div class="md-preview">{{ modelValue }}</div>' },
}))

const stubs = {
  ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  ElEmpty: { template: '<div><slot /></div>' },
  ElPopconfirm: { template: '<span><slot name="reference" /></span>' },
  AppNotice: { props: ['message'], template: '<div class="notice">{{ message }}</div>' },
}

const ROOT_COMMENT: BugComment = {
  commentId: 1,
  bugId: 101,
  userId: 10,
  username: 'owner',
  displayName: '张三',
  avatar: null,
  contentMd: '顶级评论',
  parentId: null,
  replyUserId: null,
  replyUsername: null,
  parentDeleted: false,
  deleted: false,
  createdAt: '2026-09-15T10:00:00',
}

function mountPanel() {
  return mount(BugCommentPanel, {
    props: { bugId: 101, writable: true },
    global: { stubs },
  })
}

describe('BugCommentPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    commentState.comments = [ROOT_COMMENT]
    commentState.commentsTotal = 1
    commentState.commentsPage = 1
    storeMocks.loadComments.mockResolvedValue(undefined)
    storeMocks.addComment.mockResolvedValue(undefined)
    storeMocks.replyToComment.mockResolvedValue(undefined)
    storeMocks.deleteCommentById.mockResolvedValue(undefined)
  })

  it('选择顶级评论后应展示回复对象并调用一级回复接口', async () => {
    const wrapper = mountPanel()
    await nextTick()
    const replyButton = wrapper.findAll('button').find((button) => button.text() === '回复')
    await replyButton?.trigger('click')

    expect(wrapper.text()).toContain('回复 @张三')
    await wrapper.get('textarea').setValue('收到，我来处理')
    const submitButton = wrapper.findAll('button').find((button) => button.text() === '发布回复')
    await submitButton?.trigger('click')

    expect(storeMocks.replyToComment).toHaveBeenCalledWith(101, 1, '收到，我来处理')
    expect(storeMocks.addComment).not.toHaveBeenCalled()
  })

  it('多层回复应压缩到同一父评论下，并可独立收起回复列表', async () => {
    commentState.comments = [
      ROOT_COMMENT,
      {
        ...ROOT_COMMENT,
        commentId: 2,
        userId: 11,
        username: 'developer',
        displayName: '李四',
        contentMd: '保留的回复',
        parentId: 1,
        replyUserId: 10,
        replyUsername: 'owner',
        parentDeleted: false,
      },
    ]
    commentState.commentsTotal = 2
    const wrapper = mountPanel()
    await nextTick()

    expect(wrapper.findAll('.comment-thread')).toHaveLength(1)
    const threadToggle = wrapper.find('.comment-thread__toggle')
    expect(threadToggle.text()).toContain('收起回复')
    expect(threadToggle.attributes('aria-expanded')).toBe('true')
    await threadToggle.trigger('click')
    expect(threadToggle.attributes('aria-expanded')).toBe('false')
    expect(threadToggle.text()).toContain('展开 1 条回复')
    expect(wrapper.text()).toContain('↪ 回复 @owner：顶级评论')
    expect(wrapper.findAll('button').filter((button) => button.text() === '回复')).toHaveLength(2)
  })

  it('删除父评论后应保留多层后代并隐藏被删除正文', async () => {
    const deletedParent: BugComment = { ...ROOT_COMMENT, contentMd: null, deleted: true }
    const child: BugComment = {
      ...ROOT_COMMENT,
      commentId: 2,
      userId: 11,
      username: 'developer',
      displayName: '李四',
      contentMd: '保留的子评论',
      parentId: 1,
      replyUserId: 10,
      replyUsername: 'owner',
      parentDeleted: true,
    }
    const nestedChild: BugComment = {
      ...child,
      commentId: 3,
      userId: 12,
      username: 'tester',
      displayName: '王五',
      contentMd: '继续跟进',
      parentId: 2,
      replyUserId: 11,
      replyUsername: 'developer',
      parentDeleted: false,
    }
    commentState.comments = [deletedParent, child, nestedChild]
    commentState.commentsTotal = 3
    const wrapper = mountPanel()
    await nextTick()

    expect(wrapper.findAll('.comment-thread')).toHaveLength(1)
    expect(wrapper.text()).toContain('该评论已删除')
    expect(wrapper.text()).toContain('↪ 回复内容已删除')
    expect(wrapper.text()).toContain('↪ 回复 @developer：保留的子评论')
    expect(wrapper.text()).toContain('继续跟进')
  })
})
