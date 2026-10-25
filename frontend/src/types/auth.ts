/** 后端 users.role 的取值，必须与 backend 的 UserRole 枚举保持一致 */
export type UserRole = 'USER' | 'EDITOR' | 'ADMIN'

/** 认证接口返回的用户档案（后端不会返回 passwordHash） */
export interface AuthUser {
  id: number
  username: string
  email: string
  role: UserRole
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
  confirmPassword: string
}

/** 登录时 identifier 可以是用户名，也可以是邮箱 */
export interface LoginPayload {
  identifier: string
  password: string
}

/** 后端统一的错误响应体，见 backend 的 ApiErrorResponse */
export interface ApiErrorBody {
  code?: string
  message?: string
  fields?: Record<string, string>
}
