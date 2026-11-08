import request from './request'
import type { User } from './user'

/** 登录请求参数（admin 用户登录时无需验证码字段） */
export interface LoginParams {
  username: string
  password: string
  /** 图片验证码 key（admin 登录时可为空） */
  captchaKey: string
  /** 图片验证码值（admin 登录时可为空） */
  captchaValue: string
  /** 短信验证码（admin 登录时可为空） */
  smsCode: string
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
  /** 短信验证码（图片验证码通过后由 /auth/send-sms-code 发送到 phone） */
  smsCode: string
}

/** 发送短信验证码请求参数（与后端 dto.SendSmsCodeRequest 对应） */
export interface SendSmsCodeParams {
  captchaKey: string
  captchaValue: string
  /** 注册场景：前端直接传入的目标手机号 */
  phone?: string
  /** 登录场景：已注册用户名，由后端查询其手机号 */
  username?: string
}

/** 图片验证码响应（与后端 dto.CaptchaResponse 对应） */
export interface CaptchaResult {
  captchaKey: string
  /** Base64 PNG Data URL（data:image/png;base64,...），可直接用于 img / el-image 展示 */
  captchaImage: string
}

/** 获取图片验证码 */
export function getCaptcha() {
  return request.get<CaptchaResult>('/user/auth/captcha')
}

/**
 * 发送短信验证码（登录 / 注册页，匿名接口）：携带图片验证码 key/value + 手机号（注册）或用户名（登录）。
 * 后端先校验图片验证码，通过后才真正发送（同一手机号 60 秒频控）；验证码错误则不发送并返回明确提示。
 */
export function sendSmsCode(data: SendSmsCodeParams) {
  return request.post<void>('/user/auth/send-sms-code', data)
}

/** 登录 */
export function login(data: LoginParams) {
  return request.post<LoginResult>('/user/auth/login', data)
}

/** 注册 */
export function register(data: RegisterParams) {
  return request.post<User>('/user/auth/register', data)
}

/** 获取当前登录用户 */
export function getCurrentUser() {
  return request.get<User>('/user/auth/me')
}

/** 退出登录（JWT 无状态，后端仅返回确认，实际清理由前端完成） */
export function logout() {
  return request.post<void>('/user/auth/logout')
}
