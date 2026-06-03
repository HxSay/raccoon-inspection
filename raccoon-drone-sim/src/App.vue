<script setup lang="ts">
/**
 * M300 边缘自主巡检 — 3D 仿真主界面（工业风 UI + 写实场景 + 机巢 / 边缘终端）
 */
import { ref, shallowRef, onMounted, onBeforeUnmount, computed, watch, reactive } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createPowerlineScene } from '@/sim/scene'
import { createSubstationScene } from '@/sim/substationScene'
import { createThermalPlantScene } from '@/sim/thermalPlantScene'
import { M300DroneModel } from '@/sim/drone'
import { RobotDogModel } from '@/sim/robotDog'
import { MissionRunner } from '@/sim/missionRunner'
import { StateReportService } from '@/sim/stateReport'
import type { DeployMode, MissionReport, TelemetryPayload } from '@/sim/types'
import { assertWaypointLimit, fetchCloudPlannedPath, fetchThermalPlantCloudPath } from '@/sim/edgeService'
import { fetchRouteDispatch } from '@/api/droneRoute'
import type { UavRouteDispatchPayload } from '@/types/droneDispatch'
import { EdgeCloudTelemetryReporter } from '@/sim/edgeCloudTelemetry'
import { uploadMultimodalMissionResult } from '@/sim/edgeCloudMultimodal'
import { slimMissionReport } from '@/utils/slimMissionReport'
import type { MultimodalModalityType } from '@/sim/multimodalTypes'
import {
  dispatchToCloudPath,
  dispatchToDjiWaypointMission
} from '@/sim/dispatchConverter'
import type { CloudPathPoint } from '@/sim/types'
import { TELEMETRY_INTERVAL_MS, DJI_MAX_WAYPOINTS } from '@/sim/constants'
import { PATROL_AERIAL_CAMERA, PATROL_CORRIDOR_Z0, PATROL_SCENE_LOOK } from '@/sim/scenePatrolLayout'
import {
  formatCoordForBackend,
  getPatrolReferenceTable,
  type TowerCoordRow
} from '@/sim/towerInspectionCoords'
import { DroneNest } from '@/sim/droneNest'
import { EdgeTerminal3D } from '@/sim/edgeTerminal'
import { createEdgeMetricsSimulator, type EdgeTerminalMetrics } from '@/sim/edgeMetrics'
import { SceneObjectEditor, type SceneObjectEditorSnapshot } from '@/sim/sceneEditor'
import {
  applySceneState,
  appendRemovalAndPersist,
  clearAllSceneStates,
  clearSceneState,
  getStablePathToWorld,
  loadSceneState,
  saveSceneState,
  type ScenePersistTab
} from '@/sim/scenePersistence'
import { captureSceneFromInspectionRig, disposeInspectionCaptureRenderer } from '@/sim/droneCameraCapture'
import { SceneEditor3D } from '@/editor/SceneEditor3D'
import type { EditorUiState } from '@/editor/types'
import EditorOutliner from '@/components/EditorOutliner.vue'
import EditorToolbar from '@/components/EditorToolbar.vue'
import EditorProperties from '@/components/EditorProperties.vue'
import SimControlPanel from '@/components/SimControlPanel.vue'
import { fetchRobotsBySceneType, type InspectionRobotVO } from '@/api/inspectionRobot'
import { syncFieldDevicesFromEditor } from '@/api/fieldSceneDevice'
import {
  applyCloudFieldDevicesToEditor,
  applySingleCloudDevice,
  FIELD_DEVICE_CHANNEL,
  postFieldDeviceChannel,
  removeCloudDeviceFromEditor,
  type FieldDeviceChannelMessage
} from '@/sim/fieldDeviceSceneSync'
import {
  reportRobotBattery1Hz,
  reportRobotLoad1Hz,
  reportRobotPosition10Hz,
  reportRobotPresenceHeartbeat
} from '@/api/inspectionRobotTelemetry'
import { attachRobotLabel, type RobotLabelHandle } from '@/sim/robotLabelMarkers'
import { listFireEnabledTowers, setTowerFireSimulation } from '@/sim/sceneHazard'
import { syncTowerFireVisuals, tickTowerFireVisuals } from '@/sim/towerFireVisual'
import {
  INSPECTION_DISPATCH_CHANNEL,
  MSG_INSPECTION_ANOMALY,
  MSG_INSPECTION_DISPATCH,
  MSG_INSPECTION_DISPATCH_ACK,
  MSG_INSPECTION_MISSION_COMPLETE,
  MSG_INSPECTION_MISSION_ERROR,
  MSG_INSPECTION_STATUS,
  type InspectionAnomalyPayload,
  type InspectionDispatchMessage
} from '@/types/inspectionBridge'

const canvasRef = ref<HTMLCanvasElement | null>(null)
/** 包裹 canvas：flex 子项里用绝对定位填满，避免 clientWidth 与缓冲区不一致导致半屏黑 */
const canvasWrapperRef = ref<HTMLElement | null>(null)

const deployMode = ref<DeployMode>('groundStation')
const simulateDisconnect = ref(false)
const simulateLowBattery = ref(false)
const simulateRtkLost = ref(false)
/** 杆塔 1~5 火情模拟（场景效果 → 巡检上报 → 故障分级） */
const fireTowerFlags = ref([false, false, false, false, false])

watch(
  fireTowerFlags,
  (flags) => {
    flags.forEach((on, i) => setTowerFireSimulation(i + 1, on))
    if (sceneBundle?.world) {
      syncTowerFireVisuals(sceneBundle.world, listFireEnabledTowers())
    }
  },
  { deep: true }
)

const batteryPercent = ref(96)
const taskStatus = ref('待命')
/** 巡检任务是否在飞（返航/拍照中也算）；结束后应停止 10Hz 遥测轮询 */
const inspectionInFlight = ref(false)
const missionJson = ref('')
const routeFetchUavId = ref<number | undefined>(1)
const routeFetchPlanId = ref<number | undefined>(5)
const routeFetchLoading = ref(false)
const routeFetchRawJson = ref('')
/** 从云端拉取并锚定到机巢的输电巡检航迹（有值时任务按 waypoint 直线飞行） */
const cloudPatrolPath = shallowRef<CloudPathPoint[] | null>(null)
/** 当前任务上下文（拉取 dispatch 后用于轨迹上报 uav_location_history） */
const activeMissionMeta = shallowRef<Pick<UavRouteDispatchPayload, 'uavId' | 'taskId' | 'mapId'> | null>(null)

const patrolTowerCoordRows = computed(() => getPatrolReferenceTable())

function roleLabel(role: TowerCoordRow['role']) {
  if (role === 'tower_center') return '杆塔'
  if (role === 'photo_inspection') return '拍照'
  if (role === 'drone_nest') return '机巢'
  if (role === 'ground_station') return '地面站'
  return '起降'
}

async function copyTowerCoord(row: TowerCoordRow) {
  let target = row
  if (row.role === 'tower_center') {
    const photo = patrolTowerCoordRows.value.find(
      (r) => r.role === 'photo_inspection' && r.index === row.index
    )
    if (photo) target = photo
  }
  const text = formatCoordForBackend(target)
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`已复制：${row.label}`)
  } catch {
    ElMessage.warning(text)
  }
}

async function copyAllPhotoCoords() {
  const lines = patrolTowerCoordRows.value
    .filter((r) => r.role === 'photo_inspection')
    .map((r) => `${r.label}\t${formatCoordForBackend(r)}`)
  const text = lines.join('\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制全部巡检拍照坐标')
  } catch {
    ElMessage.warning(text)
  }
}

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
/** 多机输电巡检：各机任务报告缓存，凑齐后合并弹窗 */
const patrolFleetBuffer = ref<MissionReport[]>([])

const MODALITY_LABEL: Record<MultimodalModalityType, string> = {
  VISIBLE: '可见光',
  THERMAL: '热成像',
  AUDIO: '声音',
  VIBRATION: '振动',
  TEMPERATURE: '温度'
}

function mergePatrolReports(reports: MissionReport[]): MissionReport {
  const startedAt = Math.min(...reports.map((r) => r.startedAt))
  const finishedAt = Math.max(...reports.map((r) => r.finishedAt))
  return {
    startedAt,
    finishedAt,
    durationSec: (finishedAt - startedAt) / 1000,
    distanceM: reports.reduce((s, r) => s + r.distanceM, 0),
    photos: reports.flatMap((r) => r.photos),
    aiResults: reports.flatMap((r) => r.aiResults),
    multimodalSamples: reports.flatMap((r) => r.multimodalSamples ?? []),
    telemetrySent: reports.reduce((s, r) => s + r.telemetrySent, 0),
    bufferedWhileOffline: Math.max(...reports.map((r) => r.bufferedWhileOffline))
  }
}

/** 3D 场景 Tab：输电巡检 / 变电站 / 火电站（室内廊道 + 机器狗） */
const sceneTab = ref<'patrol' | 'substation' | 'thermal'>('patrol')

/** 3D 场景编辑器：默认开启；关闭后恢复旧 Shift 点选与无人机侧栏 */
const sceneEditEnabled = ref(true)
const outlinerDrawerOpen = ref(false)
const sceneEditorSnap = shallowRef<SceneObjectEditorSnapshot | null>(null)
const editForm = reactive({
  x: 0,
  y: 0,
  z: 0,
  rotYdeg: 0,
  sx: 1,
  sy: 1,
  sz: 1
})

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
      thumb: p.imageDataUrl ?? '',
      wp: p.waypointIndex,
      ai: ai ? `${ai.label} (${(ai.confidence * 100).toFixed(1)}%)` : '—',
      defect: ai ? (ai.hasDefect ? '疑似缺陷' : '未见缺陷') : '—'
    }
  })
})

const multimodalTableRows = computed(() => {
  const r = lastReport.value
  if (!r?.multimodalSamples?.length) return []
  return r.multimodalSamples.map((s) => ({
    id: s.id,
    wp: s.waypointIndex,
    modality: MODALITY_LABEL[s.modalityType],
    preview: s.previewDataUrl ?? '',
    summary: summarizeMultimodalPayload(s.modalityType, s.payload)
  }))
})

function summarizeMultimodalPayload(type: MultimodalModalityType, payload: Record<string, unknown>): string {
  if (type === 'TEMPERATURE') {
    const max = payload.maxC as number | undefined
    const suffix = max != null && max >= 80 ? '（超温告警）' : ''
    return `${payload.ambientC}~${payload.maxC} °C${suffix}`
  }
  if (type === 'THERMAL' && payload.anomaly) {
    return `热点 ${payload.minC}~${payload.maxC} °C（异常）`
  }
  if (type === 'AUDIO') {
    return `峰值 ${payload.peakDb} dB`
  }
  if (type === 'VIBRATION') {
    const axis = payload.axis as { z?: { rms?: number } }
    return `Z rms ${axis?.z?.rms ?? '—'} mm/s`
  }
  if (type === 'VISIBLE' || type === 'THERMAL') {
    return payload.thumbnail ? '含缩略图' : '—'
  }
  return '—'
}

let substationLoadFailed = false
let thermalLoadFailed = false

let renderer: THREE.WebGLRenderer | null = null
let sceneBundle: ReturnType<typeof createPowerlineScene> | null = null
let substationBundle: ReturnType<typeof createSubstationScene> | null = null
let thermalBundle: ReturnType<typeof createThermalPlantScene> | null = null
let robotDog: RobotDogModel | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
/** 输电场地多机（与云端巡检机器人配置对应） */
let patrolDrones: M300DroneModel[] = []
let patrolRobotLabels: RobotLabelHandle[] = []
let patrolRobotConfigs: InspectionRobotVO[] = []
/** 与 patrolDrones 下标对应的云端机器人 ID */
let patrolFleetUavIds: number[] = []
/** 每架巡逻机的归位点（机巢 / 被拖动后的位置），下标与 patrolDrones 对应 */
let patrolHomes: THREE.Vector3[] = []
/** Agent 派单后的执行方案：仅就近选中的无人机参与，从各自当前位置起飞执行 */
let fleetPlan: {
  drone: M300DroneModel
  home: THREE.Vector3
  path: import('@/sim/types').CloudPathPoint[]
}[] = []
const PATROL_HOME_STORAGE_KEY = 'raccoon-sim-patrol-drone-homes'

function loadSavedPatrolHomes(): Array<{ x: number; y: number; z: number }> {
  try {
    const raw = localStorage.getItem(PATROL_HOME_STORAGE_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function savePatrolHomes(): void {
  try {
    localStorage.setItem(
      PATROL_HOME_STORAGE_KEY,
      JSON.stringify(patrolHomes.map((h) => ({ x: h.x, y: h.y, z: h.z })))
    )
  } catch {
    /* 忽略本地存储异常 */
  }
}
let nest: DroneNest | null = null
let terminal: EdgeTerminal3D | null = null
/** 杆塔被移动后导线重建的合帧标志（每帧最多重建一次） */
let wireRebuildScheduled = false
/** 每架巡逻机独立遥测通道（避免一机结束 stop 掉共享 10Hz 定时器） */
let stateReports: StateReportService[] = []
let edgeCloudReporters: EdgeCloudTelemetryReporter[] = []
let missionRunners: MissionRunner[] = []
/** 最后一架完成输电任务后关舱门 */
let patrolNestLanded = 0
let edgeSim = createEdgeMetricsSimulator(() => !simulateDisconnect.value)
let rafMain = 0
let disposeResize: (() => void) | null = null
let canvasResizeObserver: ResizeObserver | null = null
let sceneObjectEditor: SceneObjectEditor | null = null
const sceneEditor3dRef = shallowRef<SceneEditor3D | null>(null)
const editorUiState = shallowRef<EditorUiState | null>(null)
let fieldDeviceSyncTimer: ReturnType<typeof setTimeout> | null = null
const fieldDeviceSyncHint = ref('')

const viewHint = computed(() => {
  const edit =
    sceneEditEnabled.value && (sceneTab.value === 'patrol' || sceneTab.value === 'substation' || sceneTab.value === 'thermal')
      ? ' 编辑：顶部工具栏（居中）· 场景对象在顶部抽屉 · 右侧属性与任务控制。'
      : ''
  if (sceneTab.value === 'substation') {
    return '变电站场景 · 地面仰视 · 左键环视 · 滚轮缩放（与输电场地独立）' + edit
  }
  if (sceneTab.value === 'thermal') {
    return '火电站厂区 · 机器狗贴地沿道路巡检 · 左键环视 · 滚轮缩放' + edit
  }
  return viewMode.value === 'ground'
    ? '地面视角 · 约 1.7 m 眼高 · 左键环视 · 滚轮前后移动' + edit
    : '左键旋转 · 滚轮缩放 · 右键平移 · 阻尼已开启' + edit
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
    // 站在起降区南侧略偏东，仰望特高压走廊
    camera.position.set(48, 1.72, 58)
    controls.target.set(0, 42, PATROL_CORRIDOR_Z0)
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
      camera.position.set(PATROL_AERIAL_CAMERA.x, PATROL_AERIAL_CAMERA.y, PATROL_AERIAL_CAMERA.z)
      controls.target.set(PATROL_SCENE_LOOK.x, PATROL_SCENE_LOOK.y, PATROL_SCENE_LOOK.z)
    }
    controls.minDistance = 35
    controls.maxDistance = 900
    controls.minPolarAngle = 0
    controls.maxPolarAngle = Math.PI * 0.49
  }
  controls.update()
}

function getActiveWorldScene(): { scene: THREE.Scene; world: THREE.Group } | null {
  const tab = sceneTab.value
  if (tab === 'patrol' && sceneBundle) return { scene: sceneBundle.scene, world: sceneBundle.world }
  if (tab === 'substation' && substationBundle) return { scene: substationBundle.scene, world: substationBundle.world }
  if (tab === 'thermal' && thermalBundle) return { scene: thermalBundle.scene, world: thermalBundle.world }
  return null
}

function rebindEditor3dWorld(): void {
  const ed = sceneEditor3dRef.value
  if (!ed) return
  ed.unbindWorld()
  const ctx = getActiveWorldScene()
  if (ctx) ed.bindWorld(ctx.world, ctx.scene)
}

function applyEditorOrbitStyle(on: boolean): void {
  if (!controls) return
  if (on) {
    controls.mouseButtons.LEFT = THREE.MOUSE.PAN
    controls.mouseButtons.RIGHT = THREE.MOUSE.ROTATE
    controls.mouseButtons.MIDDLE = THREE.MOUSE.DOLLY
  } else {
    controls.mouseButtons.LEFT = THREE.MOUSE.ROTATE
    controls.mouseButtons.RIGHT = THREE.MOUSE.PAN
    controls.mouseButtons.MIDDLE = THREE.MOUSE.DOLLY
  }
}

async function pullCloudFieldDevicesToScene() {
  if (!sceneEditor3dRef.value) return
  try {
    const n = await applyCloudFieldDevicesToEditor(sceneEditor3dRef.value, sceneTab.value)
    if (n > 0) {
      fieldDeviceSyncHint.value = `已从管理平台加载 ${n} 个现场设备到场景`
    }
  } catch (e) {
    console.warn('[field-device] pull', e)
  }
}

function onFieldDeviceChannelMessage(ev: MessageEvent<FieldDeviceChannelMessage>) {
  const data = ev.data
  if (!data?.type || !sceneEditor3dRef.value) return
  if (data.type === 'UPSERT' && data.device) {
    if (applySingleCloudDevice(sceneEditor3dRef.value, data.device)) {
      fieldDeviceSyncHint.value = `场景已添加/更新：${data.device.deviceName}`
      scheduleFieldDeviceSync()
    }
  } else if (data.type === 'DELETE' && data.sceneObjectId) {
    removeCloudDeviceFromEditor(sceneEditor3dRef.value, data.sceneObjectId)
    fieldDeviceSyncHint.value = '已按管理平台删除移除场景物体'
  } else if (data.type === 'RELOAD') {
    void pullCloudFieldDevicesToScene()
  }
}

function scheduleFieldDeviceSync() {
  if (!sceneEditEnabled.value) return
  if (fieldDeviceSyncTimer) clearTimeout(fieldDeviceSyncTimer)
  fieldDeviceSyncTimer = setTimeout(() => {
    void syncFieldDevicesFromEditor(sceneEditor3dRef.value, sceneTab.value)
      .then((n) => {
        fieldDeviceSyncHint.value =
          n > 0 ? `已同步 ${n} 个现场设备到管理平台` : '现场设备已与管理平台一致'
        const mapId = sceneTab.value === 'patrol' ? 1 : sceneTab.value === 'substation' ? 2 : 3
        postFieldDeviceChannel({ type: 'RELOAD', mapId })
      })
      .catch((e) => {
        console.warn('[field-device] sync', e)
      })
  }, 1200)
}

watch(editorUiState, () => scheduleFieldDeviceSync(), { deep: true })

watch(sceneEditEnabled, (on) => {
  sceneObjectEditor?.setEnabled(!on)
  sceneEditor3dRef.value?.setActive(on)
  if (on) rebindEditor3dWorld()
  applyEditorOrbitStyle(on)
}, { immediate: true })

watch(sceneTab, () => {
  void pullCloudFieldDevicesToScene()
})

watch(viewMode, (mode, prev) => {
  applyViewMode(prev)
})

function ensureSubstationBundle(): void {
  if (!renderer || substationBundle || substationLoadFailed) return
  try {
    substationBundle = createSubstationScene(renderer)
    applySceneState(substationBundle.world, 'substation')
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
    camera.position.set(PATROL_AERIAL_CAMERA.x, PATROL_AERIAL_CAMERA.y, PATROL_AERIAL_CAMERA.z)
    controls.target.set(PATROL_SCENE_LOOK.x, PATROL_SCENE_LOOK.y, PATROL_SCENE_LOOK.z)
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
    controls.maxDistance = 900
    controls.minPolarAngle = 0
    controls.maxPolarAngle = Math.PI * 0.49
  }
  controls.update()
}

function ensureEdgeCloudReporter(laneIndex: number): EdgeCloudTelemetryReporter {
  const uavId = activeMissionMeta.value?.uavId ?? routeFetchUavId.value ?? 1
  const taskId = activeMissionMeta.value?.taskId
  const mapId = activeMissionMeta.value?.mapId
  if (!edgeCloudReporters[laneIndex]) {
    edgeCloudReporters[laneIndex] = new EdgeCloudTelemetryReporter({ uavId, taskId, mapId })
  } else {
    edgeCloudReporters[laneIndex]!.updateContext({ uavId, taskId, mapId })
  }
  edgeCloudReporters[laneIndex]!.setOnline(!simulateDisconnect.value)
  return edgeCloudReporters[laneIndex]!
}

function applyNetworkSim() {
  const online = !simulateDisconnect.value
  stateReports.forEach((s) => s.setOnline(online))
  edgeCloudReporters.forEach((r) => r?.setOnline(online))
}

function getBatteryForCheck() {
  return simulateLowBattery.value ? 15 : batteryPercent.value
}

function getRtkForCheck() {
  return simulateRtkLost.value ? 0 : 2
}

function runtimeFaultOpts() {
  if (simulateDisconnect.value) {
    return { faultStatus: 'FAULT', faultMessage: '通信中断' }
  }
  if (simulateLowBattery.value) {
    return { faultStatus: 'WARNING', faultMessage: '低电量' }
  }
  return { faultStatus: 'NONE' as const }
}

function onTelemetry(t: TelemetryPayload) {
  telemetry.value = t
  offlineBufferHint.value = stateReports.reduce((a, s) => a + s.getBufferedCount(), 0)
  if (sceneTab.value !== 'patrol' || !inspectionInFlight.value) return
  const uavId = patrolFleetUavIds[0] ?? routeFetchUavId.value ?? 1
  const online = !simulateDisconnect.value
  reportRobotPosition10Hz(uavId, t, online, runtimeFaultOpts())
  const enduranceMin = Math.max(1, Math.round((t.batteryPercent / 12) * 10))
  reportRobotBattery1Hz(uavId, t.batteryPercent, enduranceMin)
}

/** 输电场景机队心跳：待机也上报，供调度中枢感知在线终端（任务规划/拍卖分配） */
function reportPatrolFleetPresence() {
  if (sceneTab.value !== 'patrol' || patrolDrones.length < 1) return
  const online = !simulateDisconnect.value
  const fault = runtimeFaultOpts()
  patrolDrones.forEach((d, i) => {
    const uavId = patrolFleetUavIds[i] ?? i + 1
    const p = d.root.position
    const telem: TelemetryPayload = {
      t: Date.now(),
      position: { x: p.x, y: p.y, z: p.z },
      altitudeM: p.y,
      batteryPercent: batteryPercent.value,
      speedMps: 0,
      rtkMode: getRtkForCheck(),
      missionProgress: 0,
      phase: 'STANDBY'
    }
    reportRobotPresenceHeartbeat(uavId, telem, online, fault)
    const enduranceMin = Math.max(1, Math.round((telem.batteryPercent / 12) * 10))
    reportRobotBattery1Hz(uavId, telem.batteryPercent, enduranceMin)
  })
}

function notifyParent(payload: Record<string, unknown>) {
  if (window.parent !== window) {
    window.parent.postMessage(payload, '*')
  }
}

function onStatus(s: string) {
  taskStatus.value = s
  notifyParent({ type: MSG_INSPECTION_STATUS, status: s })
}

function notifyInspectionAnomaly(ev: InspectionAnomalyPayload) {
  notifyParent({ type: MSG_INSPECTION_ANOMALY, anomaly: ev })
}

function onPhoto() {
  offlineBufferHint.value = stateReports.reduce((a, s) => a + s.getBufferedCount(), 0)
}

async function finalizeMissionReport(r: MissionReport): Promise<MissionReport> {
  const report: MissionReport = { ...r, multimodalSamples: r.multimodalSamples ?? [] }
  const ctx = activeMissionMeta.value ?? (routeFetchUavId.value != null ? { uavId: routeFetchUavId.value } : null)
  if (!ctx) {
    report.multimodalUpload = { error: '缺少 uavId 任务上下文，未上报' }
    return report
  }
  if (!report.multimodalSamples.length) {
    return report
  }
  if (simulateDisconnect.value) {
    report.multimodalUpload = { error: '断网模拟中，多模态结果未上报云端' }
    return report
  }
  try {
    taskStatus.value = '正在上报多模态巡检结果至 iot-data…'
    const res = await uploadMultimodalMissionResult(report, ctx)
    report.multimodalUpload = res
    ElMessage.success(`多模态数据已入库（session ${res.sessionId}，${res.sampleCount} 条）`)
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    report.multimodalUpload = { error: msg }
    ElMessage.error(`多模态上报失败: ${msg}`)
  }
  return report
}

async function onComplete(r: MissionReport) {
  inspectionInFlight.value = false
  lastReport.value = await finalizeMissionReport(r)
  reportOpen.value = true
  missionJson.value = missionRunners[0]?.getDjiMissionPreview() ?? missionJson.value
  notifyParent({
    type: MSG_INSPECTION_MISSION_COMPLETE,
    summary: {
      durationSec: r.durationSec,
      distanceM: r.distanceM,
      photoCount: r.photos.length,
      telemetrySent: r.telemetrySent,
      multimodalUploaded: !!lastReport.value?.multimodalUpload && !lastReport.value.multimodalUpload.error,
      multimodalError: lastReport.value?.multimodalUpload?.error
    },
    missionReport: slimMissionReport(lastReport.value ?? r)
  })
}

function onError(e: Error) {
  inspectionInFlight.value = false
  ElMessage.error(e.message)
  taskStatus.value = '异常终止'
  nest?.setDoorTarget(0)
  notifyParent({ type: MSG_INSPECTION_MISSION_ERROR, message: e.message })
}

function ensureThermalPlantBundle(): void {
  if (!renderer || thermalBundle || thermalLoadFailed) return
  try {
    thermalBundle = createThermalPlantScene(renderer)
    robotDog = new RobotDogModel()
    robotDog.root.userData.noScenePick = true
    robotDog.setPose(thermalBundle.homePosition.clone(), 0)
    thermalBundle.world.add(robotDog.root)
    applySceneState(thermalBundle.world, 'thermal')
  } catch (e) {
    console.error('[thermal]', e)
    thermalLoadFailed = true
    ElMessage.error('火电站场景初始化失败，请查看控制台')
  }
}

function applyThermalCamera() {
  if (!camera || !controls) return
  camera.fov = 52
  camera.updateProjectionMatrix()
  camera.position.set(46, 34, 36)
  controls.target.set(5, 3, 0)
  controls.minDistance = 8
  controls.maxDistance = 120
  controls.minPolarAngle = 0.12
  controls.maxPolarAngle = Math.PI * 0.48
  controls.update()
}

function rebuildMissionRunner() {
  missionRunners.forEach((m) => m.dispose())
  missionRunners = []
  patrolFleetBuffer.value = []
  patrolNestLanded = 0
  if (!activeMissionMeta.value && routeFetchUavId.value != null) {
    activeMissionMeta.value = { uavId: routeFetchUavId.value }
  }
  if (!stateReports.length) return
  if (sceneTab.value === 'patrol' && sceneBundle && patrolDrones.length) {
    // 有 Agent 派单方案时仅出动被选中的机；否则（手动仿真）默认全部机从各自归位点起飞
    const maxLanes = Math.min(patrolDrones.length, stateReports.length)
    const entries: { drone: M300DroneModel; home: THREE.Vector3; path: CloudPathPoint[] }[] =
      fleetPlan.length > 0
        ? fleetPlan.slice(0, stateReports.length).map((e) => ({ drone: e.drone, home: e.home.clone(), path: e.path }))
        : patrolDrones.slice(0, maxLanes).map((d, i) => ({
            drone: d,
            home: (patrolHomes[i] ?? sceneBundle!.corridorHomes[i] ?? d.root.position).clone(),
            path: []
          }))
    const n = entries.length
    const onPatrolFleetComplete = async (r: MissionReport) => {
      patrolFleetBuffer.value.push(r)
      if (patrolFleetBuffer.value.length >= n) {
        inspectionInFlight.value = false
        const merged = mergePatrolReports(patrolFleetBuffer.value)
        patrolFleetBuffer.value = []
        lastReport.value = await finalizeMissionReport(merged)
        reportOpen.value = true
        missionJson.value = missionRunners.map((mr) => mr.getDjiMissionPreview()).join('\n---\n')
        notifyParent({
          type: MSG_INSPECTION_MISSION_COMPLETE,
          summary: {
            durationSec: merged.durationSec,
            distanceM: merged.distanceM,
            photoCount: merged.photos.length,
            telemetrySent: merged.telemetrySent,
            multimodalUploaded: !!lastReport.value?.multimodalUpload && !lastReport.value.multimodalUpload.error,
            multimodalError: lastReport.value?.multimodalUpload?.error
          },
          missionReport: slimMissionReport(lastReport.value ?? merged)
        })
      }
    }
    for (let i = 0; i < n; i++) {
      const entry = entries[i]!
      missionRunners.push(
        new MissionRunner({
          agent: entry.drone,
          pathWorld: sceneBundle.world,
          stateReport: stateReports[i]!,
          home: entry.home.clone(),
          getDeployMode: () => deployMode.value,
          getBattery: getBatteryForCheck,
          setBattery: i === 0 ? (v) => { batteryPercent.value = Math.round(v * 10) / 10 } : () => {},
          getRtkMode: getRtkForCheck,
          onStatus: i === 0 ? onStatus : () => {},
          onTelemetry: i === 0 ? onTelemetry : () => {},
          onCloudReport: (t) => {
            ensureEdgeCloudReporter(i).handleTelemetry(t)
            if (i === 0) cloudReceiveCount.value++
          },
          onPhoto: (_p, _ai) => {
            onPhoto()
          },
          onAnomaly: (ev) => {
            notifyInspectionAnomaly({
              faultType: ev.faultType,
              towerIndex: ev.towerIndex,
              waypointIndex: ev.waypointIndex,
              confidence: ev.confidence,
              description: ev.description,
              aiLabel: ev.aiLabel
            })
          },
          onComplete: onPatrolFleetComplete,
          onError,
          fetchPlannedPath: async (dep) => {
            if (entry.path.length) {
              return entry.path.map((p) => ({ ...p }))
            }
            if (i === 0 && cloudPatrolPath.value?.length) {
              return cloudPatrolPath.value.map((p) => ({ ...p }))
            }
            return fetchCloudPlannedPath(dep, i)
          },
          pathMode: entry.path.length || cloudPatrolPath.value?.length ? 'linear' : 'catmullrom',
          visualHooks: {
            onPreflightPassed: () => {
              if (i === 0) nest?.setDoorTarget(1)
            },
            onMissionEnded: () => {
              patrolNestLanded++
              if (patrolNestLanded >= n) {
                nest?.setDoorTarget(0)
                patrolNestLanded = 0
              }
            }
          },
          captureInspectionPhoto: () => {
            const sc = sceneBundle?.scene
            const agent = entry.drone
            if (!sc || !agent) return null
            const rig = agent.getInspectionViewRig?.()
            if (!rig) return null
            return captureSceneFromInspectionRig(sc, rig, { hideRoots: [agent.root] })
          }
        })
      )
    }
  } else if (sceneTab.value === 'thermal' && thermalBundle && robotDog) {
    missionRunners.push(
      new MissionRunner({
        agent: robotDog,
        pathWorld: thermalBundle.world,
        stateReport: stateReports[0]!,
        home: thermalBundle.homePosition.clone(),
        getDeployMode: () => deployMode.value,
        getBattery: getBatteryForCheck,
        setBattery: (v) => {
          batteryPercent.value = Math.round(v * 10) / 10
        },
        getRtkMode: getRtkForCheck,
        onStatus,
        onTelemetry,
        onCloudReport: (t) => {
          ensureEdgeCloudReporter(0).handleTelemetry(t)
          cloudReceiveCount.value++
        },
        onPhoto: (_p, _ai) => {
          onPhoto()
        },
        onAnomaly: (ev) => {
          notifyInspectionAnomaly({
            faultType: ev.faultType,
            towerIndex: ev.towerIndex,
            waypointIndex: ev.waypointIndex,
            confidence: ev.confidence,
            description: ev.description,
            aiLabel: ev.aiLabel
          })
        },
        onComplete,
        onError,
        visualHooks: buildVisualHooks(),
        fetchPlannedPath: fetchThermalPlantCloudPath,
        vehicleClass: 'ugv',
        djiAircraftId: 'QUADRUPED_INSPECTION',
        captureInspectionPhoto: () => {
          const sc = thermalBundle?.scene
          if (!sc || !robotDog) return null
          const rig = robotDog.getInspectionViewRig()
          return captureSceneFromInspectionRig(sc, rig, { hideRoots: [robotDog.root] })
        }
      })
    )
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
    } else if (tab === 'patrol' && sceneBundle) {
      void setupPatrolFleet(sceneBundle.scene, sceneBundle.corridorHomes)
      return
    }
    rebuildMissionRunner()
    sceneObjectEditor?.rebindToActiveScene()
    rebindEditor3dWorld()
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

async function setupPatrolFleet(scene: THREE.Scene, corridorHomes: THREE.Vector3[]) {
  patrolRobotLabels.forEach((l) => l.dispose())
  patrolRobotLabels = []
  patrolDrones.forEach((d) => {
    scene.remove(d.root)
    d.dispose()
  })
  patrolDrones = []
  fleetPlan = []

  try {
    patrolRobotConfigs = await fetchRobotsBySceneType('patrol')
  } catch {
    patrolRobotConfigs = []
  }
  const fleet = patrolRobotConfigs.filter((r) => r.robotType === 'UAV')
  const count = fleet.length > 0 ? fleet.length : Math.max(1, corridorHomes.length)
  patrolFleetUavIds = fleet.length > 0 ? fleet.map((r) => r.id) : [1]
  const savedHomes = loadSavedPatrolHomes()

  // 上报服务数量需匹配实际机队规模（机巢几何仅 2 位，但云端可配置更多无人机），
  // 否则编队派单会被 stateReports.length 截断、出动架数上不去。
  while (stateReports.length < count) {
    const sr = new StateReportService()
    sr.setOnline(!simulateDisconnect.value)
    stateReports.push(sr)
  }

  for (let i = 0; i < count; i++) {
    const d = new M300DroneModel()
    void d.tryLoadExternalModel('/models/m300.glb')
    const robot = fleet[i]
    let pos = corridorHomes[Math.min(i, corridorHomes.length - 1)]!.clone()
    if (robot?.sceneX != null && robot.sceneY != null && robot.sceneZ != null) {
      pos = new THREE.Vector3(robot.sceneX, robot.sceneY, robot.sceneZ)
    }
    // 优先恢复用户上次拖动保存的位置，保证巡检从当前位置出发
    const saved = savedHomes[i]
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y) && Number.isFinite(saved.z)) {
      pos = new THREE.Vector3(saved.x, saved.y, saved.z)
    }
    d.setPose(pos, 0)
    scene.add(d.root)
    d.root.userData.noScenePick = true
    patrolDrones.push(d)
    if (robot) {
      patrolRobotLabels.push(attachRobotLabel(d.root, robot))
    }
  }
  patrolHomes = patrolDrones.map((d) => d.root.position.clone())
  rebuildMissionRunner()
}

function initThree(): () => void {
  const canvas = canvasRef.value
  if (!canvas) return () => {}

  const box0 = canvasWrapperRef.value ?? canvas.parentElement
  const w = Math.max(1, Math.floor(box0?.clientWidth ?? canvas.clientWidth))
  const h = Math.max(1, Math.floor(box0?.clientHeight ?? canvas.clientHeight))

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  /** 勿用 setSize 默认 updateStyle：会把 canvas 锁死在首帧的 px 上，侧栏出现后易出现「半屏黑」 */
  renderer.setSize(w, h, false)
  canvas.style.width = '100%'
  canvas.style.height = '100%'
  canvas.style.display = 'block'
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 0.92
  renderer.outputColorSpace = THREE.SRGBColorSpace

  sceneBundle = createPowerlineScene(renderer)
  const { scene, world, homePosition, terminalPosition, corridorHomes, dispose: disposeScene } = sceneBundle
  syncTowerFireVisuals(world, listFireEnabledTowers())

  const laneN = corridorHomes.length
  stateReports = Array.from({ length: laneN }, () => new StateReportService())
  edgeCloudReporters = []

  void setupPatrolFleet(scene, corridorHomes)

  camera = new THREE.PerspectiveCamera(52, Math.max(0.01, w / h), 0.4, 2000)
  camera.position.set(PATROL_AERIAL_CAMERA.x, PATROL_AERIAL_CAMERA.y, PATROL_AERIAL_CAMERA.z)
  camera.lookAt(PATROL_SCENE_LOOK.x, PATROL_SCENE_LOOK.y, PATROL_SCENE_LOOK.z)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.target.set(PATROL_SCENE_LOOK.x, PATROL_SCENE_LOOK.y, PATROL_SCENE_LOOK.z)
  controls.minDistance = 35
  controls.maxDistance = 900
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

  nest.root.userData.noScenePick = true
  terminal.root.userData.noScenePick = true
  applySceneState(world, 'patrol')

  sceneEditor3dRef.value = new SceneEditor3D({
    canvas,
    camera,
    orbit: controls,
    getWorld: () => getActiveWorldScene()?.world ?? null,
    getScene: () => getActiveWorldScene()?.scene ?? null,
    getActiveTab: () => sceneTab.value,
    onUiChange: (s) => {
      editorUiState.value = s
    },
    onSceneEntityTransform: (obj) => {
      if (sceneTab.value !== 'patrol') return
      // 拖动无人机：记录其新位置为归位点，并持久化（巡检从此处出发、结束返回此处）
      const di = patrolDrones.findIndex((d) => d.root === obj)
      if (di >= 0) {
        patrolHomes[di] = patrolDrones[di]!.root.position.clone()
        savePatrolHomes()
        return
      }
      // 移动/旋转/缩放杆塔后，按其当前挂点重建导线（合帧，避免每次 change 都重建）
      if (obj.userData?.patrolTower !== true) return
      if (wireRebuildScheduled) return
      wireRebuildScheduled = true
      requestAnimationFrame(() => {
        wireRebuildScheduled = false
        sceneBundle?.rebuildPowerlineWires()
      })
    }
  })
  sceneEditor3dRef.value.mountInputHandlers()

  sceneObjectEditor = new SceneObjectEditor(
    canvas,
    camera,
    controls,
    () => {
      const tab = sceneTab.value
      if (tab === 'patrol' && sceneBundle) return { scene: sceneBundle.scene, world: sceneBundle.world }
      if (tab === 'substation' && substationBundle) return { scene: substationBundle.scene, world: substationBundle.world }
      if (tab === 'thermal' && thermalBundle) return { scene: thermalBundle.scene, world: thermalBundle.world }
      return null
    },
    (snap) => {
      sceneEditorSnap.value = snap
    },
    (obj) => {
      const tab = sceneTab.value
      const w =
        tab === 'patrol' && sceneBundle
          ? sceneBundle.world
          : tab === 'substation' && substationBundle
            ? substationBundle.world
            : tab === 'thermal' && thermalBundle
              ? thermalBundle.world
              : null
      if (!w) return
      appendRemovalAndPersist(tab as ScenePersistTab, w, getStablePathToWorld(obj, w))
    }
  )
  sceneObjectEditor.setEnabled(!sceneEditEnabled.value)
  sceneObjectEditor.rebindToActiveScene()

  rebindEditor3dWorld()
  sceneEditor3dRef.value.setActive(sceneEditEnabled.value)
  applyEditorOrbitStyle(sceneEditEnabled.value)
  void pullCloudFieldDevicesToScene()

  applyNetworkSim()

  rebuildMissionRunner()

  edgeSim = createEdgeMetricsSimulator(() => !simulateDisconnect.value)

  const clock = new THREE.Clock()
  const tick = () => {
    rafMain = requestAnimationFrame(tick)
    const dt = clock.getDelta()
    nest?.tick(dt)
    tickTowerFireVisuals(dt)
    edgeMetrics.value = edgeSim.tick(dt)
    if (sceneTab.value === 'patrol' && patrolFleetUavIds.length && !inspectionInFlight.value) {
      reportPatrolFleetPresence()
      if (inspectionInFlight.value) {
        patrolFleetUavIds.forEach((uavId, i) => {
          const mem = edgeMetrics.value.storagePercent
          const tasks = i === 0 && activeMissionMeta.value?.taskId ? 1 : 0
          reportRobotLoad1Hz(uavId, edgeMetrics.value.cpuPercent, mem, tasks)
        })
      }
    }
    patrolDrones.forEach((d) => d.tick(dt))
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
    } else if (sceneBundle) {
      // 防止未知 tab / 竞态导致整帧不 render（全黑）
      renderer.render(sceneBundle.scene, camera)
    }
  }
  tick()

  const onResize = () => {
    const box = canvasWrapperRef.value ?? canvasRef.value?.parentElement
    const cv = canvasRef.value
    if (!box || !cv || !renderer || !camera) return
    const rw = Math.max(1, Math.floor(box.clientWidth))
    const rh = Math.max(1, Math.floor(box.clientHeight))
    renderer.setSize(rw, rh, false)
    cv.style.width = '100%'
    cv.style.height = '100%'
    cv.style.display = 'block'
    camera.aspect = rw / rh
    camera.updateProjectionMatrix()
  }
  window.addEventListener('resize', onResize)
  disposeResize = () => {
    window.removeEventListener('resize', onResize)
    canvasResizeObserver?.disconnect()
    canvasResizeObserver = null
  }

  canvasResizeObserver = new ResizeObserver(() => {
    onResize()
  })
  requestAnimationFrame(() => {
    const t = canvasWrapperRef.value ?? canvasRef.value?.parentElement
    if (t && canvasResizeObserver) canvasResizeObserver.observe(t)
    onResize()
  })

  return () => {
    disposeResize?.()
    disposeResize = null
    cancelAnimationFrame(rafMain)
    sceneEditor3dRef.value?.dispose()
    sceneEditor3dRef.value = null
    sceneObjectEditor?.dispose()
    sceneObjectEditor = null
    missionRunners.forEach((m) => m.dispose())
    missionRunners = []
    controls?.dispose()
    controls = null
    patrolRobotLabels.forEach((l) => l.dispose())
    patrolRobotLabels = []
    patrolDrones.forEach((d) => d.dispose())
    patrolDrones = []
    stateReports.forEach((s) => s.stop())
    stateReports = []
    nest?.dispose()
    nest = null
    terminal?.dispose()
    terminal = null
    disposeInspectionCaptureRenderer()
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
  }
}

let disposeThree: (() => void) | null = null

async function waitForPatrolSceneReady(maxMs = 4000): Promise<void> {
  if (sceneTab.value !== 'patrol') {
    sceneTab.value = 'patrol'
  }
  const deadline = Date.now() + maxMs
  while (!sceneBundle && Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 50))
  }
  if (!sceneBundle) {
    throw new Error('仿真场景尚未就绪，请稍后再试')
  }
}

/** 以无人机当前位置为起降点，构造「当前位置 → 爬升 → 各拍照点 → 返回」航线 */
function buildCloudPathFromHome(
  home: THREE.Vector3,
  photoPts: CloudPathPoint[]
): CloudPathPoint[] {
  const pts: CloudPathPoint[] = [{ id: 'wp-home', x: home.x, y: home.y, z: home.z, isPhoto: false }]
  if (photoPts.length === 0) return pts
  const cruiseY = Math.max(home.y + 5, ...photoPts.map((p) => p.y))
  pts.push({ id: 'wp-climb', x: home.x, y: cruiseY, z: home.z, isPhoto: false })
  photoPts.forEach((p, k) => pts.push({ ...p, id: `wp-photo-${k}`, isPhoto: true }))
  pts.push({ id: 'wp-rth', x: home.x, y: cruiseY, z: home.z, isPhoto: false })
  pts.push({ id: 'wp-land', x: home.x, y: home.y, z: home.z, isPhoto: false })
  return pts
}

/**
 * 根据任务实际工作量（设备数）就近调度无人机：
 * - 单个塔杆/设备 → 仅派距离最近的 1 架；多设备 → 在最近的若干架之间分摊。
 * 拍照点固定落在真实塔位（以机巢为锚点解算），每架机从“当前位置”出发并返回，避免回到初始位。
 */
function planFleetAssignments(
  dispatch: UavRouteDispatchPayload,
  recommendedFleet?: number
): typeof fleetPlan {
  if (!sceneBundle || patrolDrones.length === 0) return []
  // 锚点固定用原始机巢：经纬度→场景的换算基准，保证拍照点落在真实塔位（不随无人机被拖动而偏移）
  const anchor0 = sceneBundle.corridorHomes[0]!
  const worldPath = dispatchToCloudPath(dispatch, { x: anchor0.x, y: anchor0.y, z: anchor0.z })
  const photoPts = worldPath.filter((p) => p.isPhoto)

  // 工作量：按 deviceIds 去重得到设备数；缺失则退化为拍照点数
  const deviceIds = new Set<number>()
  for (const w of dispatch.photoWaypoints ?? []) {
    for (const id of w.deviceIds ?? []) if (id != null) deviceIds.add(id)
  }
  const workload = deviceIds.size > 0 ? deviceIds.size : Math.max(1, photoPts.length)
  const maxDrones = Math.min(patrolDrones.length, Math.max(1, stateReports.length))
  // 出动架数优先采用云端（LLM 决策）下发的 recommendedFleet；缺省再按工作量估算
  const desired =
    recommendedFleet && recommendedFleet >= 1 ? recommendedFleet : workload
  const dronesToUse = Math.max(1, Math.min(maxDrones, desired, Math.max(1, photoPts.length)))

  // 任务参考点：拍照点质心（无拍照点用整条航线质心）
  const refPts = photoPts.length ? photoPts : worldPath
  const ref = new THREE.Vector3()
  refPts.forEach((p) => ref.add(new THREE.Vector3(p.x, p.y, p.z)))
  ref.multiplyScalar(1 / Math.max(1, refPts.length))

  // 选距任务参考点最近的 dronesToUse 架
  const selected = patrolDrones
    .map((d, i) => ({ i, dist: d.root.position.distanceTo(ref) }))
    .sort((a, b) => a.dist - b.dist)
    .slice(0, dronesToUse)
    .map((r) => r.i)

  // 每个拍照点分给最近的被选中机（保持原规划顺序）
  const buckets = new Map<number, CloudPathPoint[]>()
  selected.forEach((i) => buckets.set(i, []))
  for (const p of photoPts) {
    const pv = new THREE.Vector3(p.x, p.y, p.z)
    let best = selected[0]!
    let bestD = Infinity
    for (const i of selected) {
      const d = patrolDrones[i]!.root.position.distanceTo(pv)
      if (d < bestD) {
        bestD = d
        best = i
      }
    }
    buckets.get(best)!.push(p)
  }

  const plan: typeof fleetPlan = []
  for (const i of selected) {
    const photos = buckets.get(i) ?? []
    if (photoPts.length > 0 && photos.length === 0) continue // 没分到拍照点的机不起飞
    const home = patrolDrones[i]!.root.position.clone()
    plan.push({ drone: patrolDrones[i]!, home, path: buildCloudPathFromHome(home, photos) })
  }
  if (plan.length === 0) {
    // 兜底：最近一架飞全部拍照点
    const i = selected[0] ?? 0
    const home = patrolDrones[i]!.root.position.clone()
    plan.push({ drone: patrolDrones[i]!, home, path: buildCloudPathFromHome(home, photoPts) })
  }
  return plan
}

async function applyDispatchFromParent(
  dispatch: UavRouteDispatchPayload,
  options?: { autoStart?: boolean; userInput?: string; recommendedFleet?: number }
) {
  if (inspectionInFlight.value) {
    resetMission()
  }
  await waitForPatrolSceneReady()
  activeMissionMeta.value = {
    uavId: dispatch.uavId,
    taskId: dispatch.taskId,
    mapId: dispatch.mapId
  }
  routeFetchUavId.value = dispatch.uavId
  routeFetchPlanId.value = dispatch.planId
  edgeCloudReporters.forEach((r) =>
    r?.updateContext({
      uavId: dispatch.uavId,
      taskId: dispatch.taskId,
      mapId: dispatch.mapId
    })
  )
  routeFetchRawJson.value = JSON.stringify(dispatch, null, 2)
  const dji = dispatchToDjiWaypointMission(dispatch, 'M300_RTK')
  missionJson.value = JSON.stringify(dji, null, 2)
  // 就近调度：架数优先采用云端 LLM 决策（recommendedFleet），再就近选机、单设备仅派最近 1 架
  fleetPlan = planFleetAssignments(dispatch, options?.recommendedFleet)
  cloudPatrolPath.value = fleetPlan[0]?.path ?? []
  const usedN = fleetPlan.length
  taskStatus.value =
    usedN > 1
      ? `Agent 就近编队 ${usedN} 架无人机协同巡检`
      : 'Agent 已派最近的 1 架无人机执行巡检'
  rebuildMissionRunner()
  const n = dji.waypoints.length
  const hint = options?.userInput ? `（${options.userInput.slice(0, 40)}…）` : ''
  taskStatus.value = `${taskStatus.value}｜共 ${n} 个航点${hint}`
  const totalWp = fleetPlan.length
    ? fleetPlan.reduce((s, e) => s + e.path.length, 0)
    : n
  notifyParent({
    type: MSG_INSPECTION_DISPATCH_ACK,
    waypointCount: totalWp,
    uavId: dispatch.uavId,
    planId: dispatch.planId,
    fleetCount: fleetPlan.length || 1
  })
  ElMessage.success(`已接收 Agent 巡检指令，出动 ${fleetPlan.length || 1} 架 / ${n} 个航点`)
  if (options?.autoStart !== false) {
    await startMission()
  }
}

function handleParentMessage(ev: MessageEvent) {
  const data = ev.data as InspectionDispatchMessage | undefined
  if (!data || data.type !== MSG_INSPECTION_DISPATCH || !data.dispatch) return
  void applyDispatchFromParent(data.dispatch, {
    autoStart: data.autoStart,
    userInput: data.userInput,
    recommendedFleet: data.recommendedFleet
  }).catch((e) => {
    const msg = e instanceof Error ? e.message : String(e)
    taskStatus.value = `Agent 下发失败: ${msg}`
    ElMessage.error(msg)
    notifyParent({ type: MSG_INSPECTION_MISSION_ERROR, message: msg })
  })
}

let fieldDeviceChannel: BroadcastChannel | null = null
let inspectionDispatchChannel: BroadcastChannel | null = null

onMounted(() => {
  disposeThree = initThree()
  window.addEventListener('message', handleParentMessage)
  try {
    fieldDeviceChannel = new BroadcastChannel(FIELD_DEVICE_CHANNEL)
    fieldDeviceChannel.onmessage = onFieldDeviceChannelMessage
  } catch {
    fieldDeviceChannel = null
  }
  try {
    inspectionDispatchChannel = new BroadcastChannel(INSPECTION_DISPATCH_CHANNEL)
    inspectionDispatchChannel.onmessage = (ev: MessageEvent<InspectionDispatchMessage>) => {
      const data = ev.data
      if (!data || data.type !== MSG_INSPECTION_DISPATCH || !data.dispatch) return
      void applyDispatchFromParent(data.dispatch, {
        autoStart: data.autoStart,
        userInput: data.userInput,
        recommendedFleet: data.recommendedFleet
      }).catch((e) => {
        const msg = e instanceof Error ? e.message : String(e)
        ElMessage.error(`扩范围复巡下发失败: ${msg}`)
      })
    }
  } catch {
    inspectionDispatchChannel = null
  }
})

onBeforeUnmount(() => {
  inspectionDispatchChannel?.close()
  inspectionDispatchChannel = null
  fieldDeviceChannel?.close()
  fieldDeviceChannel = null
  window.removeEventListener('message', handleParentMessage)
  disposeThree?.()
  disposeThree = null
})

async function startMission() {
  if (!missionRunners.length) {
    ElMessage.warning('当前场景不支持任务仿真（请切换到输电巡检或火电站）')
    return
  }
  if (!cloudPatrolPath.value?.length) {
    ElMessage.info('未加载云端路径，将使用内置演示航线（平滑曲线）')
  }
  applyNetworkSim()
  cloudReceiveCount.value = 0
  inspectionInFlight.value = true
  await Promise.all(missionRunners.map((m) => m.start()))
  missionJson.value = missionRunners.map((m) => m.getDjiMissionPreview()).join('\n---\n')
}

function resetMission() {
  inspectionInFlight.value = false
  missionRunners.forEach((m) => m.reset())
  batteryPercent.value = 96
  telemetry.value = null
  cloudReceiveCount.value = 0
  patrolFleetBuffer.value = []
  patrolNestLanded = 0
  nest?.setDoorTarget(0)
  if (sceneTab.value === 'patrol' && sceneBundle && patrolDrones.length) {
    patrolDrones.forEach((d, i) => {
      const h = patrolHomes[i] ?? sceneBundle!.corridorHomes[i]
      if (h) {
        d.setPose(h.clone(), 0)
        d.setGimbal(0, 0)
      }
    })
  }
  if (sceneTab.value === 'thermal' && thermalBundle && robotDog) {
    robotDog.setPose(thermalBundle.homePosition.clone(), 0)
    robotDog.setGimbal(0, 0)
  }
}

function togglePause() {
  if (!missionRunners.length) return
  const nextPaused = !missionRunners[0]!.isPaused()
  missionRunners.forEach((m) => m.setPaused(nextPaused))
  taskStatus.value = nextPaused ? '已暂停' : '自主巡检中（CatmullRom 平滑航线）'
}

watch(sceneEditorSnap, (s) => {
  if (!s) return
  editForm.x = s.x
  editForm.y = s.y
  editForm.z = s.z
  editForm.rotYdeg = s.rotYdeg
  editForm.sx = s.sx
  editForm.sy = s.sy
  editForm.sz = s.sz
})

function getPersistWorld(): THREE.Group | null {
  const t = sceneTab.value
  if (t === 'patrol' && sceneBundle) return sceneBundle.world
  if (t === 'substation' && substationBundle) return substationBundle.world
  if (t === 'thermal' && thermalBundle) return thermalBundle.world
  return null
}

async function saveSceneLayout() {
  const w = getPersistWorld()
  if (!w) {
    ElMessage.warning('当前场景尚未就绪')
    return
  }
  const tab = sceneTab.value as ScenePersistTab
  const prev = loadSceneState(tab)
  saveSceneState(tab, w, prev?.removedPaths ?? [])
  try {
    const n = await syncFieldDevicesFromEditor(sceneEditor3dRef.value, sceneTab.value)
    ElMessage.success(
      n > 0
        ? `场景已保存，并已同步 ${n} 个现场设备到管理平台`
        : '场景已保存到本机；编辑器内暂无新增设备需同步'
    )
  } catch {
    ElMessage.success('场景已保存到本机（现场设备同步失败，请检查 drone 服务）')
  }
}

function persistTransformsMerged() {
  const w = getPersistWorld()
  if (!w) return
  const tab = sceneTab.value as ScenePersistTab
  const prev = loadSceneState(tab)
  saveSceneState(tab, w, prev?.removedPaths ?? [])
}

async function onPersistCommand(cmd: 'current' | 'all') {
  try {
    const msg =
      cmd === 'all'
        ? '将清除输电巡检、变电站、火电站三个场景的本地修改，并刷新页面。是否继续？'
        : '将清除当前场景的本地修改，并刷新页面。是否继续？'
    await ElMessageBox.confirm(msg, '恢复默认', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    if (cmd === 'all') clearAllSceneStates()
    else clearSceneState(sceneTab.value as ScenePersistTab)
    location.reload()
  } catch {
    /* 用户取消 */
  }
}

function onSceneMenuCommand(cmd: string) {
  if (cmd === 'save') saveSceneLayout()
  else if (cmd === 'reset-current' || cmd === 'reset-all') void onPersistCommand(cmd === 'reset-current' ? 'current' : 'all')
}

function onViewModeCommand(cmd: string) {
  if (cmd === 'aerial' || cmd === 'ground') viewMode.value = cmd
}

function applySceneEditForm() {
  sceneObjectEditor?.applyFromForm({
    x: editForm.x,
    y: editForm.y,
    z: editForm.z,
    rotYdeg: editForm.rotYdeg,
    sx: editForm.sx,
    sy: editForm.sy,
    sz: editForm.sz
  })
  persistTransformsMerged()
}

function deleteSceneSelection() {
  if (!sceneEditorSnap.value) return
  sceneObjectEditor?.deleteSelected()
  ElMessage.success('已删除并写入本地保存。')
}

function clearSceneSelection() {
  sceneObjectEditor?.clearSelection()
}

async function pullRouteAndConvert() {
  if (routeFetchUavId.value == null || routeFetchPlanId.value == null) {
    ElMessage.warning('请输入无人机 ID 与路径规划 planId')
    return
  }
  routeFetchLoading.value = true
  try {
    taskStatus.value = '正在拉取智能巡检路径…'
    const dispatch = await fetchRouteDispatch(routeFetchUavId.value, routeFetchPlanId.value)
    await applyDispatchFromParent(dispatch, { autoStart: false })
    const src = dispatch.waypoints?.length ?? 0
    taskStatus.value = `已加载航点，请点击「启动巡检」`
    ElMessage.success(`已对齐后台 ${src} 个飞行点，预览线为折线（非平滑曲线）`)
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    taskStatus.value = `拉取失败: ${msg}`
    ElMessage.error(msg)
  } finally {
    routeFetchLoading.value = false
  }
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
  <div class="industrial-app flex h-full w-full min-h-0 flex-col border-t border-[var(--ia-border)] text-[#c8d4e0]">
    <header class="edit-toolbar-header shrink-0">
      <div class="edit-toolbar-row grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 px-3 py-1.5">
        <div class="toolbar-slot-left flex min-w-0 flex-wrap items-center gap-2 justify-self-start">
          <div class="ia-brand">
            <span class="ia-brand__dot" />
            <span class="ia-brand__name">RACCOON</span>
            <span class="ia-brand__sub">巡检仿真</span>
          </div>
          <div class="ia-btn-group">
            <el-dropdown trigger="click" class="font-mono" @command="onSceneMenuCommand">
              <el-button size="small" text class="ia-tb-btn">
                场景菜单 <span class="ml-0.5 opacity-60">▾</span>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="save">保存场景</el-dropdown-item>
                  <el-dropdown-item command="reset-current" divided>恢复默认（仅当前场景）…</el-dropdown-item>
                  <el-dropdown-item command="reset-all">恢复默认（全部场景）…</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <span class="ia-btn-group__sep" />
            <el-button size="small" text class="ia-tb-btn" @click="outlinerDrawerOpen = true">
              场景对象
            </el-button>
          </div>
        </div>
        <EditorToolbar :editor="sceneEditor3dRef" :ui="editorUiState" class="edit-toolbar-inner justify-self-center" />
        <div class="toolbar-slot-right hidden items-center justify-self-end sm:flex">
          <span
            class="ia-kbd-hint"
            title="Ctrl+Z / Ctrl+Y · Q 切换世界/局部 · WASD 平移视角 · Del 删除 · Ctrl 多选"
          >
            <kbd>Ctrl</kbd><kbd>Z</kbd> · <kbd>Q</kbd> · <kbd>WASD</kbd> · <kbd>Del</kbd>
          </span>
        </div>
      </div>
    </header>

    <el-drawer
      v-model="outlinerDrawerOpen"
      title="场景对象"
      direction="ttb"
      size="min(280px, 42vh)"
      class="ia-outliner-drawer"
      destroy-on-close
    >
      <EditorOutliner layout="drawer" :editor="sceneEditor3dRef" :ui="editorUiState" />
    </el-drawer>

    <div class="app-body flex min-h-0 flex-1 flex-col gap-0 md:flex-row">
      <aside class="app-left flex w-full min-h-0 shrink-0 flex-col md:w-[18rem]">
        <SimControlPanel
          v-model:scene-tab="sceneTab"
          v-model:view-mode="viewMode"
          v-model:deploy-mode="deployMode"
          v-model:simulate-disconnect="simulateDisconnect"
          v-model:simulate-low-battery="simulateLowBattery"
          v-model:simulate-rtk-lost="simulateRtkLost"
          v-model:fire-tower-flags="fireTowerFlags"
          v-model:route-fetch-uav-id="routeFetchUavId"
          v-model:route-fetch-plan-id="routeFetchPlanId"
          :edge-metrics="edgeMetrics"
          :patrol-tower-coord-rows="patrolTowerCoordRows"
          :route-fetch-loading="routeFetchLoading"
          :route-fetch-raw-json="routeFetchRawJson"
          :mission-json="missionJson"
          :role-label="roleLabel"
          class="min-h-0 flex-1"
          @apply-network="applyNetworkSim"
          @copy-tower-coord="copyTowerCoord"
          @copy-all-photo-coords="copyAllPhotoCoords"
          @pull-route="pullRouteAndConvert"
          @try-waypoint-limit="try65535Demo"
        />
      </aside>

      <main class="relative flex min-h-[40vh] min-w-0 flex-1 flex-col overflow-hidden bg-black">
        <div ref="canvasWrapperRef" class="relative min-h-0 min-w-0 flex-1 self-stretch">
          <canvas ref="canvasRef" class="absolute inset-0 block h-full w-full touch-none" />
          <div
            class="pointer-events-none absolute bottom-2 left-2 z-10 max-w-[min(96%,28rem)] rounded border border-[var(--ia-border)] bg-black/70 px-2 py-1 font-mono text-[10px] text-[var(--ia-muted)]"
          >
            {{ viewHint }}
          </div>
        </div>
      </main>

      <aside class="app-right flex w-full min-h-0 shrink-0 flex-col md:w-[18rem]">
        <EditorProperties :editor="sceneEditor3dRef" :ui="editorUiState" class="min-h-0 flex-[1_1_46%]" />

        <div class="drone-control-panel flex min-h-0 flex-[1_1_54%] flex-col gap-3 overflow-y-auto border-t border-[var(--ia-border)] bg-[var(--ia-panel)] p-3">
          <div class="ia-panel-head">
            <span class="ia-panel-head__bar" />
            <div>
              <div class="ia-panel-head__title">无人机控制</div>
              <p class="ia-panel-head__desc">任务与遥测 · 场景参数见左侧栏</p>
            </div>
          </div>

          <el-card v-if="sceneTab === 'patrol' || sceneTab === 'thermal'" shadow="never" class="ia-card">
            <template #header>任务控制</template>
            <div class="flex flex-wrap gap-2">
              <el-button type="primary" size="small" class="!font-mono" @click="startMission">启动巡检</el-button>
              <el-button size="small" class="!font-mono" @click="togglePause">暂停 / 继续</el-button>
              <el-button size="small" class="!font-mono" @click="resetMission">重置</el-button>
            </div>
          </el-card>
          <el-card v-else shadow="never" class="ia-card">
            <template #header>任务控制</template>
            <p class="font-mono text-[10px] leading-relaxed text-[var(--ia-muted)]">当前为变电站浏览场景，无航线任务仿真。</p>
          </el-card>

          <div class="ia-section-label">遥测</div>
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
            <template #header>云端轨迹入库</template>
            <p class="font-mono text-[10px] text-[var(--ia-muted)]">
              {{ 1000 / TELEMETRY_INTERVAL_MS }} Hz → uav_location_history
            </p>
            <p class="font-mono text-sm">已入库 <b class="text-[var(--ia-accent)]">{{ cloudReceiveCount }}</b> 条</p>
            <p class="font-mono text-[10px] text-amber-600/90">边缘缓存 {{ offlineBufferHint }}</p>
          </el-card>
          <el-card v-if="sceneTab === 'patrol' || sceneTab === 'thermal'" shadow="never" class="ia-card">
            <template #header>任务状态</template>
            <p class="mb-2 font-mono text-[11px] leading-relaxed text-[var(--ia-muted)]">{{ taskStatus }}</p>
          </el-card>
        </div>
      </aside>
    </div>


    <el-dialog v-model="reportOpen" title="巡检报告" class="ia-dialog" width="min(92vw, 760px)" destroy-on-close>
      <template v-if="lastReport">
        <el-descriptions :column="2" border size="small" class="mb-3 font-mono">
          <el-descriptions-item label="时长 / s">{{ lastReport.durationSec.toFixed(1) }}</el-descriptions-item>
          <el-descriptions-item label="距离 / m">{{ lastReport.distanceM.toFixed(1) }}</el-descriptions-item>
          <el-descriptions-item label="照片">{{ lastReport.photos.length }}</el-descriptions-item>
          <el-descriptions-item label="多模态采样">{{ lastReport.multimodalSamples?.length ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="遥测条数">{{ lastReport.telemetrySent }}</el-descriptions-item>
          <el-descriptions-item label="云端入库">
            <span v-if="lastReport.multimodalUpload && 'sessionId' in lastReport.multimodalUpload">
              session {{ lastReport.multimodalUpload.sessionId }}（{{ lastReport.multimodalUpload.sampleCount }} 条）
            </span>
            <span v-else-if="lastReport.multimodalUpload && 'error' in lastReport.multimodalUpload" class="text-amber-600">
              {{ lastReport.multimodalUpload.error }}
            </span>
            <span v-else>—</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="mb-2 text-[10px] text-[var(--ia-muted)]">
          每个拍照航点采集可见光、热成像、声音、振动、温度；任务结束后批量上报 raccoon-iot-data。
        </div>
        <el-table :data="reportTableRows" stripe size="small" max-height="360" class="font-mono">
          <el-table-column label="巡检画面" width="156" align="center">
            <template #default="{ row }">
              <el-image
                v-if="row.thumb"
                :src="row.thumb"
                :preview-src-list="[row.thumb]"
                fit="cover"
                class="report-thumb"
                preview-teleported
              />
              <span v-else class="text-[var(--ia-muted)]">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="id" label="日志 ID" min-width="120" show-overflow-tooltip />
          <el-table-column prop="wp" label="WP" width="48" />
          <el-table-column prop="ai" label="AI" min-width="100" />
          <el-table-column prop="defect" label="结论" width="88" />
        </el-table>

        <div v-if="multimodalTableRows.length" class="mt-4 mb-2 text-[11px] font-semibold uppercase tracking-wider text-[var(--ia-muted)]">
          多模态巡检数据
        </div>
        <el-table
          v-if="multimodalTableRows.length"
          :data="multimodalTableRows"
          stripe
          size="small"
          max-height="280"
          class="font-mono"
        >
          <el-table-column label="预览" width="88" align="center">
            <template #default="{ row }">
              <el-image
                v-if="row.preview"
                :src="row.preview"
                fit="cover"
                class="report-thumb"
                preview-teleported
              />
              <span v-else class="text-[var(--ia-muted)]">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="wp" label="WP" width="44" />
          <el-table-column prop="modality" label="模态" width="72" />
          <el-table-column prop="summary" label="摘要" min-width="120" />
          <el-table-column prop="id" label="采样 ID" min-width="140" show-overflow-tooltip />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ---------- 顶部栏 ---------- */
.edit-toolbar-header {
  background: linear-gradient(180deg, #0c1622 0%, #0a121c 100%);
  border-bottom: 1px solid var(--ia-border);
  box-shadow: 0 1px 0 rgba(56, 167, 214, 0.06), 0 4px 16px rgba(0, 0, 0, 0.35);
}

.ia-brand {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding-right: 10px;
  margin-right: 2px;
  border-right: 1px solid var(--ia-border-soft);
  user-select: none;
}
.ia-brand__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ia-accent);
  box-shadow: 0 0 8px var(--ia-accent);
  align-self: center;
}
.ia-brand__name {
  font-family: ui-monospace, monospace;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #e7f1f8;
}
.ia-brand__sub {
  font-family: ui-monospace, monospace;
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--ia-muted);
}

.ia-btn-group {
  display: inline-flex;
  align-items: center;
  padding: 2px;
  border: 1px solid var(--ia-border-soft);
  border-radius: var(--ia-radius-sm);
  background: rgba(255, 255, 255, 0.015);
}
.ia-btn-group__sep {
  width: 1px;
  height: 16px;
  background: var(--ia-border-soft);
  margin: 0 2px;
}
:deep(.ia-tb-btn) {
  height: 26px;
  padding: 0 10px;
  font-family: ui-monospace, monospace;
  font-size: 11px;
  color: var(--ia-text);
  border-radius: 4px;
}
:deep(.ia-tb-btn:hover) {
  background: var(--ia-accent-soft);
  color: #eaf4fb;
}

.ia-kbd-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: ui-monospace, monospace;
  font-size: 9px;
  color: var(--ia-muted);
}
.ia-kbd-hint kbd {
  display: inline-block;
  padding: 1px 5px;
  font-size: 9px;
  line-height: 1.4;
  color: #b8cad9;
  background: #0c1722;
  border: 1px solid var(--ia-border);
  border-bottom-width: 2px;
  border-radius: 4px;
}

/* ---------- 侧栏外框 ---------- */
.app-left {
  background: linear-gradient(180deg, var(--ia-panel-2) 0%, var(--ia-panel) 100%);
  border-right: 1px solid var(--ia-border);
}
.app-right {
  background: linear-gradient(180deg, var(--ia-panel-2) 0%, var(--ia-panel) 100%);
  border-left: 1px solid var(--ia-border);
}

/* ---------- 分区标题 ---------- */
.ia-panel-head {
  display: flex;
  align-items: stretch;
  gap: 8px;
}
.ia-panel-head__bar {
  width: 3px;
  border-radius: 3px;
  background: linear-gradient(180deg, var(--ia-accent), rgba(56, 167, 214, 0.25));
  flex-shrink: 0;
}
.ia-panel-head__title {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #e7f1f8;
}
.ia-panel-head__desc {
  margin-top: 2px;
  font-family: ui-monospace, monospace;
  font-size: 9px;
  line-height: 1.3;
  color: var(--ia-muted);
}

.ia-section-label {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: ui-monospace, monospace;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--ia-muted);
  margin-top: 2px;
}
.ia-section-label::after {
  content: '';
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--ia-border), transparent);
}

/* ---------- 卡片 ---------- */
.ia-card {
  --el-card-bg-color: var(--ia-elev);
  --el-card-border-color: var(--ia-border-soft);
  border-radius: var(--ia-radius-sm);
  overflow: hidden;
  transition: border-color 0.18s ease;
}
.ia-card:hover {
  --el-card-border-color: var(--ia-border);
}
:deep(.ia-card .el-card__header) {
  padding: 7px 11px;
  font-size: 11px;
  font-family: ui-monospace, monospace;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ia-muted);
  background: rgba(56, 167, 214, 0.04);
  border-bottom: 1px solid var(--ia-border-soft);
}
:deep(.ia-card .el-card__body) {
  padding: 10px 11px;
}

:deep(.ia-card.ia-card-deploy-tight .el-card__header) {
  padding: 4px 8px;
}
:deep(.ia-card.ia-card-deploy-tight .el-card__body) {
  padding: 6px 8px 7px;
}

.ia-radio-tight :deep(.el-radio) {
  margin-right: 0;
  height: auto;
  line-height: 1.25;
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

:deep(.ia-collapse-json .el-collapse-item__header) {
  min-height: 28px;
  height: 28px;
  line-height: 28px;
  padding: 0 8px;
  font-size: 10px;
}
:deep(.ia-collapse-json .el-collapse-item__wrap) {
  border-top: 1px solid var(--ia-border);
}
:deep(.ia-collapse-json .el-collapse-item__content) {
  padding: 6px 8px;
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
.report-thumb {
  width: 132px;
  height: 74px;
  border-radius: 2px;
  border: 1px solid var(--ia-border);
}
.report-thumb :deep(.el-image__inner) {
  width: 132px;
  height: 74px;
}
:deep(.scene-tab-rg .el-radio-button__inner) {
  font-family: ui-monospace, monospace;
  font-size: 11px;
  padding: 5px 10px;
}

:deep(.ia-toolbar-divider.el-divider--vertical) {
  margin: 0 4px;
  height: 22px;
  align-self: center;
  border-color: var(--ia-border);
}

.app-body {
  min-height: 0;
}

.edit-toolbar-row {
  min-height: 42px;
}

:deep(.edit-toolbar-inner) {
  width: max-content;
  max-width: 100%;
  justify-content: center;
}

:deep(.ia-outliner-drawer) {
  --el-drawer-bg-color: #0f1824;
}
:deep(.ia-outliner-drawer .el-drawer__header) {
  margin-bottom: 8px;
  padding: 10px 14px 0;
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: var(--ia-muted);
  border-bottom: 1px solid var(--ia-border);
}
:deep(.ia-outliner-drawer .el-drawer__body) {
  padding: 12px 14px 16px;
  background: #0a1018;
}
:deep(.ia-outliner-drawer.el-drawer.ttb) {
  border-bottom: 1px solid var(--ia-border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}
</style>
