<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import InspectionAgentFab from '@/components/sim/InspectionAgentFab.vue'
import { AGENT_ACTIVE_DISPATCH_TASK_KEY, AGENT_ACTIVE_WORK_ORDER_KEY } from '@/api/agentPlanning'
import { postDispatchToSimIframe, subscribeInspectionDispatchBroadcast } from '@/utils/inspectionBridge'
import type { UavRouteDispatchPayload } from '@/api/drone'

/**
 * 嵌入 raccoon-drone-sim（默认 http://127.0.0.1:3010）。
 * 单独启动：npm run dev --prefix raccoon-drone-sim
 */
const envUrl = (import.meta.env.VITE_DRONE_SIM_URL as string | undefined)?.trim()
const simBase = computed(() => {
  if (envUrl) return envUrl.endsWith('/') ? envUrl : `${envUrl}/`
  return import.meta.env.DEV ? 'http://127.0.0.1:3010/' : '/sim-drone/'
})

const router = useRouter()
const standaloneUrl = 'http://localhost:3010'

const goBack = () => {
  router.push('/dashboard')
}
const iframeLoaded = ref(false)
const loadTimeout = ref(false)
const simIframeRef = ref<HTMLIFrameElement | null>(null)
const agentFabRef = ref<{
  prepareExternalMission: (
    payload: UavRouteDispatchPayload,
    options?: { workOrderId?: number; dispatchTaskId?: string }
  ) => void
} | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined

const onIframeLoad = () => {
  iframeLoaded.value = true
  loadTimeout.value = false
  if (timer) clearTimeout(timer)
}

const retryLoad = () => {
  iframeLoaded.value = false
  loadTimeout.value = false
  if (timer) clearTimeout(timer)
  const iframe = document.querySelector<HTMLIFrameElement>('.sim-iframe')
  if (iframe) {
    iframe.src = simBase.value
  }
}

let unsubscribeDispatch: (() => void) | undefined

onMounted(() => {
  timer = setTimeout(() => {
    if (!iframeLoaded.value) {
      loadTimeout.value = true
    }
  }, 12000)

  unsubscribeDispatch = subscribeInspectionDispatchBroadcast((msg) => {
    if (!iframeLoaded.value || !simIframeRef.value) {
      ElMessage.warning('仿真页尚未加载完成，请稍后从审核页再次通过或刷新仿真页')
      return
    }
    if (msg.workOrderId != null) {
      sessionStorage.setItem(AGENT_ACTIVE_WORK_ORDER_KEY, String(msg.workOrderId))
    }
    if (msg.dispatchTaskId) {
      sessionStorage.setItem(AGENT_ACTIVE_DISPATCH_TASK_KEY, msg.dispatchTaskId)
    }
    const ok = postDispatchToSimIframe(simIframeRef.value, msg.dispatch, {
      autoStart: msg.autoStart,
      userInput: msg.userInput,
      workOrderId: msg.workOrderId,
      dispatchTaskId: msg.dispatchTaskId
    })
    if (ok) {
      agentFabRef.value?.prepareExternalMission(msg.dispatch, {
        workOrderId: msg.workOrderId,
        dispatchTaskId: msg.dispatchTaskId
      })
      ElMessage.success('审核通过，无人机开始巡检')
    } else {
      ElMessage.error('无法向仿真窗口下发航线')
    }
  })
})

onBeforeUnmount(() => {
  unsubscribeDispatch?.()
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <div class="drone-sim-page">
    <button type="button" class="sim-exit-btn" title="返回系统" @click="goBack">
      <el-icon><ArrowLeft /></el-icon>
      <span>返回</span>
    </button>
    <div v-if="loadTimeout && !iframeLoaded" class="sim-panel sim-panel--error">
      <p class="sim-panel__title">仿真页面加载超时</p>
      <p class="sim-panel__desc">
        请确认已启动 raccoon-drone-sim（端口 3010），或单独打开仿真页。
      </p>
      <p class="sim-panel__cmd">
        <code>npm run dev --prefix raccoon-drone-sim</code>
      </p>
      <div class="sim-panel__actions">
        <el-button type="primary" @click="retryLoad">重新加载</el-button>
        <el-button>
          <a :href="standaloneUrl" target="_blank" rel="noopener" class="sim-link">单独打开</a>
        </el-button>
      </div>
    </div>

    <div v-else-if="!iframeLoaded" class="sim-panel sim-panel--loading">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <span>正在加载无人机仿真…</span>
    </div>

    <iframe
      ref="simIframeRef"
      class="sim-iframe"
      :src="simBase"
      title="无人机仿真模拟"
      allow="fullscreen"
      @load="onIframeLoad"
    />

    <!-- 右下角悬浮：NLP 巡检任务 Agent → 仿真无人机 -->
    <InspectionAgentFab ref="agentFabRef" :sim-iframe="simIframeRef" />
  </div>
</template>

<style scoped>
.drone-sim-page {
  position: relative;
  flex: 1;
  width: 100%;
  min-height: 0;
  height: 100vh;
  background: #0b1220;
  overflow: hidden;
}

.sim-iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: none;
  background: #0b1220;
}

.sim-panel {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
  text-align: center;
  color: #e5eaf3;
  background: rgba(11, 18, 32, 0.92);
}

.sim-panel--loading {
  pointer-events: none;
  color: #909399;
  font-size: 14px;
}

.sim-panel__title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.sim-panel__desc {
  margin: 0;
  color: #a8abb2;
  max-width: 480px;
  line-height: 1.6;
}

.sim-panel__cmd code {
  display: inline-block;
  margin-top: 8px;
  padding: 8px 12px;
  background: #1d2939;
  border-radius: 6px;
  color: #79bbff;
  font-size: 13px;
}

.sim-panel__actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.sim-link {
  color: inherit;
  text-decoration: none;
}

.sim-exit-btn {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 10;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.75);
  color: #e5eaf3;
  font-size: 13px;
  cursor: pointer;
  backdrop-filter: blur(6px);
}

.sim-exit-btn:hover {
  background: rgba(30, 41, 59, 0.9);
  border-color: rgba(255, 255, 255, 0.35);
}
</style>
