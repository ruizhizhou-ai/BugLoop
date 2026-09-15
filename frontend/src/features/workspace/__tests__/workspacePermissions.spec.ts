/**
 * 本文件验证新建工作空间入口的前端权限提示，防止普通成员或无空间用户看到越权操作。
 */
import { describe, expect, it } from 'vitest'

import type { Workspace } from '../workspaceApi'
import { canCreateWorkspace } from '../workspacePermissions'

const workspace: Workspace = {
  id: 1,
  name: '研发中心',
  description: null,
  ownerId: 10,
  status: 'ENABLED',
  currentUserRole: 'MEMBER',
  createdAt: '2026-09-16T09:00:00',
  updatedAt: '2026-09-16T09:00:00',
}

describe('canCreateWorkspace', () => {
  it('系统管理员无需空间成员身份也可创建', () => {
    expect(canCreateWorkspace('SYSTEM_ADMIN', [])).toBe(true)
  })

  it('空间负责人和管理员可创建新空间', () => {
    expect(canCreateWorkspace('USER', [{ ...workspace, currentUserRole: 'OWNER' }])).toBe(true)
    expect(canCreateWorkspace('USER', [{ ...workspace, currentUserRole: 'ADMIN' }])).toBe(true)
  })

  it('普通成员和无空间用户都不能创建', () => {
    expect(canCreateWorkspace('USER', [workspace])).toBe(false)
    expect(canCreateWorkspace('USER', [])).toBe(false)
  })
})
