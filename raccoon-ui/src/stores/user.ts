import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, getCurrentUserInfo, LoginResponse } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<LoginResponse | null>(null)

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const setUserInfo = (info: LoginResponse) => {
    userInfo.value = info
  }

  const login = async (username: string, password: string) => {
    const res = await loginApi({ username, password })
    setToken('session-token') // 为了兼容前端逻辑，设置一个非空的token值
    setUserInfo(res.data)
    return res
  }

  const logout = async () => {
    try {
      await logoutApi()
    } catch {
      // ignore error
    } finally {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
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
    return !!token.value || !!userInfo.value
  }

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    login,
    logout,
    getUserInfo,
    isLoggedIn
  }
})