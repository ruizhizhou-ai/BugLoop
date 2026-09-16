/**
 * 本文件验证系统用户管理页会加载账号列表，并向系统管理员提供创建用户入口。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'

import * as userApi from '../userApi'
import SystemUsersView from '../SystemUsersView.vue'

vi.mock('../userApi', () => ({
  fetchSystemUsers: vi.fn(),
  createSystemUser: vi.fn(),
}))

describe('SystemUsersView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(userApi.fetchSystemUsers).mockResolvedValue([
      {
        id: 1001,
        username: 'admin',
        displayName: '系统管理员',
        systemRole: 'SYSTEM_ADMIN',
        enabled: true,
        createdAt: '2026-09-16T09:00:00',
      },
    ])
  })

  it('进入页面后加载用户，并展示创建入口', async () => {
    const wrapper = shallowMount(SystemUsersView)
    await flushPromises()

    expect(userApi.fetchSystemUsers).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('创建用户')
    expect(wrapper.text()).toContain('共 1 个账号')
  })
})
