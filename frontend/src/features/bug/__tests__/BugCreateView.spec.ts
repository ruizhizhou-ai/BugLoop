/**
 * 本文件验证 Bug 创建页的模板选择、覆盖确认与责任人保护规则。
 * 测试通过页面交互驱动表单状态，确保模板不会在前端意外覆盖负责人或验收人。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, inject, provide, type PropType } from 'vue'

import { flushPromises, mount } from '@vue/test-utils'

import BugCreateView from '../BugCreateView.vue'
import { SYSTEM_BUG_TEMPLATES } from '../bugTemplates'
import type { BugTemplate } from '../bugTemplateApi'

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  create: vi.fn(),
  listBugTemplates: vi.fn(),
  uploadAttachments: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { workspaceId: '1' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

// md-editor-v3 的组件没有可用于桩件匹配的组件名，必须在模块层替换才能稳定渲染文本域。
vi.mock('md-editor-v3', () => ({
  MdEditor: {
    props: { modelValue: { type: String, required: true } },
    emits: ['update:modelValue'],
    template:
      '<textarea class="markdown-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessageBox: { confirm: mocks.confirm },
  }
})

vi.mock('@/features/auth/authStore', () => ({
  useAuthStore: () => ({ user: { id: 10 } }),
}))

vi.mock('@/features/workspace/workspaceStore', () => ({
  useWorkspaceStore: () => ({
    members: [
      { userId: 10, displayName: '验收人' },
      { userId: 11, displayName: '负责人' },
    ],
  }),
}))

vi.mock('../bugStore', () => ({
  useBugStore: () => ({
    submitting: false,
    create: mocks.create,
    uploadAttachments: mocks.uploadAttachments,
  }),
}))

vi.mock('../bugTemplateApi', () => ({
  listBugTemplates: mocks.listBugTemplates,
  toPersonalBugTemplateViewModel: (template: BugTemplate) => ({ ...template, scope: 'PERSONAL' }),
  toSystemBugTemplateViewModel: (template: {
    id: string
    name: string
    title: string
    descriptionMd: string
    priority: string
    sortOrder?: number
  }) => ({
    ...template,
    scope: 'SYSTEM',
    workspaceId: null,
    creatorId: null,
    sourceBugId: null,
    sortOrder: template.sortOrder ?? 0,
    createdAt: null,
    updatedAt: null,
  }),
}))

type RadioSelect = (value: string) => void
const radioSelectKey = Symbol('radio-select')

// 单选桩通过 provide/inject 模拟 Element Plus 的组内选择事件，使测试能验证实际 v-model 与 change 流程。
const RadioGroupStub = defineComponent({
  props: { modelValue: { type: String, required: true } },
  emits: ['update:modelValue', 'change'],
  setup(_props, { emit, slots }) {
    provide<RadioSelect>(radioSelectKey, (value) => {
      emit('update:modelValue', value)
      emit('change', value)
    })
    return () => h('div', { class: 'radio-group-stub' }, slots.default?.())
  },
})

const RadioStub = defineComponent({
  props: { value: { type: String, required: true } },
  setup(props, { slots }) {
    const select = inject<RadioSelect>(radioSelectKey)
    return () =>
      h(
        'button',
        {
          class: 'radio-stub',
          'data-template-key': props.value,
          onClick: () => select?.(props.value),
        },
        slots.default?.(),
      )
  },
})

const stubs = {
  AppNotice: { template: '<div><slot /></div>' },
  ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<label><slot /></label>' },
  ElInput: {
    props: { modelValue: { type: String, required: true } },
    emits: ['update:modelValue'],
    template:
      '<input class="input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElOption: {
    props: {
      label: { type: String, required: false },
      value: { type: [String, Number] as PropType<string | number>, required: false },
    },
    template: '<option :value="value">{{ label }}</option>',
  },
  ElRadio: RadioStub,
  ElRadioGroup: RadioGroupStub,
  ElSelect: {
    props: { modelValue: { type: [String, Number], required: false } },
    emits: ['update:modelValue'],
    template:
      '<select class="select-stub" :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>',
  },
  MdEditor: {
    props: { modelValue: { type: String, required: true } },
    emits: ['update:modelValue'],
    template:
      '<textarea class="markdown-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
}

const PERSONAL_TEMPLATE: BugTemplate = {
  id: 42,
  workspaceId: 1,
  creatorId: 10,
  name: '登录失败排查',
  title: '登录后出现服务异常',
  descriptionMd: '# 登录排查',
  priority: 'P1',
  sourceBugId: null,
  sourceBugNo: null,
  sortOrder: 0,
  createdAt: '2026-09-16T10:00:00',
  updatedAt: '2026-09-16T10:00:00',
}

/** 模板创建页交互测试。 */
describe('BugCreateView', () => {
  /** 每个测试重置 API 与确认框 mock，个人模板请求均返回同一确定数据。 */
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.listBugTemplates.mockResolvedValue([PERSONAL_TEMPLATE])
    mocks.confirm.mockResolvedValue(undefined)
  })

  /** 挂载页面并等待异步个人模板加载完成。 */
  async function mountView() {
    const wrapper = mount(BugCreateView, { global: { stubs } })
    await flushPromises()
    return wrapper
  }

  /** 验证空白默认项、内置模板和个人模板都会出现在创建页。 */
  it('应加载并按来源展示空白、内置模板和我的模板', async () => {
    const wrapper = await mountView()

    expect(mocks.listBugTemplates).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('使用模板')
    expect(wrapper.text()).toContain('空白')
    expect(wrapper.text()).toContain('内置模板')
    expect(wrapper.text()).toContain('常规 Bug')
    expect(wrapper.text()).toContain('页面异常')
    expect(wrapper.text()).toContain('接口异常')
    expect(wrapper.text()).toContain('我的模板')
    expect(wrapper.text()).toContain('登录失败排查')
    expect(wrapper.text()).toContain('管理我的模板 →')
  })

  /** 验证确认覆盖后只回填三项基础字段，负责人和验收人必须保留原值。 */
  it('确认后应回填模板字段且不覆盖负责人和验收人', async () => {
    const wrapper = await mountView()
    const selects = wrapper.findAll('.select-stub')
    await selects[1]?.setValue('11')
    await selects[2]?.setValue('10')
    await wrapper.get('.input-stub').setValue('手工填写标题')
    await wrapper.get('.markdown-stub').setValue('手工填写描述')

    await wrapper.get('[data-template-key="PERSONAL:42"]').trigger('click')
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalledWith(
      '当前内容将被模板覆盖，是否继续？',
      '使用模板',
      expect.any(Object),
    )
    expect((wrapper.get('.input-stub').element as HTMLInputElement).value).toBe(
      PERSONAL_TEMPLATE.title,
    )
    expect((wrapper.get('.markdown-stub').element as HTMLTextAreaElement).value).toBe(
      PERSONAL_TEMPLATE.descriptionMd,
    )
    expect((wrapper.findAll('.select-stub')[0]?.element as HTMLSelectElement).value).toBe('P1')
    expect((wrapper.findAll('.select-stub')[1]?.element as HTMLSelectElement).value).toBe('11')
    expect((wrapper.findAll('.select-stub')[2]?.element as HTMLSelectElement).value).toBe('10')
  })

  /** 验证内置模板走同一条回填路径，同样只写入标题、描述和优先级。 */
  it('选择内置模板应回填标题、描述和优先级', async () => {
    const systemTemplate = SYSTEM_BUG_TEMPLATES.find((template) => template.id === 'api-error')
    expect(systemTemplate).toBeDefined()

    const wrapper = await mountView()
    const selects = wrapper.findAll('.select-stub')
    await selects[1]?.setValue('11')
    await selects[2]?.setValue('10')

    // 表单为空时不触发覆盖确认，选中即应用内置模板。
    await wrapper.get('[data-template-key="SYSTEM:api-error"]').trigger('click')
    await flushPromises()

    expect(mocks.confirm).not.toHaveBeenCalled()
    expect((wrapper.get('.input-stub').element as HTMLInputElement).value).toBe(
      systemTemplate!.title,
    )
    expect((wrapper.get('.markdown-stub').element as HTMLTextAreaElement).value).toBe(
      systemTemplate!.descriptionMd,
    )
    expect((selects[0]!.element as HTMLSelectElement).value).toBe(systemTemplate!.priority)
    // 负责人和验收人保持用户当前选择，不被模板改写。
    expect((selects[1]!.element as HTMLSelectElement).value).toBe('11')
    expect((selects[2]!.element as HTMLSelectElement).value).toBe('10')
  })

  /** 验证拒绝确认时保留用户手写内容，模板不会静默改写表单。 */
  it('取消覆盖确认时应保留当前标题和描述', async () => {
    mocks.confirm.mockRejectedValueOnce(new Error('cancel'))
    const wrapper = await mountView()
    await wrapper.get('.input-stub').setValue('保留标题')
    await wrapper.get('.markdown-stub').setValue('保留描述')

    await wrapper.get('[data-template-key="SYSTEM:api-error"]').trigger('click')
    await flushPromises()

    expect((wrapper.get('.input-stub').element as HTMLInputElement).value).toBe('保留标题')
    expect((wrapper.get('.markdown-stub').element as HTMLTextAreaElement).value).toBe('保留描述')
  })
})
