import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, refreshToken as refreshTokenApi, getCurrentUserInfo, LoginResponse } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const refreshToken = ref<string>(localStorage.getItem('refreshToken') || '')
  const tokenExpireTime = ref<number>(parseInt(localStorage.getItem('tokenExpireTime') || '0'))
  const userRoles = ref<string[]>(JSON.parse(localStorage.getItem('userRoles') || '[]'))
  const userInfo = ref<LoginResponse | null>(null)

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const setRefreshToken = (newRefreshToken: string) => {
    refreshToken.value = newRefreshToken
    localStorage.setItem('refreshToken', newRefreshToken)
  }

  const setTokenExpireTime = (expireTime: number) => {
    tokenExpireTime.value = expireTime
    localStorage.setItem('tokenExpireTime', expireTime.toString())
  }

  const setUserRoles = (roles: string[]) => {
    userRoles.value = roles
    localStorage.setItem('userRoles', JSON.stringify(roles))
  }

  const setUserInfo = (info: LoginResponse) => {
    userInfo.value = info
  }

  const login = async (username: string, password: string) => {
    const res = await loginApi({ username, password })
    console.log('Login response:', res)
    // 假设后端返回的数据包含 token、refreshToken 和过期时间
    // 实际实现中需要根据后端返回的数据结构进行调整
    setToken(res.data.token || 'session-token')
    setRefreshToken(res.data.refreshToken || '')
    // 设置 token 过期时间，默认 2 小时
    setTokenExpireTime(Date.now() + 2 * 60 * 60 * 1000)
    // 假设后端返回的数据包含用户角色，如果没有则默认设置为 admin
    setUserRoles(res.data.roles || ['admin'])
    setUserInfo(res.data)
    console.log('Token set:', token.value)
    return res
  }

  const refreshAccessToken = async () => {
    try {
      const res = await refreshTokenApi()
      setToken(res.data.token)
      setRefreshToken(res.data.refreshToken)
      // 设置 token 过期时间，默认 2 小时
      setTokenExpireTime(Date.now() + 2 * 60 * 60 * 1000)
      return res
    } catch (error) {
      // 刷新 token 失败，退出登录
      logout()
      throw error
    }
  }

  const logout = async () => {
    try {
      await logoutApi()
    } catch {
      // ignore error
    } finally {
      token.value = ''
      refreshToken.value = ''
      tokenExpireTime.value = 0
      userRoles.value = []
      userInfo.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('tokenExpireTime')
      localStorage.removeItem('userRoles')
    }
  }

  const getUserInfo = async () => {
    try {
      const res = await getCurrentUserInfo()
      setUserInfo(res.data)
      return res.data
    } catch {
      logout()
      return null
    }
  }

  const isLoggedIn = () => {
    // 检查 token 是否存在且未过期
    const isTokenValid = !!token.value && tokenExpireTime.value > Date.now()
    return isTokenValid || !!userInfo.value
  }

  const hasRole = (role: string) => {
    return userRoles.value.includes(role)
  }

  const isTokenExpiringSoon = () => {
    // 检查 token 是否在 5 分钟内过期
    return tokenExpireTime.value - Date.now() < 5 * 60 * 1000
  }

  return {
    token,
    refreshToken,
    tokenExpireTime,
    userRoles,
    userInfo,
    setToken,
    setRefreshToken,
    setTokenExpireTime,
    setUserRoles,
    setUserInfo,
    login,
    logout,
    refreshAccessToken,
    getUserInfo,
    isLoggedIn,
    hasRole,
    isTokenExpiringSoon
  }
})