<script setup lang="ts">
/**
 * M300 边缘自主巡检 — 3D 仿真主界面
 * 左：任务与异常模拟；中：Three 视口；右：实时遥测
 */
import { ref, shallowRef, onMounted, onBeforeUnmount, computed } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { ElMessage } from 'element-plus'
import { createPowerlineScene } from '@/sim/scene'
import { M300DroneModel } from '@/sim/drone'
import { MissionRunner } from '@/sim/missionRunner'
import { StateReportService } from '@/sim/stateReport'
import type { DeployMode, MissionReport, TelemetryPayload } from '@/sim/types'
import { assertWaypointLimit } from '@/sim/edgeService'
import { TELEMETRY_INTERVAL_MS, DJI_MAX_WAYPOINTS } from '@/sim/constants'

const canvasRef = ref<HTMLCanvasElement | null>(null)

const deployMode = ref<DeployMode>('groundStation')
const simulateDisconnect = ref(false)
const simulateLowBattery = ref(false)
const simulateRtkLost = ref(false)

const batteryPercent = ref(96)
const taskStatus = ref('待命')
const missionJson = ref('')

const telemetry = shallowRef<TelemetryPayload | null>(null)
const cloudReceiveCount = ref(0)
const offlineBufferHint = ref(0)

const reportOpen = ref(false)
const lastReport = shallowRef<MissionReport | null>(null)

const reportTableRows = computed(() => {
  const r = lastReport.value
  if (!r) return []
  return r.photos.map((p, i) => {
    const ai = r.aiResults[i]
    return {
      id: p.id,
      wp: p.waypointIndex,
      ai: ai ? `${ai.label} (${(ai.confidence * 100).toFixed(1)}%)` : '—',
      defect: ai ? (ai.hasDefect ? '疑似缺陷' : '未见缺陷') : '—'
    }
  })
})

let renderer: THREE.WebGLRenderer | null = null
let sceneBundle: ReturnType<typeof createPowerlineScene> | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let drone: M300DroneModel | null = null
let stateReport: StateReportService | null = null
let missionRunner: MissionRunner | null = null
let rafMain = 0
let disposeResize: (() => void) | null = null

function applyNetworkSim() {
  stateReport?.setOnline(!simulateDisconnect.value)
}

function getBatteryForCheck() {
  return simulateLowBattery.value ? 15 : batteryPercent.value
}

function getRtkForCheck() {
  return simulateRtkLost.value ? 0 : 2
}

function onTelemetry(t: TelemetryPayload) {
  telemetry.value = t
  offlineBufferHint.value = stateReport?.getBufferedCount() ?? 0
}

function onStatus(s: string) {
  taskStatus.value = s
}

function onPhoto() {
  offlineBufferHint.value = stateReport?.getBufferedCount() ?? 0
}

function onComplete(r: MissionReport) {
  lastReport.value = r
  reportOpen.value = true
  missionJson.value = missionRunner?.getDjiMissionPreview() ?? missionJson.value
}

function onError(e: Error) {
  ElMessage.error(e.message)
  taskStatus.value = '异常终止'
}

function initThree(): () => void {
  const canvas = canvasRef.value
  if (!canvas) return () => {}

  const w = canvas.clientWidth
  const h = canvas.clientHeight

  sceneBundle = createPowerlineScene()
  const { scene, world, homePosition, dispose: disposeScene } = sceneBundle

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false })
  renderer.setSize(w, h)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap

  camera = new THREE.PerspectiveCamera(55, w / h, 0.5, 800)
  camera.position.set(-80, 120, 160)
  camera.lookAt(homePosition)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.target.copy(homePosition)
  controls.minDistance = 40
  controls.maxDistance = 420
  controls.maxPolarAngle = Math.PI * 0.49

  drone = new M300DroneModel()
  drone.setPose(homePosition.clone(), 0)
  scene.add(drone.root)

  stateReport = new StateReportService()
  stateReport.setCloudSink(() => {
    cloudReceiveCount.value++
  })
  applyNetworkSim()

  missionRunner = new MissionRunner({
    drone,
    pathWorld: world,
    stateReport,
    home: homePosition.clone(),
    getDeployMode: () => deployMode.value,
    getBattery: getBatteryForCheck,
    setBattery: (v) => {
      batteryPercent.value = Math.round(v * 10) / 10
    },
    getRtkMode: getRtkForCheck,
    onStatus,
    onTelemetry,
    onPhoto: (_p, _ai) => {
      onPhoto()
    },
    onComplete,
    onError
  })

  const clock = new THREE.Clock()
  const tick = () => {
    rafMain = requestAnimationFrame(tick)
    const dt = clock.getDelta()
    drone?.tick(dt)
    controls?.update()
    if (renderer && sceneBundle && camera) {
      renderer.render(sceneBundle.scene, camera)
    }
  }
  tick()

  const onResize = () => {
    if (!canvasRef.value || !renderer || !camera) return
    const rw = canvasRef.value.clientWidth
    const rh = canvasRef.value.clientHeight
    renderer.setSize(rw, rh)
    camera.aspect = rw / rh
    camera.updateProjectionMatrix()
  }
  window.addEventListener('resize', onResize)
  disposeResize = () => window.removeEventListener('resize', onResize)

  return () => {
    disposeResize?.()
    disposeResize = null
    cancelAnimationFrame(rafMain)
    missionRunner?.dispose()
    missionRunner = null
    controls?.dispose()
    controls = null
    drone = null
    renderer?.dispose()
    renderer = null
    camera = null
    disposeScene()
    sceneBundle = null
    stateReport = null
  }
}

let disposeThree: (() => void) | null = null

onMounted(() => {
  disposeThree = initThree()
})

onBeforeUnmount(() => {
  disposeThree?.()
  disposeThree = null
})

async function startMission() {
  applyNetworkSim()
  cloudReceiveCount.value = 0
  await missionRunner?.start()
  missionJson.value = missionRunner?.getDjiMissionPreview() ?? ''
}

function resetMission() {
  missionRunner?.reset()
  batteryPercent.value = 96
  telemetry.value = null
  cloudReceiveCount.value = 0
  if (sceneBundle && drone) {
    drone.setPose(sceneBundle.homePosition.clone(), 0)
    drone.setGimbal(0, 0)
  }
}

function togglePause() {
  if (!missionRunner) return
  missionRunner.setPaused(!missionRunner.isPaused())
  taskStatus.value = missionRunner.isPaused() ? '已暂停' : '自主巡检中（CatmullRom 平滑航线）'
}

function try65535Demo() {
  try {
    assertWaypointLimit(DJI_MAX_WAYPOINTS + 1)
  } catch (e) {
    ElMessage.warning((e as Error).message)
  }
}
</script>

<template>
  <div class="flex h-full w-full min-h-0 flex-col bg-slate-950 text-slate-100 md:flex-row">
    <aside
      class="order-2 flex w-full shrink-0 flex-col gap-3 overflow-y-auto border-slate-800 p-3 md:order-1 md:w-72 md:border-r lg:w-80"
    >
      <div class="text-sm font-semibold text-sky-300">边缘仿真控制台</div>
      <el-card shadow="never" class="!border-slate-700 !bg-slate-900">
        <template #header>任务状态</template>
        <p class="mb-2 text-xs leading-relaxed text-slate-400">{{ taskStatus }}</p>
        <div class="flex flex-wrap gap-2">
          <el-button type="primary" size="small" @click="startMission">启动巡检</el-button>
          <el-button size="small" @click="togglePause">暂停 / 继续</el-button>
          <el-button size="small" @click="resetMission">重置</el-button>
        </div>
      </el-card>

      <el-card shadow="never" class="!border-slate-700 !bg-slate-900">
        <template #header>部署模式（影响附加通信延迟）</template>
        <el-radio-group v-model="deployMode" size="small" class="flex flex-col gap-2">
          <el-radio label="groundStation">地面站（+100ms）</el-radio>
          <el-radio label="onboard">机载（+20ms）</el-radio>
        </el-radio-group>
        <p class="mt-2 text-xs text-slate-500">
          云端固定 200ms + 模式附加延迟，见 <code class="text-sky-600">constants.ts</code>
        </p>
      </el-card>

      <el-card shadow="never" class="!border-slate-700 !bg-slate-900">
        <template #header>异常模拟</template>
        <el-switch v-model="simulateDisconnect" active-text="断网" @change="applyNetworkSim" />
        <p class="mt-1 text-xs text-slate-500">断网时遥测写入缓存，恢复链路后由 StateReport 自动补报</p>
        <el-divider class="!my-2" />
        <el-switch v-model="simulateLowBattery" active-text="低电量起飞" />
        <p class="mt-1 text-xs text-slate-500">自检使用 15% 电量，应提示「电量不足，无法起飞!」</p>
        <el-divider class="!my-2" />
        <el-switch v-model="simulateRtkLost" active-text="非 RTK 固定解" />
        <p class="mt-1 text-xs text-slate-500">mode≠2，应提示「未切换到RTK定位…」</p>
      </el-card>

      <el-card shadow="never" class="!border-slate-700 !bg-slate-900">
        <template #header>航点上限校验（65535）</template>
        <el-button size="small" @click="try65535Demo">触发超限校验</el-button>
      </el-card>

      <el-collapse v-if="missionJson" class="sim-collapse">
        <el-collapse-item title="大疆 Waypoint 任务 JSON（预览）" name="1">
          <pre class="max-h-48 overflow-auto text-[10px] leading-snug text-emerald-600">{{ missionJson }}</pre>
        </el-collapse-item>
      </el-collapse>
    </aside>

    <main class="relative order-1 min-h-[42vh] flex-1 md:order-2 md:min-h-0">
      <canvas ref="canvasRef" class="h-full w-full touch-none" />
      <div
        class="pointer-events-none absolute bottom-2 left-2 rounded bg-black/50 px-2 py-1 text-[10px] text-slate-300"
      >
        左键旋转 · 滚轮缩放 · 右键平移
      </div>
    </main>

    <aside
      class="order-3 flex w-full shrink-0 flex-col gap-2 overflow-y-auto border-slate-800 p-3 md:w-64 md:border-l lg:w-72"
    >
      <div class="text-sm font-semibold text-sky-300">实时状态</div>
      <el-descriptions :column="1" border size="small" class="!bg-slate-900">
        <el-descriptions-item label="位置 X">
          {{ telemetry?.position.x.toFixed(1) ?? '—' }} m
        </el-descriptions-item>
        <el-descriptions-item label="位置 Y">
          {{ telemetry?.position.y.toFixed(1) ?? '—' }} m
        </el-descriptions-item>
        <el-descriptions-item label="位置 Z">
          {{ telemetry?.position.z.toFixed(1) ?? '—' }} m
        </el-descriptions-item>
        <el-descriptions-item label="飞行高度">{{ telemetry?.altitudeM.toFixed(1) ?? '—' }} m</el-descriptions-item>
        <el-descriptions-item label="剩余电量">{{ telemetry?.batteryPercent.toFixed(1) ?? '—' }} %</el-descriptions-item>
        <el-descriptions-item label="飞行速度">{{ telemetry?.speedMps.toFixed(1) ?? '—' }} m/s</el-descriptions-item>
        <el-descriptions-item label="RTK 模式">{{ telemetry?.rtkMode ?? '—' }}（2=固定解）</el-descriptions-item>
        <el-descriptions-item label="任务进度">
          {{ telemetry ? (telemetry.missionProgress * 100).toFixed(0) + '%' : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="相位">{{ telemetry?.phase ?? '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-card shadow="never" class="!border-slate-700 !bg-slate-900">
        <template #header>云端遥测接收</template>
        <p class="text-xs text-slate-400">上报频率 {{ 1000 / TELEMETRY_INTERVAL_MS }}Hz（每 {{ TELEMETRY_INTERVAL_MS }}ms）</p>
        <p class="text-sm">累计接收 <b class="text-sky-400">{{ cloudReceiveCount }}</b> 条</p>
        <p class="text-xs text-amber-400">当前缓存 {{ offlineBufferHint }} 条</p>
      </el-card>
    </aside>

    <el-dialog v-model="reportOpen" title="巡检报告（仿真）" width="min(92vw, 720px)" destroy-on-close>
      <template v-if="lastReport">
        <el-descriptions :column="2" border size="small" class="mb-3">
          <el-descriptions-item label="任务时长">{{ lastReport.durationSec.toFixed(1) }} s</el-descriptions-item>
          <el-descriptions-item label="飞行距离">{{ lastReport.distanceM.toFixed(1) }} m</el-descriptions-item>
          <el-descriptions-item label="拍摄张数">{{ lastReport.photos.length }}</el-descriptions-item>
          <el-descriptions-item label="遥测上报">{{ lastReport.telemetrySent }} 条</el-descriptions-item>
        </el-descriptions>
        <div class="mb-2 text-xs text-slate-500">拍照元数据含伪 GPS 与云台角；原图不上云，仅本地 AI 结果如下表。</div>
        <el-table :data="reportTableRows" stripe size="small" max-height="280">
          <el-table-column prop="id" label="照片 ID" min-width="140" show-overflow-tooltip />
          <el-table-column prop="wp" label="航点" width="60" />
          <el-table-column prop="ai" label="AI 结果" min-width="120" />
          <el-table-column prop="defect" label="结论" width="100" />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sim-collapse {
  --el-collapse-header-bg-color: #0f172a;
  --el-collapse-content-bg-color: #020617;
  border: 1px solid #334155;
  border-radius: 6px;
  overflow: hidden;
}
:deep(.el-card__header) {
  padding: 8px 12px;
  font-size: 13px;
}
:deep(.el-card__body) {
  padding: 10px 12px;
}
</style>
