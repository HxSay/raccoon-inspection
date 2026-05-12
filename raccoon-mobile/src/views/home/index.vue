<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useOfflineQueueStore } from '@/stores/offlineQueue'

const router = useRouter()
const user = useUserStore()
const offline = useOfflineQueueStore()
</script>

<template>
  <div class="home">
    <van-nav-bar title="工作台" fixed placeholder />
    <div class="hero">
      <div class="greet">你好，{{ user.profile?.nickname || user.profile?.username }}</div>
      <div class="hint">按流程：任务 → 签到 → 执行 → 提交；异常可一键上报并生成工单。</div>
    </div>
    <van-grid :column-num="2" :border="false" class="grid">
      <van-grid-item icon="todo-list-o" text="巡检任务" @click="router.push('/task')" />
      <van-grid-item icon="cluster-o" text="设备台账" @click="router.push('/device')" />
      <van-grid-item icon="setting-o" text="维修工单" @click="router.push('/wo')" />
      <van-grid-item icon="warning-o" text="离线队列" :badge="offline.pending.length || undefined" @click="router.push('/profile')" />
    </van-grid>
    <van-cell-group inset title="流程指引" class="steps-card">
      <van-steps direction="vertical" :active="0" active-color="#1989fa">
        <van-step>接收任务 / 查看设备与巡检点</van-step>
        <van-step>GPS 签到 + 扫码确认设备</van-step>
        <van-step>逐项录入 / 拍照 / 阈值判定</van-step>
        <van-step>提交归档；异常自动生成工单</van-step>
      </van-steps>
    </van-cell-group>
  </div>
</template>

<style scoped>
.home {
  padding-bottom: 16px;
}
.hero {
  margin: 12px 16px;
  padding: 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.greet {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}
.hint {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}
.grid {
  margin-top: 8px;
}
.steps-card {
  margin: 16px;
}
</style>
