/**
 * 本文件封装系统用户管理接口，仅供 SYSTEM_ADMIN 的用户管理页面调用。
 */
import http from '@/shared/api/http'

export interface SystemUser {
  id: number
  username: string
  displayName: string
  systemRole: 'SYSTEM_ADMIN' | 'USER'
  enabled: boolean
  createdAt: string
}

export interface CreateSystemUserPayload {
  username: string
  displayName: string
  password: string
}

/** 查询系统全部用户，服务端会再次校验 SYSTEM_ADMIN 权限。 */
export function fetchSystemUsers(): Promise<SystemUser[]> {
  return http.get<unknown, SystemUser[]>('/users')
}

/** 由系统管理员创建普通用户，不改变当前登录会话。 */
export function createSystemUser(payload: CreateSystemUserPayload): Promise<SystemUser> {
  return http.post<unknown, SystemUser>('/users', payload)
}

/** 停用普通用户账号；停用后该账号无法登录，在线会话由服务端立即失效。 */
export function disableSystemUser(userId: number): Promise<SystemUser> {
  return http.post<unknown, SystemUser>(`/users/${userId}/disable`)
}

/** 重新启用普通用户账号，恢复登录能力。 */
export function enableSystemUser(userId: number): Promise<SystemUser> {
  return http.post<unknown, SystemUser>(`/users/${userId}/enable`)
}
