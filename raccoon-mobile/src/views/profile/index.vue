<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { useOfflineQueueStore } from '@/stores/offlineQueue'

const user = useUserStore()
const offline = useOfflineQueueStore()
const router = useRouter()

const displayName = computed(() => user.profile?.nickname || user.profile?.username || '未登录')
const queueCount = computed(() => offline.pending.length)

async function flushQueue() {
  await offline.flush()
  showToast('已尝试同步离线队列')
}

function logout() {
  user.logout()
  router.replace('/login')
}
</script>

<template>
  <div>
    <van-nav-bar title="个人中心" fixed placeholder />
    <div class="hero">
      <div class="avatar">{{ (displayName || '?').slice(0, 1) }}</div>
      <div class="meta">
        <div class="name">{{ displayName }}</div>
        <div class="sub">{{ user.profile?.username }}</div>
      </div>
    </div>
    <van-cell-group inset>
      <van-cell title="离线待同步" :value="String(queueCount) + ' 条'">
        <template #right-icon>
          <van-button size="small" type="primary" plain :disabled="queueCount === 0" @click="flushQueue">立即同步</van-button>
        </template>
      </van-cell>
      <van-cell title="关于" value="智能巡检移动端 · Vue3 + Vant" />
    </van-cell-group>
    <div class="out">
      <van-button block round type="danger" plain @click="logout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped>
.hero {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 20px;
  background: linear-gradient(135deg, #1c2333, #2d3a52);
  color: #fff;
  margin-bottom: 12px;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 600;
}
.name {
  font-size: 18px;
  font-weight: 600;
}
.sub {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 4px;
}
.out {
  padding: 24px 16px;
}
</style>
