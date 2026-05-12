<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const pageTitle = computed(() => {
  const last = route.matched.filter((r) => r.meta?.title).pop()
  return (last?.meta?.title as string) || '我的巡检'
})

const logout = async () => {
  try {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    await userStore.logout()
    router.push('/login')
  } catch {
    /* 取消 */
  }
}
</script>

<template>
  <div class="mobile-layout">
    <header class="bar">
      <div class="brand">{{ pageTitle }}</div>
      <div class="actions">
        <el-button link type="primary" @click="router.push('/dashboard')">工作台</el-button>
        <el-button link type="primary" @click="logout">退出</el-button>
      </div>
    </header>
    <main class="body">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.mobile-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f6f8;
}

.bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #304156;
  color: #fff;
}

.brand {
  font-size: 16px;
  font-weight: 600;
}

.actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.actions :deep(.el-button) {
  color: #bfcbd9;
}

.actions :deep(.el-button:hover) {
  color: #fff;
}

.body {
  flex: 1;
  overflow: auto;
  -webkit-overflow-scrolling: touch;
}
</style>
