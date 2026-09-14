/**
 * 本文件定义所有接口共用的响应结构与错误类型，与后端 ApiResponse 保持一致。
 */

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 后端统一分页结构，与 PageResponse 保持一致。 */
export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

/** 携带业务错误码的接口异常，页面据此展示后端返回的中文提示。 */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
