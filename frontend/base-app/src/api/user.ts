import request from './request'

/** 用户实体（与后端 entity.User 对应；password 由后端 @JsonIgnore 永远不会返回） */
export interface User {
  id?: number
  /** 登录认证标识，仅注册/登录后有值，旧 CRUD 数据可能为空 */
  username?: string
  /** 角色：ADMIN（管理员）/ USER（普通用户），用户管理接口仅管理员可调 */
  role?: string
  name: string
  email: string
  /** 手机号，注册、用户新增/编辑均必填，用于接收短信验证码 */
  phone: string
  age: number
  /** 主题偏好：dark（暗黑模式）或 light（亮色模式），登录后由后端返回 */
  themePreference?: string
  /** 语言偏好：zh（中文）或 en（英文），登录后由后端返回 */
  languagePreference?: string
}

/** 查询用户列表 */
export function listUsers() {
  return request.get<User[]>('/user/users')
}

/** 根据 id 查询用户 */
export function getUser(id: number) {
  return request.get<User>(`/user/users/${id}`)
}

/** 新增用户 */
export function createUser(data: Omit<User, 'id'>) {
  return request.post<User>('/user/users', data)
}

/** 更新用户 */
export function updateUser(id: number, data: Omit<User, 'id'>) {
  return request.put<User>(`/user/users/${id}`, data)
}

/** 删除用户 */
export function deleteUser(id: number) {
  return request.delete<void>(`/user/users/${id}`)
}
