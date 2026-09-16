/**
 * 本文件验证模板管理弹窗的列表展示、新建、编辑、删除与内置模板只读规则。
 * 仅 Mock HTTP 层，模板数据访问与 ViewModel 归一使用真实实现。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import { flushPromises, mount } from '@vue/test-utils'

import BugTemplateManager from '../BugTemplateManager.vue'
import { ApiError } from '@/shared/api/types'
import type { BugTemplate } from '../bugTemplateApi'

const http = vi.hoisted(() => ({
  get: vi.fn<(url: string) => Promise<unknown>>(),
  post: vi.fn<(url: string, body?: unknown) => Promise<unknown>>(),
  put: vi.fn<(url: string, body?: unknown) => Promise<unknown>>(),
  delete: vi.fn<(url: string) => Promise<unknown>>(),
}))

vi.mock('@/shared/api/http', () => ({ default: http }))

vi.mock('md-editor-v3', () => ({
  MdEditor: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template:
      '<textarea class="md-editor-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
}))

const SOURCE_TEMPLATE: BugTemplate = {
  id: 42,
  workspaceId: 1,
  creatorId: 10,
  name: '登录失败排查模板',
  title: '登录后出现服务异常',
  descriptionMd: '## 问题现象',
  priority: 'P1',
  sourceBugId: 22,
  sourceBugNo: 'BUG-000022',
  sortOrder: 3,
  createdAt: '2026-09-15T10:00:00',
  updatedAt: '2026-09-16T10:00:00',
}

const MANUAL_TEMPLATE: BugTemplate = {
  ...SOURCE_TEMPLATE,
  id: 43,
  name: '数据异常模板',
  priority: 'P2',
  sourceBugId: null,
  sourceBugNo: null,
}

const stubs = {
  // 真实弹窗通过 Teleport 挂到 body，桩件直接内联渲染，便于查询列表和表单内容。
  ElDialog: {
    props: ['modelValue'],
    template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>',
  },
  ElPopconfirm: {
    emits: ['confirm'],
    template:
      '<span class="popconfirm-stub"><slot name="reference" /><button class="popconfirm-confirm" @click="$emit(\'confirm\')">确认</button></span>',
  },
}

/** 挂载已打开的模板管理弹窗，列表数据由各用例的 HTTP Mock 决定。 */
function mountManager() {
  return mount(BugTemplateManager, {
    props: { modelValue: true, workspaceId: 1 },
    global: { stubs },
  })
}

/** 编辑弹窗里的“保存”按钮与列表行操作按钮文字不同，按精确文案查找避免误点。 */
function findSaveButton(wrapper: ReturnType<typeof mountManager>) {
  return wrapper.findAll('button').find((button) => button.text().trim() === '保存')
}

describe('BugTemplateManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    http.get.mockResolvedValue([])
    http.post.mockResolvedValue(undefined)
    http.put.mockResolvedValue(undefined)
    http.delete.mockResolvedValue(undefined)
  })

  it('应展示我的模板与内置模板，且内置模板没有编辑删除入口', async () => {
    http.get.mockResolvedValue([SOURCE_TEMPLATE, MANUAL_TEMPLATE])
    const wrapper = mountManager()
    await flushPromises()

    expect(http.get).toHaveBeenCalledWith('/workspaces/1/bug-templates')
    expect(wrapper.text()).toContain('我的模板')
    expect(wrapper.text()).toContain('内置模板')
    expect(wrapper.text()).toContain('登录失败排查模板')
    expect(wrapper.text()).toContain('基于 BUG-000022')
    expect(wrapper.text()).toContain('更新于 2026-09-16')
    expect(wrapper.text()).toContain('数据异常模板')
    expect(wrapper.text()).toContain('手工创建')
    expect(wrapper.text()).toContain('我的')
    expect(wrapper.text()).toContain('内置')

    const systemRow = wrapper
      .findAll('.template-row')
      .find((row) => row.text().includes('常规 Bug'))
    expect(systemRow).toBeDefined()
    expect(systemRow!.findAll('button')).toHaveLength(0)

    const personalRow = wrapper
      .findAll('.template-row')
      .find((row) => row.text().includes('登录失败排查模板'))
    expect(personalRow!.text()).toContain('编辑')
    expect(personalRow!.text()).toContain('删除')
  })

  it('新建模板应提交到当前工作空间并在成功后刷新列表', async () => {
    http.get.mockResolvedValueOnce([]).mockResolvedValueOnce([MANUAL_TEMPLATE])
    const wrapper = mountManager()
    await flushPromises()

    const createButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('新建模板'))
    await createButton!.trigger('click')
    await nextTick()

    // 编辑弹窗内只有名称和标题两个输入框，优先级通过下拉选择不出现在这里。
    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('接口超时排查')
    await inputs[1]!.setValue('接口调用超时')
    await wrapper.find('.md-editor-stub').setValue('## 请求接口')
    await findSaveButton(wrapper)!.trigger('click')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/workspaces/1/bug-templates', {
      name: '接口超时排查',
      title: '接口调用超时',
      descriptionMd: '## 请求接口',
      priority: 'P2',
    })
    expect(http.get).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('changed')).toHaveLength(1)
    expect(wrapper.text()).toContain('数据异常模板')
  })

  it('编辑模板应回填内容并携带原排序值提交', async () => {
    http.get.mockResolvedValue([SOURCE_TEMPLATE])
    const wrapper = mountManager()
    await flushPromises()

    const editButton = wrapper.findAll('button').find((button) => button.text().trim() === '编辑')
    await editButton!.trigger('click')
    await nextTick()

    const inputs = wrapper.findAll('input')
    expect((inputs[0]!.element as HTMLInputElement).value).toBe('登录失败排查模板')
    expect((inputs[1]!.element as HTMLInputElement).value).toBe('登录后出现服务异常')
    expect((wrapper.find('.md-editor-stub').element as HTMLTextAreaElement).value).toBe(
      '## 问题现象',
    )

    await inputs[0]!.setValue('登录失败排查模板（改）')
    await findSaveButton(wrapper)!.trigger('click')
    await flushPromises()

    expect(http.put).toHaveBeenCalledWith('/bug-templates/42', {
      name: '登录失败排查模板（改）',
      title: '登录后出现服务异常',
      descriptionMd: '## 问题现象',
      priority: 'P1',
      sortOrder: 3,
    })
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('删除模板应调用删除接口并把该模板移出列表', async () => {
    http.get.mockResolvedValueOnce([SOURCE_TEMPLATE]).mockResolvedValueOnce([])
    const wrapper = mountManager()
    await flushPromises()

    await wrapper.find('.popconfirm-confirm').trigger('click')
    await flushPromises()

    expect(http.delete).toHaveBeenCalledWith('/bug-templates/42')
    expect(wrapper.text()).toContain('暂无个人模板')
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('切换工作空间后应按新空间重新加载模板', async () => {
    http.get.mockResolvedValue([SOURCE_TEMPLATE])
    const wrapper = mountManager()
    await flushPromises()
    expect(http.get).toHaveBeenLastCalledWith('/workspaces/1/bug-templates')

    await wrapper.setProps({ workspaceId: 2 })
    await flushPromises()

    expect(http.get).toHaveBeenLastCalledWith('/workspaces/2/bug-templates')
  })

  it('保存失败时应保留编辑弹窗并展示后端错误', async () => {
    http.get.mockResolvedValue([SOURCE_TEMPLATE])
    http.put.mockRejectedValue(new ApiError(40301, '无权操作其他用户的个人模板'))
    const wrapper = mountManager()
    await flushPromises()

    const editButton = wrapper.findAll('button').find((button) => button.text().trim() === '编辑')
    await editButton!.trigger('click')
    await nextTick()

    await findSaveButton(wrapper)!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('无权操作其他用户的个人模板')
    // 表单仍保留在弹窗中，用户可以修改后重试。
    expect(wrapper.find('.md-editor-stub').exists()).toBe(true)
    expect(wrapper.emitted('changed')).toBeUndefined()
  })
})
