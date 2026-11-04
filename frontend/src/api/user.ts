import request from './request'

/** 用户实体（与后端 entity.User 对应；password 由后端 @JsonIgnore 永远不会返回） */
export interface User {
  id?: number
  /** 登录认证标识，仅注册/登录后有值，旧 CRUD 数据可能为空 */
  username?: string
  name: string
  email: string
  age: number
}

/** 查询用户列表 */
export function listUsers() {
  return request.get<User[]>('/users')
}

/** 根据 id 查询用户 */
export function getUser(id: number) {
  return request.get<User>(`/users/${id}`)
}

/** 新增用户 */
export function createUser(data: Omit<User, 'id'>) {
  return request.post<User>('/users', data)
}

/** 更新用户 */
export function updateUser(id: number, data: Omit<User, 'id'>) {
  return request.put<User>(`/users/${id}`, data)
}

/** 删除用户 */
export function deleteUser(id: number) {
  return request.delete<void>(`/users/${id}`)
}
