import request from '@/utils/request'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  confirmPassword: string
  phone: string
  email: string
  agree: boolean
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
    url: '/auth/login',
    method: 'post',
    data
  })
}

export function refreshToken() {
  return request({
    url: '/auth/refresh',
    method: 'post'
  })
}

export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

export function getCurrentUserInfo() {
  return request({
    url: '/auth/current',
    method: 'post'
  })
}

export function register(data: RegisterRequest) {
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}