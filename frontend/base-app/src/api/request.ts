import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { clearAuth, getToken } from '@/utils/auth'
import i18n from '@/i18n'

/** 后端统一响应结构 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  // 开发环境下经 Vite 代理转发到后端（端口与 context-path 见 vite.config.ts / application.yml）
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截器：自动携带 Authorization: Bearer <token>
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** Token 失效（HTTP 401/403）时清除本地令牌并跳转登录页 */
function handleAuthFailure(message: string) {
  clearAuth()
  ElMessage.error(message)
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.push({ path: '/login', query: { redirect: current.fullPath } })
  }
}

/**
 * 被挤下线处理：清除本地状态并强制跳转登录页。
 * 不保留 redirect 参数，避免用户回到原页面后再次被踢出造成死循环。
 */
function handleKickOut(message: string) {
  clearAuth()
  ElMessage.error(message)
  // 使用 replace 而非 push，避免用户点击浏览器后退按钮回到已失效的页面
  router.replace('/login')
}

// 响应拦截器：统一解包 Result，业务失败时弹出错误提示
request.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult
    if (result.code !== 200) {
      ElMessage.error(result.message || i18n.global.t('common.requestFailed'))
      return Promise.reject(new Error(result.message || i18n.global.t('common.requestFailed')))
    }
    return result.data as unknown as typeof response
  },
  (error) => {
    const status = error.response?.status as number | undefined
    if (status === 401 || status === 403) {
      // 后端 Security 过滤链返回的统一 Result 结构
      const result = error.response?.data as ApiResult | undefined
      const code = result?.code
      const message = result?.message
      
      // 业务码 40101 表示账号在其他设备登录，已被迫下线
      if (code === 40101) {
        handleKickOut(message || i18n.global.t('common.kickedOut'))
        return Promise.reject(error)
      }
      
      // 普通的 Token 无效/过期/未授权
      handleAuthFailure(message || (status === 401 ? i18n.global.t('common.loginExpired') : i18n.global.t('common.unauthorized')))
      return Promise.reject(error)
    }
    ElMessage.error(error.message || i18n.global.t('common.networkError'))
    return Promise.reject(error)
  },
)

// 类型声明：让 axios 方法直接返回解包后的 data
// （使用 import('axios') 内联引用，避免顶部导入被判定为未使用）
declare module 'axios' {
  export interface AxiosInstance {
    request<T = unknown>(config: import('axios').AxiosRequestConfig): Promise<T>
    get<T = unknown>(url: string, config?: import('axios').AxiosRequestConfig): Promise<T>
    delete<T = unknown>(url: string, config?: import('axios').AxiosRequestConfig): Promise<T>
    head<T = unknown>(url: string, config?: import('axios').AxiosRequestConfig): Promise<T>
    post<T = unknown>(
      url: string,
      data?: unknown,
      config?: import('axios').AxiosRequestConfig,
    ): Promise<T>
    put<T = unknown>(
      url: string,
      data?: unknown,
      config?: import('axios').AxiosRequestConfig,
    ): Promise<T>
    patch<T = unknown>(
      url: string,
      data?: unknown,
      config?: import('axios').AxiosRequestConfig,
    ): Promise<T>
  }
}

export default request
