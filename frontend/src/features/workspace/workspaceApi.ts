/**
 * 本文件封装工作空间、成员和角色接口，是 Workspace Store 与后端通信的唯一入口。
 */
import http from '@/shared/api/http'

export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER'
export type WorkspaceStatus = 'ENABLED' | 'DISABLED'

export interface Workspace {
  id: number
  name: string
  description: string | null
  ownerId: number
  status: WorkspaceStatus
  currentUserRole: WorkspaceRole | null
  createdAt: string
  updatedAt: string
}

export interface WorkspaceMember {
  userId: number
  username: string
  displayName: string
  role: WorkspaceRole
  enabled: boolean
  joinedAt: string
}

/** 添加成员时可选择的启用系统用户，不包含敏感账号信息。 */
export interface AvailableWorkspaceUser {
  id: number
  username: string
  displayName: string
}

export interface WorkspacePayload {
  name: string
  description: string | null
}

export interface AddWorkspaceMemberPayload {
  userId: number
  role: WorkspaceRole
}

/** 创建工作空间并返回创建者为 OWNER 的空间详情。 */
export function createWorkspace(payload: WorkspacePayload): Promise<Workspace> {
  return http.post<unknown, Workspace>('/workspaces', payload)
}

/** 查询当前用户加入的工作空间，作为顶部切换器的数据源。 */
export function fetchMyWorkspaces(): Promise<Workspace[]> {
  return http.get<unknown, Workspace[]>('/workspaces')
}

/** 校验访问权限并读取目标工作空间详情。 */
export function fetchWorkspace(workspaceId: number): Promise<Workspace> {
  return http.get<unknown, Workspace>(`/workspaces/${workspaceId}`)
}

/** 修改工作空间名称和描述。 */
export function updateWorkspace(workspaceId: number, payload: WorkspacePayload): Promise<Workspace> {
  return http.put<unknown, Workspace>(`/workspaces/${workspaceId}`, payload)
}

/** 查询目标工作空间成员。 */
export function fetchWorkspaceMembers(workspaceId: number): Promise<WorkspaceMember[]> {
  return http.get<unknown, WorkspaceMember[]>(`/workspaces/${workspaceId}/members`)
}

/** 搜索尚未加入指定工作空间的启用系统用户，供成员添加弹窗选择。 */
export function searchAvailableWorkspaceUsers(
  workspaceId: number,
  keyword: string,
): Promise<AvailableWorkspaceUser[]> {
  return http.get<unknown, AvailableWorkspaceUser[]>(`/workspaces/${workspaceId}/available-users`, {
    params: { keyword },
  })
}

/** 添加已存在的系统用户。 */
export function addWorkspaceMember(
  workspaceId: number,
  payload: AddWorkspaceMemberPayload,
): Promise<WorkspaceMember> {
  return http.post<unknown, WorkspaceMember>(`/workspaces/${workspaceId}/members`, payload)
}

/** 修改指定成员的工作空间角色。 */
export function updateWorkspaceMemberRole(
  workspaceId: number,
  userId: number,
  role: WorkspaceRole,
): Promise<WorkspaceMember> {
  return http.put<unknown, WorkspaceMember>(`/workspaces/${workspaceId}/members/${userId}/role`, {
    role,
  })
}

/** 移除工作空间成员。 */
export function removeWorkspaceMember(workspaceId: number, userId: number): Promise<void> {
  return http.delete<unknown, void>(`/workspaces/${workspaceId}/members/${userId}`)
}

/** 停用工作空间并保留历史数据。 */
export function disableWorkspace(workspaceId: number): Promise<Workspace> {
  return http.post<unknown, Workspace>(`/workspaces/${workspaceId}/disable`)
}

/** 重新启用工作空间并恢复业务写入能力。 */
export function enableWorkspace(workspaceId: number): Promise<Workspace> {
  return http.post<unknown, Workspace>(`/workspaces/${workspaceId}/enable`)
}
