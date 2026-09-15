/**
 * 本文件封装统一 HTTP 客户端，集中处理登录凭证注入与统一响应解包。
 */
import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'

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

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/** 统一注入登录凭证，普通 JSON 请求和二进制下载共用。 */
function injectAuthHeader(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const token = readToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
})

http.interceptors.request.use(injectAuthHeader)

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

const binaryHttp = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60_000,
  responseType: 'blob',
})

binaryHttp.interceptors.request.use(injectAuthHeader)

/** 下载接口返回二进制流，不能经过统一响应解包，这里单独发起请求并解析响应头文件名。 */
export async function downloadFile(path: string): Promise<{ blob: Blob; fileName: string | null }> {
  try {
    const response = await binaryHttp.get<Blob>(path)
    return {
      blob: response.data,
      fileName: parseDownloadFileName(response.headers['content-disposition']),
    }
  } catch (error) {
    throw await toDownloadError(error)
  }
}

/** 失败响应体仍是统一 JSON 结构，尝试还原业务错误码和中文提示。 */
async function toDownloadError(error: unknown): Promise<ApiError> {
  const body = (error as AxiosError).response?.data
  if (body instanceof Blob) {
    try {
      const parsed = JSON.parse(await body.text()) as ApiResponse<unknown>
      if (typeof parsed.code === 'number') {
        return new ApiError(parsed.code, parsed.message)
      }
    } catch {
      // 非 JSON 响应体统一回退到通用下载失败提示
    }
  }
  return new ApiError(-1, '附件下载失败，请稍后重试')
}

/** 优先使用 RFC 5987 的 filename*，退回普通 filename；两者都缺失时返回 null。 */
function parseDownloadFileName(disposition: unknown): string | null {
  if (typeof disposition !== 'string') {
    return null
  }
  const encodedValue = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  if (encodedValue) {
    try {
      return decodeURIComponent(encodedValue)
    } catch {
      return null
    }
  }
  return /filename="?([^";]+)"?/i.exec(disposition)?.[1] ?? null
}

export default http
