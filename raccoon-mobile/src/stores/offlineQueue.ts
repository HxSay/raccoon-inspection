import { defineStore } from 'pinia'
import { ref } from 'vue'
import { storage } from '@/utils/storage'
import axios from 'axios'

const QUEUE_KEY = 'offline_http_queue'

export interface QueuedRequest {
  id: string
  url: string
  method: string
  data?: unknown
  /** 提交时需带 Authorization */
  authorization?: string
  createdAt: number
}

/**
 * 离线队列：弱网或断网时将写操作暂存，联网后自动重放（需已登录 token）。
 */
export const useOfflineQueueStore = defineStore('offlineQueue', () => {
  const pending = ref<QueuedRequest[]>([])

  function load() {
    pending.value = storage.get<QueuedRequest[]>(QUEUE_KEY) || []
  }

  function persist() {
    storage.set(QUEUE_KEY, pending.value)
  }

  function enqueue(item: Omit<QueuedRequest, 'id' | 'createdAt'>) {
    load()
    const q: QueuedRequest = {
      ...item,
      id: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      createdAt: Date.now()
    }
    pending.value.push(q)
    persist()
  }

  async function flush() {
    load()
    if (pending.value.length === 0 || !navigator.onLine) return
    const remain: QueuedRequest[] = []
    for (const item of pending.value) {
      try {
        const res = await axios({
          url: item.url,
          method: item.method as 'POST',
          data: item.data,
          headers: {
            'Content-Type': 'application/json',
            ...(item.authorization ? { Authorization: item.authorization } : {})
          },
          baseURL: import.meta.env.VITE_APP_BASE_API || '/api'
        })
        const body = res.data as { code?: number }
        if (body?.code !== 200) {
          remain.push(item)
        }
      } catch {
        remain.push(item)
      }
    }
    pending.value = remain
    persist()
  }

  load()
  return { pending, enqueue, flush, persist, load }
})
