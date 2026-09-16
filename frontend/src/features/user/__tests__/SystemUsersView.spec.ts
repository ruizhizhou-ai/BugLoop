/**
 * 本文件验证系统用户管理页会加载账号列表，并向系统管理员提供创建与启停入口。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, type VNode } from 'vue'

import { mount } from '@vue/test-utils'

import * as userApi from '../userApi'
import SystemUsersView from '../SystemUsersView.vue'
import { ApiError } from '@/shared/api/types'

vi.mock('../userApi', () => ({
  fetchSystemUsers: vi.fn(),
  createSystemUser: vi.fn(),
  disableSystemUser: vi.fn(),
  enableSystemUser: vi.fn(),
}))

const ADMIN_USER = {
  id: 1001,
  username: 'admin',
  displayName: '系统管理员',
  systemRole: 'SYSTEM_ADMIN' as const,
  enabled: true,
  createdAt: '2026-09-16T09:00:00',
}
const ORDINARY_USER = {
  id: 1002,
  username: 'zhangsan',
  displayName: '张三',
  systemRole: 'USER' as const,
  enabled: true,
  createdAt: '2026-09-16T09:10:00',
}

// 表格使用真实组件，行内容才能被断言；其余组件用递归桩保证插槽内容可渲染。
const RecursiveStub = defineComponent({
  name: 'RecursiveStub',
  setup(_props, { slots }) {
    const renderSlots = (): VNode[] =>
      Object.keys(slots).flatMap((name) => (slots[name]?.() ?? []) as VNode[])
    return () => h('div', renderSlots())
  },
})

const stubs = {
  ElButton: RecursiveStub,
  ElDialog: RecursiveStub,
  ElForm: RecursiveStub,
  ElFormItem: RecursiveStub,
  ElInput: RecursiveStub,
  ElTag: RecursiveStub,
  ElPopconfirm: {
    emits: ['confirm'],
    template:
      '<span class="popconfirm-stub"><button type="button" class="popconfirm-stub__confirm" @click="$emit(\'confirm\')">确认</button><slot name="reference" /></span>',
  },
}

function mountPage() {
  return mount(SystemUsersView, { global: { stubs } })
}

describe('SystemUsersView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(userApi.fetchSystemUsers).mockResolvedValue([ADMIN_USER, ORDINARY_USER])
  })

  it('进入页面后加载用户，并展示创建入口', async () => {
    const wrapper = mountPage()
    await vi.waitFor(() => expect(wrapper.text()).toContain('共 2 个账号'))

    expect(userApi.fetchSystemUsers).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('创建用户')
  })

  it('普通用户行提供停用操作，系统管理员行不展示停用入口', async () => {
    const wrapper = mountPage()
    await vi.waitFor(() => expect(wrapper.text()).toContain('张三'))

    const rows = wrapper.findAll('.el-table__body-wrapper tbody tr')
    const adminRow = rows.find((row) => row.text().includes('系统管理员'))!
    const ordinaryRow = rows.find((row) => row.text().includes('张三'))!

    expect(adminRow.text()).not.toContain('停用')
    expect(ordinaryRow.text()).toContain('停用')
  })

  it('停用普通用户后就地更新状态并给出提示', async () => {
    vi.mocked(userApi.disableSystemUser).mockResolvedValue({ ...ORDINARY_USER, enabled: false })
    const wrapper = mountPage()
    await vi.waitFor(() => expect(wrapper.text()).toContain('张三'))

    const ordinaryRow = wrapper
      .findAll('.el-table__body-wrapper tbody tr')
      .find((row) => row.text().includes('张三'))!
    await ordinaryRow.get('.popconfirm-stub__confirm').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('已停用，将无法登录'))

    expect(userApi.disableSystemUser).toHaveBeenCalledWith(ORDINARY_USER.id)
    expect(ordinaryRow.text()).toContain('已停用')
    // 停用后该行切换为「启用」入口。
    expect(ordinaryRow.text()).toContain('启用')
  })

  it('停用失败时展示后端返回的中文提示', async () => {
    vi.mocked(userApi.disableSystemUser).mockRejectedValue(
      new ApiError(40301, '只能停用或启用普通用户账号'),
    )
    const wrapper = mountPage()
    await vi.waitFor(() => expect(wrapper.text()).toContain('张三'))

    const ordinaryRow = wrapper
      .findAll('.el-table__body-wrapper tbody tr')
      .find((row) => row.text().includes('张三'))!
    await ordinaryRow.get('.popconfirm-stub__confirm').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('只能停用或启用普通用户账号'))

    expect(ordinaryRow.text()).toContain('已启用')
  })
})
