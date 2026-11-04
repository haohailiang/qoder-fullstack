import request from './request'
import type { User } from './user'

/** 登录请求参数 */
export interface LoginParams {
  username: string
  password: string
}

/** 登录响应（与后端 dto.LoginResponse 对应） */
export interface LoginResult {
  token: string
  expiresIn: number
  user: User
}

/** 注册请求参数（与后端 dto.RegisterRequest 对应） */
export interface RegisterParams {
  username: string
  password: string
  confirmPassword: string
  name: string
  email: string
  age: number
}

/** 登录 */
export function login(data: LoginParams) {
  return request.post<LoginResult>('/auth/login', data)
}

/** 注册 */
export function register(data: RegisterParams) {
  return request.post<User>('/auth/register', data)
}

/** 获取当前登录用户 */
export function getCurrentUser() {
  return request.get<User>('/auth/me')
}

/** 退出登录（JWT 无状态，后端仅返回确认，实际清理由前端完成） */
export function logout() {
  return request.post<void>('/auth/logout')
}
