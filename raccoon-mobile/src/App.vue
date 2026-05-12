<script setup lang="ts">
import { onMounted } from 'vue'
import { useOfflineQueueStore } from '@/stores/offlineQueue'

const offline = useOfflineQueueStore()

/** 网络恢复后尝试重放离线队列 */
onMounted(() => {
  window.addEventListener('online', () => offline.flush())
})
</script>

<template>
  <router-view />
</template>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
  background: #f4f6f8;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
</style>
