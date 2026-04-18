import request from '@/utils/request'

export interface UserQueryRequest {
  username?: string
  phone?: string
  status?: number
  page?: number
  size?: number
}

export interface UserRequest {
  id?: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  password?: string
  gender?: number
  userType?: number
  status?: number
}

export interface UserResponse {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  gender: number
  userType: number
  status: number
  createTime: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  pages: number
  current: number
  size: number
}

export function getUserPage(data: UserQueryRequest) {
  return request({
    url: '/system/user/page',
    method: 'post',
    data
  })
}

export function addUser(data: UserRequest) {
  return request({
    url: '/system/user/add',
    method: 'post',
    data
  })
}

export function updateUser(data: UserRequest) {
  return request({
    url: '/system/user/update',
    method: 'post',
    data
  })
}

export function updateUserStatus(id: number, status: number) {
  return request({
    url: '/system/user/status',
    method: 'post',
    params: { id, status }
  })
}

export function resetUserPassword(id: number) {
  return request({
    url: '/system/user/resetPassword',
    method: 'post',
    params: { id }
  })
}

export function deleteUser(id: number) {
  return request({
    url: '/system/user/delete',
    method: 'post',
    params: { id }
  })
}