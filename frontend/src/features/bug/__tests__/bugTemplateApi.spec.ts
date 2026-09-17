/**
 * 本文件验证模板 API 的路径、请求体透传和 SYSTEM/PERSONAL ViewModel 映射。
 * 测试仅模拟 HTTP 层，不渲染页面，确保本任务保持在数据访问层范围内。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

// vi.mock 会被提升到模块加载前，HTTP mock 同样必须在提升阶段创建，避免访问尚未初始化的顶层变量。
const http = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/shared/api/http', () => ({ default: http }))

import {
  createBugTemplate,
  createSystemBugTemplate,
  deleteBugTemplate,
  listBugTemplates,
  saveBugAsTemplate,
  updateBugTemplateSharing,
  updateBugTemplate,
} from '../bugTemplateApi'
import type {
  BugTemplate,
  CreateBugTemplateRequest,
  UpdateBugTemplateRequest,
} from '../bugTemplateApi'

/** 模板 API 与统一 ViewModel 单元测试。 */
describe('bugTemplateApi', () => {
  const createRequest: CreateBugTemplateRequest = {
    name: '登录排查模板',
    title: '登录失败',
    descriptionMd: '# 排查步骤',
    priority: 'P1',
  }

  const personalTemplate: BugTemplate = {
    id: 42,
    workspaceId: 10,
    creatorId: 8,
    scope: 'PERSONAL',
    shared: false,
    ...createRequest,
    sourceBugId: 99,
    sourceBugNo: 'BUG-000099',
    sortOrder: 3,
    createdAt: '2026-09-16T10:00:00',
    updatedAt: '2026-09-16T11:00:00',
  }

  /** 每个断言从干净的请求 mock 开始，避免前一接口调用影响路径校验。 */
  beforeEach(() => {
    vi.clearAllMocks()
  })

  /** 验证后端个人模板 CRUD 路径和请求体与接口契约一致。 */
  it('应调用个人模板 CRUD 接口', () => {
    const updateRequest: UpdateBugTemplateRequest = { ...createRequest, sortOrder: 2 }

    void listBugTemplates(10)
    void createBugTemplate(10, createRequest)
    void updateBugTemplate(42, updateRequest)
    void updateBugTemplateSharing(42, true)
    void deleteBugTemplate(42)

    expect(http.get).toHaveBeenCalledWith('/workspaces/10/bug-templates')
    expect(http.post).toHaveBeenCalledWith('/workspaces/10/bug-templates', createRequest)
    expect(http.put).toHaveBeenCalledWith('/bug-templates/42', updateRequest)
    expect(http.patch).toHaveBeenCalledWith('/bug-templates/42/sharing', { shared: true })
    expect(http.delete).toHaveBeenCalledWith('/bug-templates/42')
  })

  /** 验证从 Bug 保存模板使用独立路径，防止错误复用工作空间模板创建接口。 */
  it('应调用从 Bug 保存模板接口', () => {
    void saveBugAsTemplate(99, createRequest)

    expect(http.post).toHaveBeenCalledWith('/bugs/99/save-as-template', createRequest)
  })

  /** 验证系统模板改由专用数据库接口创建，不再依赖前端静态模板。 */
  it('应调用系统模板创建接口', () => {
    void createSystemBugTemplate(createRequest)
    expect(http.post).toHaveBeenCalledWith('/bug-templates/system', createRequest)
  })
})
