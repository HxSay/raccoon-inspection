import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: () => import('@/views/login/index.vue'), meta: { public: true, title: '登录' } },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/home' },
      { path: 'home', name: 'Home', component: () => import('@/views/home/index.vue'), meta: { title: '工作台' } },
      { path: 'device', name: 'Device', component: () => import('@/views/device/list.vue'), meta: { title: '设备台账' } },
      { path: 'device/:id', name: 'DeviceDetail', component: () => import('@/views/device/detail.vue'), meta: { title: '设备详情' } },
      { path: 'task', name: 'Task', component: () => import('@/views/task/list.vue'), meta: { title: '巡检任务' } },
      { path: 'task/:id', name: 'TaskDetail', component: () => import('@/views/task/detail.vue'), meta: { title: '任务详情' } },
      { path: 'task/:id/exec', name: 'TaskExec', component: () => import('@/views/task/execute.vue'), meta: { title: '巡检执行' } },
      { path: 'task/:id/report', name: 'TaskReport', component: () => import('@/views/task/report.vue'), meta: { title: '异常上报' } },
      { path: 'wo', name: 'Wo', component: () => import('@/views/wo/list.vue'), meta: { title: '维修工单' } },
      { path: 'wo/:id', name: 'WoDetail', component: () => import('@/views/wo/detail.vue'), meta: { title: '工单详情' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/profile/index.vue'), meta: { title: '个人中心' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  document.title = (to.meta.title as string) || '智能巡检'
  const user = useUserStore()
  if (to.meta.public) {
    if (user.isLoggedIn && to.path === '/login') next('/home')
    else next()
    return
  }
  if (to.meta.requiresAuth && !user.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router
