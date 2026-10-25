import axios, { AxiosError } from 'axios'
import type { ApiErrorBody } from '../types/auth'

/**
 * 统一的 HTTP 错误对象：页面只需要 `error.message` 就能提示用户，
 * 表单页还能用 `error.fields` 定位到具体输入框。
 */
export class ApiError extends Error {
  readonly code: string
  readonly status?: number
  readonly fields: Record<string, string>

  constructor(message: string, code = 'UNKNOWN', status?: number, fields: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.fields = fields
  }

  /** 字段级错误里取第一条，用于在提示条上显示 */
  get firstFieldMessage(): string {
    const values = Object.values(this.fields)
    return values.length > 0 ? values[0] : this.message
  }
}

/**
 * 全局唯一的 Axios 实例：baseURL 固定 /api，由 Vite 代理转发到 Spring Boot，
 * 组件里不要写死 http://localhost:8080。
 *
 * withCredentials 打开后浏览器才会保存并携带 JSESSIONID（登录态就存在这张 Cookie 里）。
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

/** 登录态失效广播：composable 监听后清空当前用户，路由守卫据此跳转登录页 */
export const AUTH_UNAUTHORIZED_EVENT = 'auth:unauthorized'

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status
    const body = error.response?.data

    // 只有「已登录但会话过期」才需要广播；登录接口本身失败也是 401，但那时本来就没有登录态
    if (status === 401) {
      window.dispatchEvent(new Event(AUTH_UNAUTHORIZED_EVENT))
    }

    if (body && typeof body === 'object' && typeof body.message === 'string') {
      return Promise.reject(
        new ApiError(body.message, body.code ?? 'HTTP_ERROR', status, body.fields ?? {}),
      )
    }
    if (status) {
      return Promise.reject(new ApiError(`请求失败（HTTP ${status}）`, 'HTTP_ERROR', status))
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new ApiError('请求超时，请确认后端服务已启动', 'TIMEOUT'))
    }
    return Promise.reject(new ApiError('网络异常，请确认后端服务（8080）是否正在运行', 'NETWORK_ERROR'))
  },
)
