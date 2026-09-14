/**
 * 本文件封装统一 HTTP 客户端，为后续 Token、错误码和请求追踪处理提供单一入口。
 */
import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15_000,
})

export default http
