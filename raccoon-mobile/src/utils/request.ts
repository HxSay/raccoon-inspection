import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { useOfflineQueueStore } from '@/stores/offlineQueue'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '/api',
  timeout: 30000
})

const WRITE_PATHS = [
  '/cmms/inspection/task/checkIn',
  '/cmms/inspection/task/start',
  '/cmms/inspection/task/complete',
  '/cmms/inspection/task/reportAbnormal',
  '/cmms/workOrder/save',
  '/cmms/workOrder/status'
]

function pathWithQuery(cfg: InternalAxiosRequestConfig): string {
  let path = cfg.url || ''
  const params = cfg.params as Record<string, unknown> | undefined
  if (params && Object.keys(params).length) {
    const sp = new URLSearchParams()
    for (const [k, v] of Object.entries(params)) {
      if (v != null && v !== '') sp.append(k, String(v))
    }
    const q = sp.toString()
    if (q) path += (path.includes('?') ? '&' : '?') + q
  }
  return path
}

function shouldQueueOffline(url: string) {
  const base = url.split('?')[0] || url
  return WRITE_PATHS.some((p) => base.includes(p))
}

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const user = useUserStore()
  const url = config.url || ''
  if (!url.includes('/auth/login') && user.token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${user.token}`
  }
  return config
})

service.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body.code !== 200) {
      showToast(body.msg || '请求失败')
      if (body.code === 401) {
        useUserStore().logout()
        void import('@/router').then((m) => m.default.replace('/login'))
      }
      return Promise.reject(new Error(body.msg || 'fail'))
    }
    return body
  },
  (err) => {
    const config = err.config
    const offline = useOfflineQueueStore()
    if (!navigator.onLine && config && shouldQueueOffline(config.url || '')) {
      offline.enqueue({
        url: pathWithQuery(config),
        method: config.method?.toUpperCase() || 'POST',
        data: config.data,
        authorization: (config.headers as Record<string, string>)?.Authorization
      })
      showToast('已离线保存，网络恢复后自动提交')
      return Promise.resolve({ code: 200, msg: 'queued', data: null })
    }
    showToast(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default service
