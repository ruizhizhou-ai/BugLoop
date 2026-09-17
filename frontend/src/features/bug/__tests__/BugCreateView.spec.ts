/**
 * 本文件验证 Bug 创建页的模板选择、覆盖确认与责任人保护规则。
 * 测试通过页面交互驱动表单状态，确保模板不会在前端意外覆盖负责人或验收人。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PropType } from 'vue'

import { flushPromises, mount } from '@vue/test-utils'

import BugCreateView from '../BugCreateView.vue'
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
}))

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
  ElSelect: {
    props: { modelValue: { type: [String, Number], required: false } },
    emits: ['update:modelValue', 'change'],
    template:
      '<select v-bind="$attrs" class="select-stub" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
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
  scope: 'PERSONAL',
  shared: false,
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

/** 数据库返回的系统模板，用于验证创建页不再依赖前端静态常量。 */
const SYSTEM_TEMPLATES: BugTemplate[] = [
  {
    ...PERSONAL_TEMPLATE,
    id: 101,
    workspaceId: 0,
    creatorId: 0,
    scope: 'SYSTEM',
    name: '常规 Bug',
    title: '请简要描述问题现象',
    descriptionMd: '常规模板',
    priority: 'P2',
  },
  {
    ...PERSONAL_TEMPLATE,
    id: 102,
    workspaceId: 0,
    creatorId: 0,
    scope: 'SYSTEM',
    name: '页面异常',
    title: '页面出现异常',
    descriptionMd: '页面模板',
    priority: 'P2',
  },
  {
    ...PERSONAL_TEMPLATE,
    id: 103,
    workspaceId: 0,
    creatorId: 0,
    scope: 'SYSTEM',
    name: '接口异常',
    title: '接口调用异常',
    descriptionMd: '接口模板',
    priority: 'P1',
  },
]

/** 模板创建页交互测试。 */
describe('BugCreateView', () => {
  /** 每个测试重置 API 与确认框 mock，个人模板请求均返回同一确定数据。 */
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.listBugTemplates.mockResolvedValue([...SYSTEM_TEMPLATES, PERSONAL_TEMPLATE])
    mocks.confirm.mockResolvedValue(undefined)
  })

  /** 挂载页面并等待异步个人模板加载完成。 */
  async function mountView() {
    const wrapper = mount(BugCreateView, { global: { stubs } })
    await flushPromises()
    return wrapper
  }

  /** 验证空白、系统、个人和共享类型均通过模板类型下拉框提供。 */
  it('应按下拉框提供模板类型与具体模板选择', async () => {
    const wrapper = await mountView()

    expect(mocks.listBugTemplates).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('使用模板')
    expect(wrapper.text()).toContain('模板类型')
    expect(wrapper.text()).toContain('系统模板')
    expect(wrapper.text()).toContain('我的模板')
    expect(wrapper.text()).toContain('共享模板')
    expect(wrapper.find('[data-template-category-select]').exists()).toBe(true)
    expect(wrapper.find('[data-template-select]').exists()).toBe(false)
  })

  /** 验证确认覆盖后只回填三项基础字段，负责人和验收人必须保留原值。 */
  it('确认后应回填模板字段且不覆盖负责人和验收人', async () => {
    const wrapper = await mountView()
    const formSelects = wrapper.findAll('.bug-create__row .select-stub')
    await formSelects[1]?.setValue('11')
    await formSelects[2]?.setValue('10')
    await wrapper.get('.input-stub').setValue('手工填写标题')
    await wrapper.get('.markdown-stub').setValue('手工填写描述')

    await wrapper.get('[data-template-category-select]').setValue('PERSONAL')
    await wrapper.get('[data-template-select]').setValue('PERSONAL:42')
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
    expect(
      (wrapper.findAll('.bug-create__row .select-stub')[0]?.element as HTMLSelectElement).value,
    ).toBe('P1')
    expect(
      (wrapper.findAll('.bug-create__row .select-stub')[1]?.element as HTMLSelectElement).value,
    ).toBe('11')
    expect(
      (wrapper.findAll('.bug-create__row .select-stub')[2]?.element as HTMLSelectElement).value,
    ).toBe('10')
  })

  /** 验证系统模板走同一条回填路径，同样只写入标题、描述和优先级。 */
  it('选择系统模板应回填标题、描述和优先级', async () => {
    const systemTemplate = SYSTEM_TEMPLATES[2]!

    const wrapper = await mountView()
    const selects = wrapper.findAll('.bug-create__row .select-stub')
    await selects[1]?.setValue('11')
    await selects[2]?.setValue('10')

    // 表单为空时不触发覆盖确认，选中系统模板即应用。
    await wrapper.get('[data-template-category-select]').setValue('SYSTEM')
    await wrapper.get('[data-template-select]').setValue('SYSTEM:103')
    await flushPromises()

    expect(mocks.confirm).not.toHaveBeenCalled()
    expect((wrapper.get('.input-stub').element as HTMLInputElement).value).toBe(
      systemTemplate.title,
    )
    expect((wrapper.get('.markdown-stub').element as HTMLTextAreaElement).value).toBe(
      systemTemplate.descriptionMd,
    )
    expect((selects[0]!.element as HTMLSelectElement).value).toBe(systemTemplate.priority)
    // 负责人和验收人保持用户当前选择，不被模板改写。
    expect((selects[1]!.element as HTMLSelectElement).value).toBe('11')
    expect((selects[2]!.element as HTMLSelectElement).value).toBe('10')
  })

  /** 验证从任意模板切回空白会清除基础字段，但不能改变负责人和验收人。 */
  it('从模板切回空白应清空基础字段并保留负责人和验收人', async () => {
    const wrapper = await mountView()
    const selects = wrapper.findAll('.bug-create__row .select-stub')
    await selects[1]?.setValue('11')
    await selects[2]?.setValue('10')

    await wrapper.get('[data-template-category-select]').setValue('SYSTEM')
    await wrapper.get('[data-template-select]').setValue('SYSTEM:103')
    await flushPromises()
    await wrapper.get('[data-template-category-select]').setValue('BLANK')
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalledWith(
      '当前模板内容将被清空，是否继续？',
      '使用空白模板',
      expect.any(Object),
    )
    expect((wrapper.get('.input-stub').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('.markdown-stub').element as HTMLTextAreaElement).value).toBe('')
    expect((selects[0]!.element as HTMLSelectElement).value).toBe('P2')
    expect((selects[1]!.element as HTMLSelectElement).value).toBe('11')
    expect((selects[2]!.element as HTMLSelectElement).value).toBe('10')
  })

  /** 验证拒绝确认时保留用户手写内容，模板不会静默改写表单。 */
  it('取消覆盖确认时应保留当前标题和描述', async () => {
    mocks.confirm.mockRejectedValueOnce(new Error('cancel'))
    const wrapper = await mountView()
    await wrapper.get('.input-stub').setValue('保留标题')
    await wrapper.get('.markdown-stub').setValue('保留描述')

    await wrapper.get('[data-template-category-select]').setValue('SYSTEM')
    await wrapper.get('[data-template-select]').setValue('SYSTEM:103')
    await flushPromises()

    expect((wrapper.get('.input-stub').element as HTMLInputElement).value).toBe('保留标题')
    expect((wrapper.get('.markdown-stub').element as HTMLTextAreaElement).value).toBe('保留描述')
  })
})
