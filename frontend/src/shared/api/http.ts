/**
 * 本文件封装统一 HTTP 客户端，集中处理登录凭证注入与统一响应解包。
 */
import axios from 'axios'
import type { AxiosError } from 'axios'

import { ApiError } from './types'
import type { ApiResponse } from './types'

const TOKEN_STORAGE_KEY = 'bugloop.token'

export function readToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function writeToken(token: string): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15_000,
})

http.interceptors.request.use((config) => {
  const token = readToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code === 0) {
      return body.data as never
    }
    return Promise.reject(new ApiError(body.code, body.message))
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const body = error.response?.data
    if (body && typeof body.code === 'number') {
      return Promise.reject(new ApiError(body.code, body.message))
    }
    return Promise.reject(new ApiError(-1, '网络异常，请稍后重试'))
  },
)

export default http
