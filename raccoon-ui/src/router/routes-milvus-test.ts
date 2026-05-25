import type { RouteRecordRaw } from 'vue-router'

/**
 * Milvus 测试页路由片段。
 * 合并到 router/index.ts 的 MainLayout children 数组中即可。
 */
export const milvusTestRoute: RouteRecordRaw = {
  path: '/ai/milvus/test',
  name: 'MilvusTest',
  component: () => import('@/views/ai/milvus-test/index.vue'),
  meta: { title: 'Milvus 测试', icon: 'Coin' }
}
