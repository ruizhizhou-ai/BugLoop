/**
 * 本文件封装认证接口调用，供认证 Store 使用。
 */
import http from '@/shared/api/http'

export interface AuthUser {
  id: number
  username: string
  displayName: string
  systemRole: 'SYSTEM_ADMIN' | 'USER'
  createdAt: string
}

export interface LoginResult {
  token: string
  user: AuthUser
}

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  displayName: string
  password: string
}

export function login(payload: LoginPayload): Promise<LoginResult> {
  return http.post<unknown, LoginResult>('/auth/login', payload)
}

export function register(payload: RegisterPayload): Promise<LoginResult> {
  return http.post<unknown, LoginResult>('/auth/register', payload)
}

export function logout(): Promise<void> {
  return http.post<unknown, void>('/auth/logout')
}

export function fetchCurrentUser(): Promise<AuthUser> {
  return http.get<unknown, AuthUser>('/users/me')
}
