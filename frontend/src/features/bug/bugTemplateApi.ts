/**
 * 本文件封装 Bug 个人模板接口与统一模板 ViewModel。
 * 后端返回的个人模板和现有 bugTemplates.ts 中的内置模板通过 scope 归一，页面层只需按 SYSTEM、PERSONAL 分组展示。
 */
import type { BugPriority } from './bugApi'
import http from '@/shared/api/http'

/** 模板来源范围；SYSTEM 对应前端内置模板，PERSONAL 对应当前用户的后端个人模板。 */
export type TemplateScope = 'SYSTEM' | 'PERSONAL'

/**
 * 后端 BugTemplateVO 对应的数据模型，仅表示当前用户可访问的个人模板。
 */
export interface BugTemplate {
  id: number
  workspaceId: number
  creatorId: number
  name: string
  title: string
  descriptionMd: string
  priority: BugPriority
  sourceBugId: number | null
  /** 来源 Bug 的业务编号，手工创建或来源缺失时为空。 */
  sourceBugNo: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

/** 手工创建个人模板时允许提交的字段；归属工作空间与创建人由后端上下文确定。 */
export interface CreateBugTemplateRequest {
  name: string
  title: string
  descriptionMd: string
  priority: BugPriority
}

/** 编辑个人模板时允许提交的字段，归属和来源 Bug 均不在请求体中。 */
export interface UpdateBugTemplateRequest extends CreateBugTemplateRequest {
  sortOrder: number
}

/** 从 Bug 保存为模板时允许提交的字段，来源 Bug 主键由接口路径确定。 */
export interface SaveBugAsTemplateRequest extends CreateBugTemplateRequest {}

/**
 * 内置模板静态配置应满足的最小结构。此类型只描述 bugTemplates.ts 的对接契约，不维护具体内置模板内容。
 */
export interface SystemBugTemplateSource {
  id: string
  name: string
  title: string
  descriptionMd: string
  priority: BugPriority
  sortOrder?: number
}

/**
 * 页面统一使用的模板模型，个人模板保留其归属元数据，内置模板则以 null 表示无后端持久化归属。
 */
export interface BugTemplateViewModel {
  id: string | number
  scope: TemplateScope
  name: string
  title: string
  descriptionMd: string
  priority: BugPriority
  workspaceId: number | null
  creatorId: number | null
  sourceBugId: number | null
  sourceBugNo: string | null
  sortOrder: number
  createdAt: string | null
  updatedAt: string | null
}

/**
 * 将后端个人模板转换为统一 ViewModel，供页面与 SYSTEM 模板合并后分组展示。
 *
 * @param template 后端返回的个人模板
 * @returns scope 为 PERSONAL 的统一模板模型
 */
export function toPersonalBugTemplateViewModel(template: BugTemplate): BugTemplateViewModel {
  return {
    ...template,
    scope: 'PERSONAL',
  }
}

/**
 * 将 bugTemplates.ts 提供的内置模板转换为统一 ViewModel，不向静态模板补造后端归属信息。
 *
 * @param template 内置模板静态配置项
 * @returns scope 为 SYSTEM 的统一模板模型
 */
export function toSystemBugTemplateViewModel(
  template: SystemBugTemplateSource,
): BugTemplateViewModel {
  return {
    ...template,
    scope: 'SYSTEM',
    workspaceId: null,
    creatorId: null,
    sourceBugId: null,
    sourceBugNo: null,
    sortOrder: template.sortOrder ?? 0,
    createdAt: null,
    updatedAt: null,
  }
}

/**
 * 查询当前用户在指定工作空间中的个人模板，不返回内置模板；页面层负责与静态来源合并。
 *
 * @param workspaceId 当前工作空间主键
 * @returns 当前用户的个人模板列表
 */
export function listBugTemplates(workspaceId: number): Promise<BugTemplate[]> {
  return http.get<unknown, BugTemplate[]>(`/workspaces/${workspaceId}/bug-templates`)
}

/**
 * 在指定工作空间创建当前用户的个人模板。
 *
 * @param workspaceId 当前工作空间主键
 * @param request 模板基础字段
 * @returns 新建个人模板
 */
export function createBugTemplate(
  workspaceId: number,
  request: CreateBugTemplateRequest,
): Promise<BugTemplate> {
  return http.post<unknown, BugTemplate>(`/workspaces/${workspaceId}/bug-templates`, request)
}

/**
 * 修改当前用户拥有的个人模板。
 *
 * @param templateId 个人模板主键
 * @param request 允许更新的模板字段
 * @returns 更新后的个人模板
 */
export function updateBugTemplate(
  templateId: number,
  request: UpdateBugTemplateRequest,
): Promise<BugTemplate> {
  return http.put<unknown, BugTemplate>(`/bug-templates/${templateId}`, request)
}

/**
 * 逻辑删除当前用户拥有的个人模板。
 *
 * @param templateId 个人模板主键
 * @returns 删除完成后的空响应
 */
export function deleteBugTemplate(templateId: number): Promise<void> {
  return http.delete<unknown, void>(`/bug-templates/${templateId}`)
}

/**
 * 将有权限访问的 Bug 保存为当前用户的个人模板，后端负责来源 Bug 的权限与字段白名单校验。
 *
 * @param bugId 来源 Bug 主键
 * @param request 模板名称和保存后的基础字段
 * @returns 新建个人模板
 */
export function saveBugAsTemplate(
  bugId: number,
  request: SaveBugAsTemplateRequest,
): Promise<BugTemplate> {
  return http.post<unknown, BugTemplate>(`/bugs/${bugId}/save-as-template`, request)
}
