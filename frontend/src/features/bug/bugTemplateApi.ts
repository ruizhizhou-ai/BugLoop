/**
 * 本文件封装数据库 Bug 模板接口与统一 ViewModel。
 * 后端已统一返回系统、个人和共享个人模板，页面只需按 scope 与创建人分组展示。
 */
import type { BugPriority } from './bugApi'
import http from '@/shared/api/http'

/** 模板来源范围；SYSTEM 为数据库全局系统模板，PERSONAL 为工作空间个人模板。 */
export type TemplateScope = 'SYSTEM' | 'PERSONAL'

/**
 * 后端 BugTemplateVO 对应的数据模型，表示当前用户在目标工作空间可使用的模板。
 */
export interface BugTemplate {
  id: number
  workspaceId: number
  creatorId: number
  scope: TemplateScope
  /** 个人模板是否已向同工作空间成员开放使用；系统模板固定为 false。 */
  shared: boolean
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
export type SaveBugAsTemplateRequest = CreateBugTemplateRequest

/**
 * 查询当前用户在指定工作空间可使用的系统、个人和共享模板。
 *
 * @param workspaceId 当前工作空间主键
 * @returns 当前用户可使用的模板列表
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
 * 创建数据库系统模板，服务端仅允许系统管理员调用。
 *
 * @param request 系统模板基础字段
 * @returns 新建系统模板
 */
export function createSystemBugTemplate(request: CreateBugTemplateRequest): Promise<BugTemplate> {
  return http.post<unknown, BugTemplate>('/bug-templates/system', request)
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
 * 切换当前用户个人模板的共享开关；共享后同工作空间成员可选择该模板。
 *
 * @param templateId 目标个人模板主键
 * @param shared 目标共享状态
 * @returns 更新后的模板
 */
export function updateBugTemplateSharing(
  templateId: number,
  shared: boolean,
): Promise<BugTemplate> {
  return http.patch<unknown, BugTemplate>(`/bug-templates/${templateId}/sharing`, { shared })
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
