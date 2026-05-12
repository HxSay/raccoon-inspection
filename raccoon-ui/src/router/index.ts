import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
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
  /** 手机端：当前登录用户作为巡检人时，查看待执行/执行中/已完成的巡检工单 */
  {
    path: '/m',
    component: () => import('@/layouts/MobileLayout.vue'),
    redirect: '/m/patrol',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'patrol',
        name: 'MobilePatrolOrders',
        component: () => import('@/views/cmms/inspection/mobile/MyPatrolOrders.vue'),
        meta: { title: '我的巡检工单' }
      },
      {
        path: 'execute/:orderId',
        name: 'MobilePatrolExecute',
        component: () => import('@/views/cmms/inspection/work-order/Execute.vue'),
        meta: { title: '巡检执行', hidden: true }
      },
      {
        path: 'detail/:orderId',
        name: 'MobilePatrolDetail',
        component: () => import('@/views/cmms/inspection/work-order/Detail.vue'),
        meta: { title: '巡检详情', hidden: true }
      }
    ]
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
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: '/role',
        name: 'Role',
        component: () => import('@/views/role/index.vue'),
        meta: { title: '角色管理', icon: 'UserFilled' }
      },
      {
        path: '/dict/type',
        name: 'DictType',
        component: () => import('@/views/dict/type/index.vue'),
        meta: { title: '字典类型管理', icon: 'Document' }
      },
      {
        path: '/dict/data',
        name: 'DictData',
        component: () => import('@/views/dict/data/index.vue'),
        meta: { title: '字典数据管理', icon: 'Collection' }
      },
      {
        path: '/cmms/device',
        name: 'CmmsDevice',
        component: () => import('@/views/cmms/device/index.vue'),
        meta: { title: '设备管理', icon: 'Monitor' }
      },
      {
        path: '/cmms/inspection',
        name: 'CmmsInspection',
        component: () => import('@/views/cmms/inspection/index.vue'),
        meta: { title: '巡检管理', icon: 'DocumentChecked' }
      },
      {
        path: '/cmms/maintenance',
        name: 'CmmsMaintenance',
        component: () => import('@/views/cmms/maintenance/index.vue'),
        meta: { title: '设备维护工单', icon: 'Tools' }
      },
      {
        path: '/cmms/inspection/work-order/form',
        name: 'InspectionWorkOrderForm',
        component: () => import('@/views/cmms/inspection/work-order/Form.vue'),
        meta: { title: '新建巡检工单', hidden: true }
      },
      {
        path: '/cmms/inspection/work-order/execute/:orderId',
        name: 'InspectionWorkOrderExecute',
        component: () => import('@/views/cmms/inspection/work-order/Execute.vue'),
        meta: { title: '巡检执行', hidden: true }
      },
      {
        path: '/cmms/inspection/work-order/detail/:orderId',
        name: 'InspectionWorkOrderDetail',
        component: () => import('@/views/cmms/inspection/work-order/Detail.vue'),
        meta: { title: '巡检结果', hidden: true }
      },
      {
        path: '/cmms/grid-inspection-wo',
        redirect: '/cmms/inspection?tab=order'
      },
      {
        path: '/cmms/grid-inspection-wo/form',
        redirect: '/cmms/inspection/work-order/form'
      },
      {
        path: '/cmms/grid-inspection-wo/execute/:orderId',
        redirect: (to) => ({ path: `/cmms/inspection/work-order/execute/${to.params.orderId}` })
      },
      {
        path: '/cmms/grid-inspection-wo/detail/:orderId',
        redirect: (to) => ({ path: `/cmms/inspection/work-order/detail/${to.params.orderId}` })
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
  }

  // 如果用户已登录且访问登录页，跳转到首页
  if (to.path === '/login' && userStore.isLoggedIn()) {
    next('/')
    return
  }

  next()
})

export default router