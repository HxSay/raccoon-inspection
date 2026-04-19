import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { title: '注册', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'Odometer' }
      },
      {
        path: '/user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', roles: ['admin'] }
      },
      {
        path: '/role',
        name: 'Role',
        component: () => import('@/views/role/index.vue'),
        meta: { title: '角色管理', icon: 'UserFilled', roles: ['admin'] }
      },
      {
        path: '/dict/type',
        name: 'DictType',
        component: () => import('@/views/dict/type/index.vue'),
        meta: { title: '字典类型管理', icon: 'Document', roles: ['admin'] }
      },
      {
        path: '/dict/data',
        name: 'DictData',
        component: () => import('@/views/dict/data/index.vue'),
        meta: { title: '字典数据管理', icon: 'Collection', roles: ['admin'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const title = to.meta.title as string

  if (title) {
    document.title = `${title} - Raccoon Inspection`
  }

  // 检查是否需要认证
  if (to.meta.requiresAuth !== false) {
    // 检查用户是否登录
    if (!userStore.isLoggedIn()) {
      // 记录回跳地址
      const redirect = to.fullPath
      next(`/login?redirect=${encodeURIComponent(redirect)}`)
      return
    }

    // 检查用户角色权限
    const roles = to.meta.roles as string[]
    if (roles && roles.length > 0) {
      const hasRole = roles.some(role => userStore.hasRole(role))
      if (!hasRole) {
        ElMessage.error('没有权限访问该资源')
        next('/')
        return
      }
    }
  }

  // 如果用户已登录且访问登录页，跳转到首页
  if (to.path === '/login' && userStore.isLoggedIn()) {
    next('/')
    return
  }

  next()
})

export default router