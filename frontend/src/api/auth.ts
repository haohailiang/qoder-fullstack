import request from './request'
import type { User } from './user'

/** 登录请求参数 */
export interface LoginParams {
  username: string
  password: string
  captchaKey: string
  captchaValue: string
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
  phone: string
  age: number
  captchaKey: string
  captchaValue: string
}

/** 图片验证码响应（与后端 dto.CaptchaResponse 对应） */
export interface CaptchaResult {
  captchaKey: string
  /** Base64 PNG Data URL（data:image/png;base64,...），可直接用于 img / el-image 展示 */
  captchaImage: string
}

/** 获取图片验证码 */
export function getCaptcha() {
  return request.get<CaptchaResult>('/auth/captcha')
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
