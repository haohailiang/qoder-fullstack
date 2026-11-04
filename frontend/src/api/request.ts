import axios from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  // 开发环境下经 Vite 代理转发到 http://localhost:8080
  baseURL: '/api',
  timeout: 10000,
})

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
