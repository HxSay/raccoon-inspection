import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, refreshToken as refreshTokenApi, getCurrentUserInfo, LoginResponse } from '@/api/auth'

const USER_PROFILE_KEY = 'userProfile'

function readJson<T>(key: string, fallback: T): T {
  try {
    const s = localStorage.getItem(key)
    if (s == null || s === '') return fallback
    return JSON.parse(s) as T
  } catch {
    return fallback
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const refreshToken = ref<string>(localStorage.getItem('refreshToken') || '')
  const tokenExpireTime = ref<number>(parseInt(localStorage.getItem('tokenExpireTime') || '0'))
  const storedProfile = readJson<LoginResponse | null>(USER_PROFILE_KEY, null)
  const userInfo = ref<LoginResponse | null>(storedProfile)

  const parsedStoredRoles = readJson<string[]>('userRoles', [])
  let initialRoles: string[] = parsedStoredRoles.length > 0 ? parsedStoredRoles : []
  if (initialRoles.length === 0 && storedProfile) {
    if (storedProfile.userType === 2 || storedProfile.username?.toLowerCase() === 'root') {
      initialRoles = ['admin']
    }
  }
  const userRoles = ref<string[]>(initialRoles)
  if (parsedStoredRoles.length === 0 && initialRoles.length > 0) {
    localStorage.setItem('userRoles', JSON.stringify(initialRoles))
  }

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
    localStorage.setItem(USER_PROFILE_KEY, JSON.stringify(info))
  }

  const normalizeLoginRoles = (payload: Record<string, unknown>, profile: LoginResponse) => {
    const raw = payload.roles
    let roles: string[] = []
    if (Array.isArray(raw)) {
      roles = raw.filter((x): x is string => typeof x === 'string' && x.length > 0)
    }
    // 注意：[] 在 JS 中为 truthy，不能用 `roles || ['admin']`，否则会把空数组当成“有角色”
    if (roles.length === 0 && (profile.userType === 2 || profile.username?.toLowerCase() === 'root')) {
      roles = ['admin']
    }
    return roles
  }

  const login = async (username: string, password: string) => {
    const res = await loginApi({ username, password })
    const payload = res.data as Record<string, unknown>
    const profile = (payload.user ?? payload) as LoginResponse
    setToken((payload.token as string) || 'session-token')
    setRefreshToken((payload.refreshToken as string) || '')
    setTokenExpireTime(Date.now() + 2 * 60 * 60 * 1000)
    setUserRoles(normalizeLoginRoles(payload, profile))
    setUserInfo(profile)
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
      localStorage.removeItem(USER_PROFILE_KEY)
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
    if (userRoles.value.includes(role)) {
      return true
    }
    // 与后端约定：userType 2 = B 端管理员；root 视为平台管理员（避免仅依赖本地缓存 roles）
    if (role === 'admin') {
      const u = userInfo.value
      if (u?.userType === 2 || u?.username?.toLowerCase() === 'root') {
        return true
      }
    }
    return false
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