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
import { dispatchToCloudPath, dispatchToDjiWaypointMission } from '@/sim/dispatchConverter'
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

const canvasRef = ref<HTMLCanvasElement | null>(null)
/** 包裹 canvas：flex 子项里用绝对定位填满，避免 clientWidth 与缓冲区不一致导致半屏黑 */
const canvasWrapperRef = ref<HTMLElement | null>(null)

const deployMode = ref<DeployMode>('groundStation')
const simulateDisconnect = ref(false)
const simulateLowBattery = ref(false)
const simulateRtkLost = ref(false)

const batteryPercent = ref(96)
const taskStatus = ref('待命')
const missionJson = ref('')
const routeFetchUavId = ref<number | undefined>(1)
const routeFetchTaskId = ref<number | undefined>(1)
const routeFetchLoading = ref(false)
const routeFetchRawJson = ref('')
/** 从云端拉取并锚定到机巢的输电巡检航迹（有值时任务按 waypoint 直线飞行） */
const cloudPatrolPath = shallowRef<CloudPathPoint[] | null>(null)

const patrolTowerCoordRows = computed(() => getPatrolReferenceTable())

function roleLabel(role: TowerCoordRow['role']) {
  if (role === 'tower_center') return '杆塔'
  if (role === 'photo_inspection') return '拍照'
  if (role === 'drone_nest') return '机巢'
  if (role === 'ground_station') return '地面站'
  return '起降'
}

async function copyTowerCoord(row: TowerCoordRow) {
  const text = formatCoordForBackend(row)
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
    telemetrySent: reports.reduce((s, r) => s + r.telemetrySent, 0),
    bufferedWhileOffline: Math.max(...reports.map((r) => r.bufferedWhileOffline))
  }
}

/** 3D 场景 Tab：输电巡检 / 变电站 / 火电站（室内廊道 + 机器狗） */
const sceneTab = ref<'patrol' | 'substation' | 'thermal'>('patrol')

/** 3D 场景编辑器：默认开启；关闭后恢复旧 Shift 点选与无人机侧栏 */
const sceneEditEnabled = ref(true)
const simDrawerOpen = ref(false)
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

let substationLoadFailed = false
let thermalLoadFailed = false

let renderer: THREE.WebGLRenderer | null = null
let sceneBundle: ReturnType<typeof createPowerlineScene> | null = null
let substationBundle: ReturnType<typeof createSubstationScene> | null = null
let thermalBundle: ReturnType<typeof createThermalPlantScene> | null = null
let robotDog: RobotDogModel | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
/** 输电场地多机（与 corridorHomes 一一对应） */
let patrolDrones: M300DroneModel[] = []
let nest: DroneNest | null = null
let terminal: EdgeTerminal3D | null = null
/** 每架巡逻机独立遥测通道（避免一机结束 stop 掉共享 10Hz 定时器） */
let stateReports: StateReportService[] = []
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

const viewHint = computed(() => {
  const edit =
    sceneEditEnabled.value && (sceneTab.value === 'patrol' || sceneTab.value === 'substation' || sceneTab.value === 'thermal')
      ? ' 编辑：Shift+左键点选，拖轴平移；顶部可改数值。删除已自动保存；平移后请点「保存场景」。任务在右侧栏。'
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

watch(sceneEditEnabled, (on) => {
  sceneObjectEditor?.setEnabled(!on)
  sceneEditor3dRef.value?.setActive(on)
  if (on) rebindEditor3dWorld()
  missionRunners.forEach((m) => m.setPaused(on))
  applyEditorOrbitStyle(on)
}, { immediate: true })

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

function applyNetworkSim() {
  stateReports.forEach((s) => s.setOnline(!simulateDisconnect.value))
}

function getBatteryForCheck() {
  return simulateLowBattery.value ? 15 : batteryPercent.value
}

function getRtkForCheck() {
  return simulateRtkLost.value ? 0 : 2
}

function onTelemetry(t: TelemetryPayload) {
  telemetry.value = t
  offlineBufferHint.value = stateReports.reduce((a, s) => a + s.getBufferedCount(), 0)
}

function onStatus(s: string) {
  taskStatus.value = s
}

function onPhoto() {
  offlineBufferHint.value = stateReports.reduce((a, s) => a + s.getBufferedCount(), 0)
}

function onComplete(r: MissionReport) {
  lastReport.value = r
  reportOpen.value = true
  missionJson.value = missionRunners[0]?.getDjiMissionPreview() ?? missionJson.value
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
  if (!stateReports.length) return
  if (sceneTab.value === 'patrol' && sceneBundle && patrolDrones.length) {
    const n = Math.min(patrolDrones.length, sceneBundle.corridorHomes.length)
    const onPatrolFleetComplete = (r: MissionReport) => {
      patrolFleetBuffer.value.push(r)
      if (patrolFleetBuffer.value.length >= n) {
        lastReport.value = mergePatrolReports(patrolFleetBuffer.value)
        patrolFleetBuffer.value = []
        reportOpen.value = true
        missionJson.value = missionRunners.map((mr) => mr.getDjiMissionPreview()).join('\n---\n')
      }
    }
    for (let i = 0; i < n; i++) {
      missionRunners.push(
        new MissionRunner({
          agent: patrolDrones[i]!,
          pathWorld: sceneBundle.world,
          stateReport: stateReports[i]!,
          home: sceneBundle.corridorHomes[i]!.clone(),
          getDeployMode: () => deployMode.value,
          getBattery: getBatteryForCheck,
          setBattery: i === 0 ? (v) => { batteryPercent.value = Math.round(v * 10) / 10 } : () => {},
          getRtkMode: getRtkForCheck,
          onStatus: i === 0 ? onStatus : () => {},
          onTelemetry: i === 0 ? onTelemetry : () => {},
          onPhoto: (_p, _ai) => {
            onPhoto()
          },
          onComplete: onPatrolFleetComplete,
          onError,
          fetchPlannedPath: async (dep) => {
            if (cloudPatrolPath.value?.length) {
              return cloudPatrolPath.value.map((p) => ({ ...p }))
            }
            return fetchCloudPlannedPath(dep, i)
          },
          pathMode: cloudPatrolPath.value?.length ? 'linear' : 'catmullrom',
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
            const agent = patrolDrones[i]
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
        onPhoto: (_p, _ai) => {
          onPhoto()
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
  missionRunners.forEach((m) => m.setPaused(sceneEditEnabled.value))
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

  const laneN = corridorHomes.length
  stateReports = Array.from({ length: laneN }, (_, i) => {
    const s = new StateReportService()
    s.setCloudSink(() => {
      if (i === 0) cloudReceiveCount.value++
    })
    return s
  })

  patrolDrones = []
  for (let i = 0; i < laneN; i++) {
    const d = new M300DroneModel()
    void d.tryLoadExternalModel('/models/m300.glb')
    d.setPose(corridorHomes[i]!.clone(), 0)
    scene.add(d.root)
    patrolDrones.push(d)
  }

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
  patrolDrones.forEach((d) => {
    d.root.userData.noScenePick = true
  })

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

  applyNetworkSim()

  rebuildMissionRunner()

  edgeSim = createEdgeMetricsSimulator(() => !simulateDisconnect.value)

  const clock = new THREE.Clock()
  const tick = () => {
    rafMain = requestAnimationFrame(tick)
    const dt = clock.getDelta()
    nest?.tick(dt)
    edgeMetrics.value = edgeSim.tick(dt)
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

onMounted(() => {
  disposeThree = initThree()
})

onBeforeUnmount(() => {
  disposeThree?.()
  disposeThree = null
})

async function startMission() {
  if (sceneEditEnabled.value) {
    ElMessage.warning('请先关闭「模型编辑」再运行任务仿真')
    return
  }
  if (!missionRunners.length) {
    ElMessage.warning('当前场景不支持任务仿真（请切换到输电巡检或火电站）')
    return
  }
  if (!cloudPatrolPath.value?.length) {
    ElMessage.info('未加载云端路径，将使用内置演示航线（平滑曲线）')
  }
  applyNetworkSim()
  cloudReceiveCount.value = 0
  await Promise.all(missionRunners.map((m) => m.start()))
  missionJson.value = missionRunners.map((m) => m.getDjiMissionPreview()).join('\n---\n')
}

function resetMission() {
  missionRunners.forEach((m) => m.reset())
  batteryPercent.value = 96
  telemetry.value = null
  cloudReceiveCount.value = 0
  patrolFleetBuffer.value = []
  patrolNestLanded = 0
  nest?.setDoorTarget(0)
  if (sceneTab.value === 'patrol' && sceneBundle && patrolDrones.length) {
    const homes = sceneBundle.corridorHomes
    patrolDrones.forEach((d, i) => {
      const h = homes[i]
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

function saveSceneLayout() {
  const w = getPersistWorld()
  if (!w) {
    ElMessage.warning('当前场景尚未就绪')
    return
  }
  const tab = sceneTab.value as ScenePersistTab
  const prev = loadSceneState(tab)
  saveSceneState(tab, w, prev?.removedPaths ?? [])
  ElMessage.success('已保存到本机浏览器（localStorage），刷新后仍会保留。')
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
  else if (cmd === 'sim') simDrawerOpen.value = true
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
  if (routeFetchUavId.value == null || routeFetchTaskId.value == null) {
    ElMessage.warning('请输入无人机 ID 与巡检任务 ID')
    return
  }
  routeFetchLoading.value = true
  try {
    taskStatus.value = '正在拉取智能巡检路径…'
    const dispatch = await fetchRouteDispatch(routeFetchUavId.value, routeFetchTaskId.value)
    routeFetchRawJson.value = JSON.stringify(dispatch, null, 2)
    const dji = dispatchToDjiWaypointMission(dispatch, 'M300_RTK')
    missionJson.value = JSON.stringify(dji, null, 2)
    const home = sceneBundle?.corridorHomes[0]
    cloudPatrolPath.value = dispatchToCloudPath(
      dispatch,
      home ? { x: home.x, y: home.y, z: home.z } : undefined
    )
    rebuildMissionRunner()
    const n = dji.waypoints.length
    const src = dispatch.waypoints?.length ?? 0
    taskStatus.value = `已加载 ${n} 个航点，请点击「开始任务」按 waypoint 飞行`
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
    <header class="shrink-0 border-b border-[var(--ia-border)] bg-[var(--ia-panel)] px-2 py-1.5">
      <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
        <div class="hidden shrink-0 font-mono text-[10px] tracking-wide text-[var(--ia-accent)] sm:block">RACCOON EDGE SIM</div>
        <el-radio-group v-model="sceneTab" size="small" class="scene-tab-rg flex flex-wrap font-mono">
          <el-radio-button value="patrol">输电巡检场地</el-radio-button>
          <el-radio-button value="substation">变电站场景</el-radio-button>
          <el-radio-button value="thermal">火电站巡检</el-radio-button>
        </el-radio-group>

        <el-dropdown v-if="sceneTab === 'patrol'" trigger="click" class="font-mono" @command="onViewModeCommand">
          <el-button size="small" type="default" class="!font-mono">
            视角 · {{ viewMode === 'aerial' ? '鸟瞰' : '地面' }} <span class="ml-0.5 opacity-60">▾</span>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="aerial">鸟瞰</el-dropdown-item>
              <el-dropdown-item command="ground">地面</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-divider direction="vertical" class="ia-toolbar-divider" />

        <div class="flex flex-wrap items-center gap-1.5">
          <span class="font-mono text-[10px] text-[var(--ia-muted)]">模型编辑</span>
          <el-switch v-model="sceneEditEnabled" size="small" />
          <el-dropdown trigger="click" class="font-mono" @command="onSceneMenuCommand">
            <el-button size="small" type="default" class="!font-mono">
              场景菜单 <span class="ml-0.5 opacity-60">▾</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="save">保存场景</el-dropdown-item>
                <el-dropdown-item command="reset-current" divided>恢复默认（仅当前场景）…</el-dropdown-item>
                <el-dropdown-item command="reset-all">恢复默认（全部场景）…</el-dropdown-item>
                <el-dropdown-item command="sim" divided>仿真控制台</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <EditorToolbar v-if="sceneEditEnabled" :editor="sceneEditor3dRef" :ui="editorUiState" />
    </header>

    <!-- 勿使用 md:min-h-0：会把本行 min-height 压成 0，在 flex 下易导致 canvas 高度为 0、画面全黑 -->
    <div class="flex min-h-[44vh] flex-1 flex-col md:flex-row md:min-h-[min(100%,36rem)]">
      <EditorOutliner v-if="sceneEditEnabled" :editor="sceneEditor3dRef" :ui="editorUiState" />

      <main
        class="relative flex min-h-[44vh] min-w-0 flex-1 flex-col overflow-hidden border-[var(--ia-border)] bg-black md:border-x"
      >
        <div ref="canvasWrapperRef" class="relative min-h-0 min-w-0 flex-1 self-stretch">
          <canvas ref="canvasRef" class="absolute inset-0 block h-full w-full touch-none" />
          <div
            class="pointer-events-none absolute bottom-2 left-2 z-10 max-w-[min(96%,28rem)] rounded border border-[var(--ia-border)] bg-black/70 px-2 py-1 font-mono text-[10px] text-[var(--ia-muted)]"
          >
            {{ viewHint }}
          </div>
        </div>
      </main>

      <EditorProperties
        v-if="sceneEditEnabled"
        :editor="sceneEditor3dRef"
        :ui="editorUiState"
        class="max-h-[50vh] min-h-0 shrink-0 overflow-y-auto max-md:w-full md:max-h-none"
      />

      <aside
        v-else
        class="flex w-full shrink-0 flex-col gap-2 overflow-y-auto border-[var(--ia-border)] bg-[var(--ia-panel)] p-3 md:w-72 md:border-l"
      >
        <div class="font-mono text-[11px] font-semibold uppercase tracking-wider text-[var(--ia-accent)]">无人机控制</div>
        <p class="font-mono text-[9px] leading-tight text-[var(--ia-muted)]">任务与遥测在侧栏；场景切换与模型编辑在顶部。</p>

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

        <div class="font-mono text-[10px] font-semibold uppercase tracking-wider text-[var(--ia-muted)]">遥测</div>
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
        <el-card v-if="sceneTab === 'patrol' || sceneTab === 'thermal'" shadow="never" class="ia-card">
          <template #header>任务状态</template>
          <p class="mb-2 font-mono text-[11px] leading-relaxed text-[var(--ia-muted)]">{{ taskStatus }}</p>
        </el-card>
      </aside>
    </div>

    <el-drawer v-model="simDrawerOpen" title="仿真控制台" direction="ltr" size="min(380px, 92vw)" class="ia-drawer-shell">
      <div class="flex flex-col gap-3 pb-4 font-mono text-[11px] text-[var(--ia-muted)]">
        <section class="rounded border border-[var(--ia-border)] bg-[#0c141c] p-2.5">
          <div class="mb-1.5 text-[10px] font-semibold uppercase tracking-wider">边缘终端负载</div>
          <div class="grid grid-cols-2 gap-2 text-[11px]">
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

        <section v-if="sceneTab === 'patrol'" class="rounded border border-[var(--ia-border)] bg-[#0c141c] p-2.5">
          <div class="mb-1 text-[10px] font-semibold uppercase tracking-wider">场景视角</div>
          <el-radio-group v-model="viewMode" size="small" class="ia-radio-tight flex flex-col gap-0.5">
            <el-radio value="aerial">鸟瞰（默认轨道）</el-radio>
            <el-radio value="ground">地面观察（人眼高度）</el-radio>
          </el-radio-group>
          <p class="mt-1 text-[9px] leading-tight">切至地面时为固定站位；从鸟瞰进入地面会记住当前机位，切回鸟瞰时恢复。</p>
        </section>

        <el-card shadow="never" class="ia-card">
          <template #header>部署模式</template>
          <el-radio-group v-model="deployMode" size="small" class="ia-radio-tight flex flex-col gap-1">
            <el-radio value="groundStation">地面站（+100ms RTT）</el-radio>
            <el-radio value="onboard">机载（+20ms RTT）</el-radio>
          </el-radio-group>
          <p class="mt-1 text-[9px] leading-tight">云端固定 200ms + 模式附加延迟（<code class="text-[var(--ia-accent)]">constants.ts</code>）</p>
        </el-card>

        <el-card shadow="never" class="ia-card">
          <template #header>异常注入</template>
          <el-switch v-model="simulateDisconnect" active-text="断网" @change="applyNetworkSim" />
          <p class="mt-1 text-[10px]">断网时遥测缓存，恢复后补报</p>
          <el-divider class="!my-2 !border-[var(--ia-border)]" />
          <el-switch v-model="simulateLowBattery" active-text="低电量起飞" />
          <p class="mt-1 text-[10px]">自检 15% →「电量不足，无法起飞!」</p>
          <el-divider class="!my-2 !border-[var(--ia-border)]" />
          <el-switch v-model="simulateRtkLost" active-text="非 RTK 固定解" />
          <p class="mt-1 text-[10px]">mode≠2 →「未切换到RTK定位…」</p>
        </el-card>

        <el-card shadow="never" class="ia-card">
          <template #header>航点上限（65535）</template>
          <el-button size="small" class="!font-mono" @click="try65535Demo">触发校验</el-button>
        </el-card>

        <el-card v-if="sceneTab === 'patrol'" shadow="never" class="ia-card">
          <template #header>
            <div class="flex items-center justify-between gap-2">
              <span>杆塔参考坐标（WGS84）</span>
              <el-button type="primary" link size="small" class="!font-mono" @click="copyAllPhotoCoords">
                复制拍照点
              </el-button>
            </div>
          </template>
          <p class="mb-2 text-[9px] leading-tight text-[var(--ia-muted)]">
            与 3D 场景标签、<code class="text-[var(--ia-accent)]">sceneToGps</code> 一致；填入 raccoon-ui「路径规划」起降 / 途经 / 拍照点。基准约 30.5°N、104.0°E（仿真用）。
          </p>
          <el-table :data="patrolTowerCoordRows" size="small" stripe max-height="280" class="font-mono tower-coord-table">
            <el-table-column prop="label" label="位置" min-width="108" show-overflow-tooltip />
            <el-table-column label="类型" width="44">
              <template #default="{ row }">
                <span class="text-[9px] text-[var(--ia-accent)]">{{ roleLabel(row.role) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="longitude" label="经度" min-width="88" />
            <el-table-column prop="latitude" label="纬度" min-width="88" />
            <el-table-column prop="height" label="高(m)" width="52" />
            <el-table-column label="" width="40" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" title="复制 经度,纬度,高度" @click="copyTowerCoord(row)">
                  复制
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="ia-card">
          <template #header>智能巡检路径（云端）</template>
          <div class="flex flex-col gap-2">
            <div class="grid grid-cols-2 gap-2">
              <div>
                <div class="mb-0.5 text-[10px] text-[var(--ia-muted)]">无人机 ID</div>
                <el-input-number v-model="routeFetchUavId" :min="1" :controls="false" class="!w-full" size="small" />
              </div>
              <div>
                <div class="mb-0.5 text-[10px] text-[var(--ia-muted)]">巡检任务 ID</div>
                <el-input-number v-model="routeFetchTaskId" :min="1" :controls="false" class="!w-full" size="small" />
              </div>
            </div>
            <el-button
              type="primary"
              size="small"
              class="!font-mono"
              :loading="routeFetchLoading"
              @click="pullRouteAndConvert"
            >
              拉取并转换 Waypoint JSON
            </el-button>
            <p class="text-[9px] leading-tight text-[var(--ia-muted)]">
              调用
              <code class="text-[var(--ia-accent)]">GET /route-plan/dispatch</code>
              （代理至 8091）。拉取后点「开始任务」将按折线逐点飞行；未拉取则用内置演示航线（平滑曲线）。
            </p>
          </div>
        </el-card>

        <el-collapse v-if="routeFetchRawJson" class="ia-collapse ia-collapse-json">
          <el-collapse-item title="后台路径规划 JSON" name="dispatch">
            <pre class="max-h-40 overflow-auto p-1 text-[10px] leading-snug text-[#8ab4f8]">{{ routeFetchRawJson }}</pre>
          </el-collapse-item>
        </el-collapse>

        <el-collapse v-if="missionJson" class="ia-collapse ia-collapse-json">
          <el-collapse-item title="Waypoint 任务 JSON" name="1">
            <pre class="max-h-48 overflow-auto p-1 text-[10px] leading-snug text-[#6ecf9b]">{{ missionJson }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-drawer>

    <el-dialog v-model="reportOpen" title="巡检报告" class="ia-dialog" width="min(92vw, 760px)" destroy-on-close>
      <template v-if="lastReport">
        <el-descriptions :column="2" border size="small" class="mb-3 font-mono">
          <el-descriptions-item label="时长 / s">{{ lastReport.durationSec.toFixed(1) }}</el-descriptions-item>
          <el-descriptions-item label="距离 / m">{{ lastReport.distanceM.toFixed(1) }}</el-descriptions-item>
          <el-descriptions-item label="照片">{{ lastReport.photos.length }}</el-descriptions-item>
          <el-descriptions-item label="遥测条数">{{ lastReport.telemetrySent }}</el-descriptions-item>
        </el-descriptions>
        <div class="mb-2 text-[10px] text-[var(--ia-muted)]">
          关键航点画面为仿真相机离屏渲染截图；元数据含伪 GPS / 云台角。业务上原图不上云。
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

:deep(.ia-drawer-shell) {
  --el-drawer-bg-color: #0f1824;
}
:deep(.ia-drawer-shell .el-drawer__header) {
  margin-bottom: 8px;
  padding: 12px 14px 0;
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: var(--ia-muted);
  border-bottom: 1px solid var(--ia-border);
}
:deep(.ia-drawer-shell .el-drawer__body) {
  padding: 10px 12px 14px;
  background: #0a1018;
}
</style>
