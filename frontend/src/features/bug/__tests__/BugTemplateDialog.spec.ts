/**
 * 本文件验证“存为模板”弹窗：字段范围、打开回填、保存参数与失败保留。
 * 接口只通过 HTTP Mock 断言提交内容，不渲染真实浮层。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import { flushPromises, mount } from '@vue/test-utils'

import BugTemplateDialog from '../BugTemplateDialog.vue'
import { ApiError } from '@/shared/api/types'

const http = vi.hoisted(() => ({
  post: vi.fn<(url: string, body?: unknown) => Promise<unknown>>(),
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

const SOURCE = {
  bugId: 101,
  sourceTitle: '登录后出现服务异常',
  sourceDescriptionMd: '## 问题现象\n\n登录后出现 500',
  sourcePriority: 'P1' as const,
}

const stubs = {
  // 真实弹窗通过 Teleport 挂到 body，桩件直接内联渲染，便于查询表单内容。
  ElDialog: {
    props: ['modelValue'],
    template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>',
  },
}

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(BugTemplateDialog, {
    props: { modelValue: true, ...SOURCE, ...props },
    global: { stubs },
  })
}

describe('BugTemplateDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    http.post.mockResolvedValue(undefined)
  })

  it('弹窗只包含模板字段，不出现其他业务数据', async () => {
    const wrapper = mountDialog()
    await nextTick()

    expect(wrapper.findAll('.el-form-item__label').map((label) => label.text())).toEqual([
      '模板名称',
      '标题',
      '详细说明（Markdown）',
      '优先级',
    ])
  })

  it('打开时应回填来源 Bug 的标题和描述，模板名称为空', async () => {
    const wrapper = mountDialog()
    await nextTick()

    const inputs = wrapper.findAll('input')
    expect((inputs[0]!.element as HTMLInputElement).value).toBe('')
    expect((inputs[1]!.element as HTMLInputElement).value).toBe(SOURCE.sourceTitle)
    expect((wrapper.find('.md-editor-stub').element as HTMLTextAreaElement).value).toBe(
      SOURCE.sourceDescriptionMd,
    )
  })

  it('保存时应提交名称、标题、描述和优先级并关闭弹窗', async () => {
    const wrapper = mountDialog()
    await nextTick()

    await wrapper.findAll('input')[0]!.setValue('登录失败排查模板')
    const saveButton = wrapper.findAll('button').find((button) => button.text().trim() === '保存')
    await saveButton?.trigger('click')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/bugs/101/save-as-template', {
      name: '登录失败排查模板',
      title: SOURCE.sourceTitle,
      descriptionMd: SOURCE.sourceDescriptionMd,
      priority: SOURCE.sourcePriority,
    })
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
  })

  it('模板名称为空时应阻止提交并保持弹窗打开', async () => {
    const wrapper = mountDialog()
    await nextTick()

    const saveButton = wrapper.findAll('button').find((button) => button.text().trim() === '保存')
    await saveButton?.trigger('click')
    await flushPromises()

    expect(http.post).not.toHaveBeenCalled()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('后端拒绝保存时应保留弹窗并展示错误原因', async () => {
    http.post.mockRejectedValue(new ApiError(40301, '只能将自己创建的 Bug 保存为模板'))
    const wrapper = mountDialog()
    await nextTick()

    await wrapper.findAll('input')[0]!.setValue('登录失败排查模板')
    const saveButton = wrapper.findAll('button').find((button) => button.text().trim() === '保存')
    await saveButton?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('只能将自己创建的 Bug 保存为模板')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('再次打开时应清空上一次的名称并按当前来源 Bug 重新回填', async () => {
    const wrapper = mountDialog()
    await nextTick()

    await wrapper.findAll('input')[0]!.setValue('上一次的模板名称')
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true, sourceTitle: '新的标题' })
    await nextTick()

    const inputs = wrapper.findAll('input')
    expect((inputs[0]!.element as HTMLInputElement).value).toBe('')
    expect((inputs[1]!.element as HTMLInputElement).value).toBe('新的标题')
  })
})
