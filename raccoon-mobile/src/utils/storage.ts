/**
 * 本地存储封装：Token、用户信息、离线队列持久化键名集中管理。
 */
const PREFIX = 'raccoon_mobile_'

export const storage = {
  get<T>(key: string): T | null {
    try {
      const raw = localStorage.getItem(PREFIX + key)
      if (raw == null) return null
      return JSON.parse(raw) as T
    } catch {
      return null
    }
  },
  set(key: string, value: unknown) {
    localStorage.setItem(PREFIX + key, JSON.stringify(value))
  },
  remove(key: string) {
    localStorage.removeItem(PREFIX + key)
  }
}

export const STORAGE_KEYS = {
  TOKEN: 'token',
  REFRESH: 'refreshToken',
  USER: 'userProfile'
} as const
