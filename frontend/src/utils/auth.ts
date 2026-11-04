import { ref } from 'vue'

/**
 * 令牌与用户状态管理。
 * 选择 localStorage 而非 sessionStorage 的原因：
 * - 刷新页面、关闭标签页后重开仍保持登录态，演示体验更连贯
 * - sessionStorage 每个标签页独立，新开标签页会要求重新登录
 * 代价是 Token 长期驻留，因此仅用于开发演示，生产环境可结合后端 Token 有效期控制。
 */
const TOKEN_KEY = 'demo_token'
const USER_KEY = 'demo_user'

/** 响应式 Token，供路由守卫与 App 头部实时读取 */
export const tokenRef = ref<string | null>(localStorage.getItem(TOKEN_KEY))

/** 响应式当前登录用户信息 */
export const currentUserRef = ref<AuthUserInfo | null>(readStoredUser())

export interface AuthUserInfo {
  id?: number
  username?: string
  name: string
  email: string
  age: number
}

function readStoredUser(): AuthUserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUserInfo
  } catch {
    return null
  }
}

export function getToken(): string | null {
  return tokenRef.value
}

export function setAuth(token: string, user: AuthUserInfo) {
  tokenRef.value = token
  currentUserRef.value = user
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  tokenRef.value = null
  currentUserRef.value = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
