import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 30000
})

// 刷新 token 的请求队列
let refreshTokenPromise: Promise<any> | null = null

// 不需要添加 token 的路径
const noTokenPaths = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh']

service.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    
    // 检查是否需要添加 token
    const url = config.url || ''
    const shouldAddToken = !noTokenPaths.some(path => url.includes(path))
    
    console.log('Request URL:', url)
    console.log('Should add token:', shouldAddToken)
    console.log('Current token:', userStore.token)
    
    if (shouldAddToken && userStore.token) {
      // 检查 token 是否即将过期
        if (userStore.isTokenExpiringSoon()) {
          // 如果没有正在进行的刷新 token 请求，发起新的请求
          if (!refreshTokenPromise) {
            refreshTokenPromise = userStore.refreshAccessToken()
          }
          
          // 等待刷新 token 完成
          await refreshTokenPromise
          refreshTokenPromise = null
        }
      
      // 添加 token 到请求头
      config.headers.Authorization = `Bearer ${userStore.token}`
      console.log('Added token to request:', config.headers.Authorization)
    }
    
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  async (error) => {
    if (error.response) {
      const status = error.response.status
      const userStore = useUserStore()
      
      switch (status) {
        case 401:
          // Token 过期，尝试刷新 token
          if (!refreshTokenPromise) {
            refreshTokenPromise = userStore.refreshAccessToken()
          }
          
          try {
            // 等待刷新 token 完成
            await refreshTokenPromise
            refreshTokenPromise = null
            
            // 重新发送失败的请求
            return service(error.config)
          } catch (refreshError) {
            // 刷新 token 失败，退出登录
            ElMessage.error('Token已过期，请重新登录')
            userStore.logout()
            router.push('/login')
            return Promise.reject(refreshError)
          }
        case 403:
          ElMessage.error('没有权限访问该资源')
          // 跳转到首页
          router.push('/')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(error.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default service