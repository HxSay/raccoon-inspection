import request from '@/utils/request'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  gender: number
  userType: number
  status: number
}

export function login(data: LoginRequest) {
  return request({
    url: '/system/auth/login',
    method: 'post',
    data
  })
}

export function refreshToken() {
  return request({
    url: '/system/auth/refresh',
    method: 'post'
  })
}

export function logout() {
  return request({
    url: '/system/auth/logout',
    method: 'post'
  })
}

export function getCurrentUserInfo() {
  return request({
    url: '/system/auth/current',
    method: 'post'
  })
}