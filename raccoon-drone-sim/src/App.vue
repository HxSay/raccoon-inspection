<script setup lang="ts">
/**
 * M300 边缘自主巡检 — 3D 仿真主界面（工业风 UI + 写实场景 + 机巢 / 边缘终端）
 */
import { ref, shallowRef, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { ElMessage } from 'element-plus'
import { createPowerlineScene } from '@/sim/scene'
import { createSubstationScene } from '@/sim/substationScene'
import { createThermalPlantScene } from '@/sim/thermalPlantScene'
import { M300DroneModel } from '@/sim/drone'
import { RobotDogModel } from '@/sim/robotDog'
import { MissionRunner } from '@/sim/missionRunner'
import { StateReportService } from '@/sim/stateReport'
import type { DeployMode, MissionReport, TelemetryPayload } from '@/sim/types'
import { assertWaypointLimit, fetchThermalPlantCloudPath } from '@/sim/edgeService'
import { TELEMETRY_INTERVAL_MS, DJI_MAX_WAYPOINTS } from '@/sim/constants'
import { DroneNest } from '@/sim/droneNest'
import { EdgeTerminal3D } from '@/sim/edgeTerminal'
import { createEdgeMetricsSimulator, type EdgeTerminalMetrics } from '@/sim/edgeMetrics'

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

const edgeMetrics = shallowRef<EdgeTerminalMetrics>({
  cpuPercent: 26,
  storagePercent: 42,
  networkMbps: 14,
  networkLabel: '链路正常'
})

const reportOpen = ref(false)
const lastReport = shallowRef<MissionReport | null>(null)

/** 3D 场景 Tab：输电巡检 / 变电站 / 火电站（室内廊道 + 机器狗） */
const sceneTab = ref<'patrol' | 'substation' | 'thermal'>('patrol')

/** 鸟瞰 / 地面观察（约人眼高度，仅巡检场地 Tab 有效） */
const viewMode = ref<'aerial' | 'ground'>('aerial')
const savedAerialOrbit = {
  position: new THREE.Vector3(),
  target: new THREE.Vector3(),
  valid: false as boolean
}

/** 切到变电站 Tab 前记住的巡检场地相机（切回时恢复） */
const savedPatrolCamera = {
  position: new THREE.Vector3(),
  target: new THREE.Vector3(),
  valid: false as boolean
}

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

let substationLoadFailed = false
let thermalLoadFailed = false

let renderer: THREE.WebGLRenderer | null = null
let sceneBundle: ReturnType<typeof createPowerlineScene> | null = null
let substationBundle: ReturnType<typeof createSubstationScene> | null = null
let thermalBundle: ReturnType<typeof createThermalPlantScene> | null = null
let robotDog: RobotDogModel | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let drone: M300DroneModel | null = null
let nest: DroneNest | null = null
let terminal: EdgeTerminal3D | null = null
let stateReport: StateReportService | null = null
let missionRunner: MissionRunner | null = null
let edgeSim = createEdgeMetricsSimulator(() => !simulateDisconnect.value)
let rafMain = 0
let disposeResize: (() => void) | null = null

const viewHint = computed(() => {
  if (sceneTab.value === 'substation') {
    return '变电站场景 · 地面仰视 · 左键环视 · 滚轮缩放（与输电场地独立）'
  }
  if (sceneTab.value === 'thermal') {
    return '火电站廊道巡检 · 机器狗与无人机共用航线/派发逻辑 · 左键环视'
  }
  return viewMode.value === 'ground'
    ? '地面视角 · 约 1.7 m 眼高 · 左键环视 · 滚轮前后移动'
    : '左键旋转 · 滚轮缩放 · 右键平移 · 阻尼已开启'
})

function applyViewMode(prev?: 'aerial' | 'ground') {
  if (!camera || !controls || !sceneBundle || sceneTab.value !== 'patrol') return
  const home = sceneBundle.homePosition

  if (viewMode.value === 'ground') {
    if (prev === 'aerial') {
      savedAerialOrbit.position.copy(camera.position)
      savedAerialOrbit.target.copy(controls.target)
      savedAerialOrbit.valid = true
    }
    camera.fov = 60
    camera.updateProjectionMatrix()
    // 站在起降区南侧略偏东，仰望线路走廊与杆塔（塔列 z≈-35）
    camera.position.set(38, 1.72, 58)
    controls.target.set(12, 38, -22)
    controls.minDistance = 0.35
    controls.maxDistance = 220
    controls.minPolarAngle = 0.08
    controls.maxPolarAngle = Math.PI * 0.499
  } else {
    camera.fov = 52
    camera.updateProjectionMatrix()
    if (savedAerialOrbit.valid) {
      camera.position.copy(savedAerialOrbit.position)
      controls.target.copy(savedAerialOrbit.target)
    } else {
      camera.position.set(-95, 135, 175)
      controls.target.copy(home)
    }
    controls.minDistance = 35
    controls.maxDistance = 520
    controls.minPolarAngle = 0
    controls.maxPolarAngle = Math.PI * 0.49
  }
  controls.update()
}

watch(viewMode, (mode, prev) => {
  applyViewMode(prev)
})

function ensureSubstationBundle(): void {
  if (!renderer || substationBundle || substationLoadFailed) return
  try {
    substationBundle = createSubstationScene(renderer)
  } catch (e) {
    console.error('[substation]', e)
    substationLoadFailed = true
    ElMessage.error('变电站场景初始化失败，请查看控制台')
  }
}

function applySubstationCamera() {
  if (!camera || !controls) return
  camera.fov = 58
  camera.updateProjectionMatrix()
  camera.position.set(4.2, 1.68, 30)
  controls.target.set(-7.5, 18.5, -7)
  controls.minDistance = 0.55
  controls.maxDistance = 88
  controls.minPolarAngle = 0.04
  controls.maxPolarAngle = Math.PI * 0.498
  controls.update()
}

function restorePatrolCameraAfterSubstation() {
  if (!camera || !controls || !sceneBundle) return
  if (savedPatrolCamera.valid) {
    camera.position.copy(savedPatrolCamera.position)
    controls.target.copy(savedPatrolCamera.target)
  } else {
    camera.position.set(-95, 135, 175)
    controls.target.copy(sceneBundle.homePosition)
  }
  camera.fov = viewMode.value === 'ground' ? 60 : 52
  camera.updateProjectionMatrix()
  if (viewMode.value === 'ground') {
    controls.minDistance = 0.35
    controls.maxDistance = 220
    controls.minPolarAngle = 0.08
    controls.maxPolarAngle = Math.PI * 0.499
  } else {
    controls.minDistance = 35
    controls.maxDistance = 520
    controls.minPolarAngle = 0
    controls.maxPolarAngle = Math.PI * 0.49
  }
  controls.update()
}

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
  nest?.setDoorTarget(0)
}

function ensureThermalPlantBundle(): void {
  if (!renderer || thermalBundle || thermalLoadFailed) return
  try {
    thermalBundle = createThermalPlantScene(renderer)
    robotDog = new RobotDogModel()
    robotDog.setPose(thermalBundle.homePosition.clone(), 0)
    thermalBundle.world.add(robotDog.root)
  } catch (e) {
    console.error('[thermal]', e)
    thermalLoadFailed = true
    ElMessage.error('火电站场景初始化失败，请查看控制台')
  }
}

function applyThermalCamera() {
  if (!camera || !controls) return
  camera.fov = 56
  camera.updateProjectionMatrix()
  camera.position.set(10, 1.55, 10)
  controls.target.set(-4, 2.2, -8)
  controls.minDistance = 0.45
  controls.maxDistance = 62
  controls.minPolarAngle = 0.06
  controls.maxPolarAngle = Math.PI * 0.48
  controls.update()
}

function rebuildMissionRunner() {
  missionRunner?.dispose()
  missionRunner = null
  if (!stateReport) return
  if (sceneTab.value === 'patrol' && sceneBundle && drone) {
    missionRunner = new MissionRunner({
      agent: drone,
      pathWorld: sceneBundle.world,
      stateReport,
      home: sceneBundle.homePosition.clone(),
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
      onError,
      visualHooks: buildVisualHooks()
    })
  } else if (sceneTab.value === 'thermal' && thermalBundle && robotDog) {
    missionRunner = new MissionRunner({
      agent: robotDog,
      pathWorld: thermalBundle.world,
      stateReport,
      home: thermalBundle.homePosition.clone(),
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
      onError,
      visualHooks: buildVisualHooks(),
      fetchPlannedPath: fetchThermalPlantCloudPath,
      vehicleClass: 'ugv',
      djiAircraftId: 'QUADRUPED_INSPECTION'
    })
  }
}

watch(
  sceneTab,
  (tab, prev) => {
    if (!camera || !controls) return
    if (tab === 'patrol' && (prev === 'substation' || prev === 'thermal')) {
      substationLoadFailed = false
      thermalLoadFailed = false
      restorePatrolCameraAfterSubstation()
    } else if (tab !== 'patrol' && prev === 'patrol' && sceneBundle) {
      savedPatrolCamera.position.copy(camera.position)
      savedPatrolCamera.target.copy(controls.target)
      savedPatrolCamera.valid = true
    }
    if (tab === 'substation') {
      ensureSubstationBundle()
      applySubstationCamera()
    } else if (tab === 'thermal') {
      ensureThermalPlantBundle()
      applyThermalCamera()
    }
    rebuildMissionRunner()
  },
  { flush: 'sync' }
)

function buildVisualHooks() {
  return {
    onPreflightPassed: () => {
      if (sceneTab.value === 'patrol') nest?.setDoorTarget(1)
    },
    onMissionEnded: () => {
      if (sceneTab.value === 'patrol') nest?.setDoorTarget(0)
    }
  }
}

function initThree(): () => void {
  const canvas = canvasRef.value
  if (!canvas) return () => {}

  const w = canvas.clientWidth
  const h = canvas.clientHeight

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false })
  renderer.setSize(w, h)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 0.92
  renderer.outputColorSpace = THREE.SRGBColorSpace

  sceneBundle = createPowerlineScene(renderer)
  const { scene, world, homePosition, terminalPosition, dispose: disposeScene } = sceneBundle

  camera = new THREE.PerspectiveCamera(52, w / h, 0.4, 1200)
  camera.position.set(-95, 135, 175)
  camera.lookAt(homePosition)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.target.copy(homePosition)
  controls.minDistance = 35
  controls.maxDistance = 520
  controls.maxPolarAngle = Math.PI * 0.49
  controls.rotateSpeed = 0.65
  controls.zoomSpeed = 0.85
  controls.panSpeed = 0.78
  controls.screenSpacePanning = true
  controls.mouseButtons = {
    LEFT: THREE.MOUSE.ROTATE,
    MIDDLE: THREE.MOUSE.DOLLY,
    RIGHT: THREE.MOUSE.PAN
  }

  nest = new DroneNest()
  const dockOffset = new THREE.Vector3(0, 1.35, 2.2)
  nest.root.position.copy(homePosition).sub(dockOffset)
  nest.setDoorTarget(0)
  world.add(nest.root)

  terminal = new EdgeTerminal3D()
  terminal.root.position.copy(terminalPosition)
  world.add(terminal.root)

  drone = new M300DroneModel()
  void drone.tryLoadExternalModel('/models/m300.glb')
  drone.setPose(homePosition.clone(), 0)
  scene.add(drone.root)

  stateReport = new StateReportService()
  stateReport.setCloudSink(() => {
    cloudReceiveCount.value++
  })
  applyNetworkSim()

  rebuildMissionRunner()

  edgeSim = createEdgeMetricsSimulator(() => !simulateDisconnect.value)

  const clock = new THREE.Clock()
  const tick = () => {
    rafMain = requestAnimationFrame(tick)
    const dt = clock.getDelta()
    nest?.tick(dt)
    edgeMetrics.value = edgeSim.tick(dt)
    drone?.tick(dt)
    if (sceneTab.value === 'thermal') robotDog?.tick(dt)
    controls?.update()
    if (!renderer || !camera) return
    const tab = sceneTab.value
    if (tab === 'substation') {
      ensureSubstationBundle()
      if (substationBundle) {
        renderer.render(substationBundle.scene, camera)
      } else if (sceneBundle) {
        renderer.render(sceneBundle.scene, camera)
      }
    } else if (tab === 'thermal') {
      ensureThermalPlantBundle()
      if (thermalBundle) {
        renderer.render(thermalBundle.scene, camera)
      } else if (sceneBundle) {
        renderer.render(sceneBundle.scene, camera)
      }
    } else if (tab === 'patrol' && sceneBundle) {
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
    drone?.dispose()
    drone = null
    nest?.dispose()
    nest = null
    terminal?.dispose()
    terminal = null
    renderer?.dispose()
    renderer = null
    camera = null
    substationBundle?.dispose()
    substationBundle = null
    substationLoadFailed = false
    thermalBundle?.dispose()
    thermalBundle = null
    robotDog?.dispose()
    robotDog = null
    thermalLoadFailed = false
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
  if (!missionRunner) {
    ElMessage.warning('当前场景不支持任务仿真（请切换到输电巡检或火电站）')
    return
  }
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
  nest?.setDoorTarget(0)
  if (sceneTab.value === 'patrol' && sceneBundle && drone) {
    drone.setPose(sceneBundle.homePosition.clone(), 0)
    drone.setGimbal(0, 0)
  }
  if (sceneTab.value === 'thermal' && thermalBundle && robotDog) {
    robotDog.setPose(thermalBundle.homePosition.clone(), 0)
    robotDog.setGimbal(0, 0)
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
  <div
    class="industrial-app flex h-full w-full min-h-0 flex-col border-t border-[var(--ia-border)] text-[#c8d4e0] md:flex-row"
  >
    <aside
      class="order-2 flex w-full shrink-0 flex-col gap-2.5 overflow-y-auto border-[var(--ia-border)] bg-[var(--ia-panel)] p-3 md:order-1 md:w-80 md:border-r lg:w-[22rem]"
    >
      <header class="border-b border-[var(--ia-border)] pb-2 font-mono text-xs tracking-wide text-[var(--ia-accent)]">
        RACCOON EDGE SIM / 边缘巡检仿真
      </header>

      <section v-if="sceneTab === 'patrol'" class="rounded border border-[var(--ia-border)] bg-[#0c141c] p-2.5">
        <div class="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-[var(--ia-muted)]">场景视角</div>
        <el-radio-group v-model="viewMode" size="small" class="flex flex-col gap-1.5 font-mono">
          <el-radio value="aerial">鸟瞰（默认轨道）</el-radio>
          <el-radio value="ground">地面观察（人眼高度）</el-radio>
        </el-radio-group>
        <p class="mt-1.5 text-[10px] leading-snug text-[var(--ia-muted)]">
          切至地面时为固定站位；从鸟瞰进入地面会记住当前机位，切回鸟瞰时恢复。
        </p>
      </section>

      <section class="rounded border border-[var(--ia-border)] bg-[#0c141c] p-2.5">
        <div class="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-[var(--ia-muted)]">边缘控制终端</div>
        <div class="grid grid-cols-2 gap-2 font-mono text-[11px]">
          <div>
            <span class="text-[var(--ia-muted)]">CPU</span>
            <div class="text-sm text-[#e8f0f8]">{{ edgeMetrics.cpuPercent.toFixed(1) }} %</div>
          </div>
          <div>
            <span class="text-[var(--ia-muted)]">存储</span>
            <div class="text-sm text-[#e8f0f8]">{{ edgeMetrics.storagePercent.toFixed(1) }} %</div>
          </div>
          <div class="col-span-2">
            <span class="text-[var(--ia-muted)]">网络</span>
            <div class="text-sm text-[#e8f0f8]">{{ edgeMetrics.networkMbps.toFixed(1) }} Mbps</div>
            <div class="text-[10px] text-[#6a8aa8]">{{ edgeMetrics.networkLabel }}</div>
          </div>
        </div>
      </section>

      <el-card v-if="sceneTab === 'patrol' || sceneTab === 'thermal'" shadow="never" class="ia-card">
        <template #header>任务状态</template>
        <p class="mb-2 font-mono text-[11px] leading-relaxed text-[var(--ia-muted)]">{{ taskStatus }}</p>
        <div class="flex flex-wrap gap-2">
          <el-button type="primary" size="small" class="!font-mono" @click="startMission">启动巡检</el-button>
          <el-button size="small" class="!font-mono" @click="togglePause">暂停 / 继续</el-button>
          <el-button size="small" class="!font-mono" @click="resetMission">重置</el-button>
        </div>
      </el-card>

      <el-card shadow="never" class="ia-card">
        <template #header>部署模式</template>
        <el-radio-group v-model="deployMode" size="small" class="flex flex-col gap-2 font-mono">
          <el-radio value="groundStation">地面站（+100ms RTT）</el-radio>
          <el-radio value="onboard">机载（+20ms RTT）</el-radio>
        </el-radio-group>
        <p class="mt-2 text-[10px] leading-snug text-[var(--ia-muted)]">
          云端固定 200ms + 模式附加延迟（<code class="text-[var(--ia-accent)]">constants.ts</code>）
        </p>
      </el-card>

      <el-card shadow="never" class="ia-card">
        <template #header>异常注入</template>
        <el-switch v-model="simulateDisconnect" active-text="断网" @change="applyNetworkSim" />
        <p class="mt-1 text-[10px] text-[var(--ia-muted)]">断网时遥测缓存，恢复后补报</p>
        <el-divider class="!my-2 !border-[var(--ia-border)]" />
        <el-switch v-model="simulateLowBattery" active-text="低电量起飞" />
        <p class="mt-1 text-[10px] text-[var(--ia-muted)]">自检 15% →「电量不足，无法起飞!」</p>
        <el-divider class="!my-2 !border-[var(--ia-border)]" />
        <el-switch v-model="simulateRtkLost" active-text="非 RTK 固定解" />
        <p class="mt-1 text-[10px] text-[var(--ia-muted)]">mode≠2 →「未切换到RTK定位…」</p>
      </el-card>

      <el-card shadow="never" class="ia-card">
        <template #header>航点上限（65535）</template>
        <el-button size="small" class="!font-mono" @click="try65535Demo">触发校验</el-button>
      </el-card>

      <el-collapse v-if="missionJson" class="ia-collapse">
        <el-collapse-item title="Waypoint 任务 JSON" name="1">
          <pre class="max-h-44 overflow-auto p-1 font-mono text-[10px] leading-snug text-[#6ecf9b]">{{ missionJson }}</pre>
        </el-collapse-item>
      </el-collapse>
    </aside>

    <main class="relative order-1 min-h-[44vh] flex-1 border-[var(--ia-border)] bg-black md:order-2 md:min-h-0 md:border-x">
      <div
        class="pointer-events-auto absolute left-1/2 top-2 z-20 flex -translate-x-1/2 rounded border border-[var(--ia-border)] bg-[#0a1018]/95 px-1 py-0.5 shadow-md backdrop-blur-sm"
      >
        <el-radio-group v-model="sceneTab" size="small" class="scene-tab-rg flex flex-wrap justify-center font-mono">
          <el-radio-button value="patrol">输电巡检场地</el-radio-button>
          <el-radio-button value="substation">变电站场景</el-radio-button>
          <el-radio-button value="thermal">火电站巡检</el-radio-button>
        </el-radio-group>
      </div>
      <canvas ref="canvasRef" class="h-full w-full touch-none" />
      <div
        class="pointer-events-none absolute bottom-2 left-2 rounded border border-[var(--ia-border)] bg-black/70 px-2 py-1 font-mono text-[10px] text-[var(--ia-muted)]"
      >
        {{ viewHint }}
      </div>
    </main>

    <aside
      class="order-3 flex w-full shrink-0 flex-col gap-2 overflow-y-auto border-[var(--ia-border)] bg-[var(--ia-panel)] p-3 md:w-72 md:border-l"
    >
      <div class="font-mono text-[11px] font-semibold uppercase tracking-wider text-[var(--ia-accent)]">无人机遥测</div>
      <el-descriptions :column="1" border size="small" class="ia-desc">
        <el-descriptions-item label="X / m">{{ telemetry?.position.x.toFixed(1) ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="Y / m">{{ telemetry?.position.y.toFixed(1) ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="Z / m">{{ telemetry?.position.z.toFixed(1) ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="高度">{{ telemetry?.altitudeM.toFixed(1) ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="电量 %">{{ telemetry?.batteryPercent.toFixed(1) ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="速度">{{ telemetry?.speedMps.toFixed(1) ?? '—' }} m/s</el-descriptions-item>
        <el-descriptions-item label="RTK">{{ telemetry?.rtkMode ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="进度">{{ telemetry ? (telemetry.missionProgress * 100).toFixed(0) + '%' : '—' }}</el-descriptions-item>
        <el-descriptions-item label="相位">{{ telemetry?.phase ?? '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-card shadow="never" class="ia-card">
        <template #header>云端接收</template>
        <p class="font-mono text-[10px] text-[var(--ia-muted)]">{{ 1000 / TELEMETRY_INTERVAL_MS }} Hz</p>
        <p class="font-mono text-sm">COUNT <b class="text-[var(--ia-accent)]">{{ cloudReceiveCount }}</b></p>
        <p class="font-mono text-[10px] text-amber-600/90">BUF {{ offlineBufferHint }}</p>
      </el-card>
    </aside>

    <el-dialog
      v-model="reportOpen"
      title="巡检报告"
      class="ia-dialog"
      width="min(92vw, 760px)"
      destroy-on-close
    >
      <template v-if="lastReport">
        <el-descriptions :column="2" border size="small" class="mb-3 font-mono">
          <el-descriptions-item label="时长 / s">{{ lastReport.durationSec.toFixed(1) }}</el-descriptions-item>
          <el-descriptions-item label="距离 / m">{{ lastReport.distanceM.toFixed(1) }}</el-descriptions-item>
          <el-descriptions-item label="照片">{{ lastReport.photos.length }}</el-descriptions-item>
          <el-descriptions-item label="遥测条数">{{ lastReport.telemetrySent }}</el-descriptions-item>
        </el-descriptions>
        <div class="mb-2 text-[10px] text-[var(--ia-muted)]">元数据含伪 GPS / 云台角；原图不上云。</div>
        <el-table :data="reportTableRows" stripe size="small" max-height="280" class="font-mono">
          <el-table-column prop="id" label="ID" min-width="130" show-overflow-tooltip />
          <el-table-column prop="wp" label="WP" width="52" />
          <el-table-column prop="ai" label="AI" min-width="110" />
          <el-table-column prop="defect" label="结论" width="92" />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ia-card {
  --el-card-bg-color: #0c141c;
  --el-card-border-color: var(--ia-border);
  border-radius: 2px;
}
:deep(.ia-card .el-card__header) {
  padding: 6px 10px;
  font-size: 11px;
  font-family: ui-monospace, monospace;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ia-muted);
  border-bottom: 1px solid var(--ia-border);
}
:deep(.ia-card .el-card__body) {
  padding: 10px;
}

.ia-collapse {
  border: 1px solid var(--ia-border);
  border-radius: 2px;
  overflow: hidden;
  --el-collapse-header-bg-color: #0c141c;
  --el-collapse-content-bg-color: #0a1018;
}
:deep(.ia-collapse .el-collapse-item__header) {
  font-family: ui-monospace, monospace;
  font-size: 11px;
  color: var(--ia-muted);
}

.ia-desc {
  --el-descriptions-item-bordered-label-background: #0c141c;
  --el-fill-color-blank: #0a1018;
  --el-border-color-lighter: var(--ia-border);
  font-family: ui-monospace, monospace;
  font-size: 11px;
}
:deep(.ia-desc .el-descriptions__label) {
  color: var(--ia-muted);
}
:deep(.ia-desc .el-descriptions__content) {
  color: #e2edf5;
}

:deep(.ia-dialog) {
  --el-dialog-bg-color: #0f1824;
  --el-dialog-border-color: var(--ia-border);
}
:deep(.scene-tab-rg .el-radio-button__inner) {
  font-family: ui-monospace, monospace;
  font-size: 11px;
  padding: 5px 10px;
}
</style>
