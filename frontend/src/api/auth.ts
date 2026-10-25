import { http } from './http'
import type { AuthUser, LoginPayload, RegisterPayload } from '../types/auth'

/** 注册：只创建账号，不建立登录态（注册后由前端自动调用一次 login） */
export async function register(payload: RegisterPayload): Promise<AuthUser> {
  const { data } = await http.post<AuthUser>('/auth/register', payload)
  return data
}

/** 登录成功后后端写入 JSESSIONID，浏览器会自动保存 */
export async function login(payload: LoginPayload): Promise<AuthUser> {
  const { data } = await http.post<AuthUser>('/auth/login', payload)
  return data
}

/** 退出登录：让服务端 Session 失效（204 无响应体） */
export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}

/** 刷新页面后用它恢复登录态；未登录时后端返回 401 */
export async function fetchCurrentUser(): Promise<AuthUser> {
  const { data } = await http.get<AuthUser>('/auth/me')
  return data
}
