import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { clearAuth, getToken } from '@/utils/auth'

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

// 响应拦截器：统一解包 Result，业务失败时弹出错误提示
request.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult
    if (result.code !== 200) {
      ElMessage.error(result.message || '请求失败')
      return Promise.reject(new Error(result.message || '请求失败'))
    }
    return result.data as unknown as typeof response
  },
  (error) => {
    const status = error.response?.status as number | undefined
    if (status === 401 || status === 403) {
      // 后端 Security 过滤链返回的统一 Result 结构
      const message = (error.response?.data as ApiResult | undefined)?.message
      handleAuthFailure(message || (status === 401 ? '登录已失效，请重新登录' : '未授权访问，请先登录'))
      return Promise.reject(error)
    }
    ElMessage.error(error.message || '网络错误，请稍后重试')
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
