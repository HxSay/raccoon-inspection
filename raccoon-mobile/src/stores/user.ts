import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { storage, STORAGE_KEYS } from '@/utils/storage'

export interface UserProfile {
  id: number
  username: string
  nickname?: string
  userType?: number
}

/**
 * 登录态：与 PC 端共用 /auth/login、JWT。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(storage.get<string>(STORAGE_KEYS.TOKEN))
  const refreshToken = ref<string | null>(storage.get<string>(STORAGE_KEYS.REFRESH))
  const profile = ref<UserProfile | null>(storage.get<UserProfile>(STORAGE_KEYS.USER))

  const isLoggedIn = computed(() => !!token.value)

  function setSession(t: string, rt: string, user: UserProfile) {
    token.value = t
    refreshToken.value = rt
    profile.value = user
    storage.set(STORAGE_KEYS.TOKEN, t)
    storage.set(STORAGE_KEYS.REFRESH, rt)
    storage.set(STORAGE_KEYS.USER, user)
  }

  function logout() {
    token.value = null
    refreshToken.value = null
    profile.value = null
    storage.remove(STORAGE_KEYS.TOKEN)
    storage.remove(STORAGE_KEYS.REFRESH)
    storage.remove(STORAGE_KEYS.USER)
  }

  /** 巡检员/维修员：userType>=1 即允许使用移动端（可按角色细化） */
  function canUseMobile() {
    if (!profile.value) return false
    return true
  }

  return { token, refreshToken, profile, isLoggedIn, setSession, logout, canUseMobile }
})
