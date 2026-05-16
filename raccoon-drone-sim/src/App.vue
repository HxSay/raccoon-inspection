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
import { TELEMETRY_INTERVAL_MS, DJI_MAX_WAYPOINTS } from '@/sim/constants'
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

/** Shift+点选 + 变换手柄：编辑当前 Tab 场景内物体（删除 / 应用数值 /「保存场景」写入本机） */
const sceneEditEnabled = ref(false)
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
let sceneObjectEditor: SceneObjectEditor | null = null

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
    // 站在起降区南侧略偏东，仰望单排线路走廊与杆塔（塔列 z≈-35）
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
          fetchPlannedPath: (dep) => fetchCloudPlannedPath(dep, i),
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

  nest.root.userData.noScenePick = true
  terminal.root.userData.noScenePick = true
  patrolDrones.forEach((d) => {
    d.root.userData.noScenePick = true
  })

  applySceneState(world, 'patrol')

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
  sceneObjectEditor.setEnabled(sceneEditEnabled.value)
  sceneObjectEditor.rebindToActiveScene()

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
  if (!missionRunners.length) {
    ElMessage.warning('当前场景不支持任务仿真（请切换到输电巡检或火电站）')
    return
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

watch(sceneEditEnabled, (on) => {
  sceneObjectEditor?.setEnabled(on)
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

function handlePersistDropdown(cmd: string) {
  if (cmd === 'current' || cmd === 'all') void onPersistCommand(cmd)
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
    <header class="shrink-0 border-b border-[var(--ia-border)] bg-[var(--ia-panel)] px-2 py-2">
      <div class="flex flex-wrap items-center gap-x-2 gap-y-1.5">
        <div class="hidden font-mono text-[10px] tracking-wide text-[var(--ia-accent)] sm:block">RACCOON EDGE SIM</div>
        <el-radio-group v-model="sceneTab" size="small" class="scene-tab-rg flex flex-wrap font-mono">
          <el-radio-button value="patrol">输电巡检场地</el-radio-button>
          <el-radio-button value="substation">变电站场景</el-radio-button>
          <el-radio-button value="thermal">火电站巡检</el-radio-button>
        </el-radio-group>

        <template v-if="sceneTab === 'patrol'">
          <el-radio-group v-model="viewMode" size="small" class="ia-radio-tight flex flex-wrap gap-0.5 font-mono">
            <el-radio-button value="aerial">鸟瞰</el-radio-button>
            <el-radio-button value="ground">地面</el-radio-button>
          </el-radio-group>
        </template>

        <el-divider direction="vertical" class="ia-toolbar-divider" />

        <span class="hidden font-mono text-[9px] uppercase tracking-wider text-[var(--ia-muted)] md:inline">场景 / 地图</span>

        <div class="flex flex-wrap items-center gap-1.5">
          <span class="font-mono text-[10px] text-[var(--ia-muted)]">模型编辑</span>
          <el-switch v-model="sceneEditEnabled" size="small" />
          <el-button size="small" class="!font-mono" @click="saveSceneLayout">保存场景</el-button>
          <el-dropdown trigger="click" @command="handlePersistDropdown">
            <el-button size="small" class="!font-mono"> 恢复默认 <span class="opacity-70">▾</span> </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="current">仅当前场景…</el-dropdown-item>
                <el-dropdown-item command="all" divided>全部场景…</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" class="!font-mono" @click="simDrawerOpen = true">仿真控制台</el-button>
        </div>
      </div>

      <div
        v-if="sceneEditEnabled && sceneEditorSnap"
        class="mt-2 flex flex-col gap-2 border-t border-[var(--ia-border)] pt-2 lg:flex-row lg:items-end lg:gap-3"
      >
        <div class="truncate font-mono text-[10px] text-[var(--ia-accent)]" :title="sceneEditorSnap.label">{{ sceneEditorSnap.label }}</div>
        <div class="flex flex-1 flex-wrap items-end gap-2">
          <div class="grid w-full min-w-0 grid-cols-3 gap-2 sm:flex sm:flex-1 sm:flex-wrap">
            <div class="min-w-0 sm:w-[5.5rem]">
              <div class="mb-0.5 font-mono text-[9px] text-[var(--ia-muted)]">X</div>
              <el-input-number v-model="editForm.x" :step="0.5" size="small" controls-position="right" class="!w-full" />
            </div>
            <div class="min-w-0 sm:w-[5.5rem]">
              <div class="mb-0.5 font-mono text-[9px] text-[var(--ia-muted)]">Y</div>
              <el-input-number v-model="editForm.y" :step="0.5" size="small" controls-position="right" class="!w-full" />
            </div>
            <div class="min-w-0 sm:w-[5.5rem]">
              <div class="mb-0.5 font-mono text-[9px] text-[var(--ia-muted)]">Z</div>
              <el-input-number v-model="editForm.z" :step="0.5" size="small" controls-position="right" class="!w-full" />
            </div>
            <div class="col-span-3 min-w-0 sm:col-span-1 sm:w-[7.5rem]">
              <div class="mb-0.5 font-mono text-[9px] text-[var(--ia-muted)]">绕 Y（°）</div>
              <el-input-number v-model="editForm.rotYdeg" :step="5" size="small" controls-position="right" class="!w-full" />
            </div>
            <div class="min-w-0 sm:w-[4.5rem]">
              <div class="mb-0.5 font-mono text-[9px] text-[var(--ia-muted)]">Sx</div>
              <el-input-number v-model="editForm.sx" :min="0.05" :step="0.05" size="small" controls-position="right" class="!w-full" />
            </div>
            <div class="min-w-0 sm:w-[4.5rem]">
              <div class="mb-0.5 font-mono text-[9px] text-[var(--ia-muted)]">Sy</div>
              <el-input-number v-model="editForm.sy" :min="0.05" :step="0.05" size="small" controls-position="right" class="!w-full" />
            </div>
            <div class="min-w-0 sm:w-[4.5rem]">
              <div class="mb-0.5 font-mono text-[9px] text-[var(--ia-muted)]">Sz</div>
              <el-input-number v-model="editForm.sz" :min="0.05" :step="0.05" size="small" controls-position="right" class="!w-full" />
            </div>
          </div>
          <div class="flex shrink-0 flex-wrap gap-1.5">
            <el-button type="primary" size="small" class="!font-mono" @click="applySceneEditForm">应用并保存位姿</el-button>
            <el-button size="small" class="!font-mono" @click="clearSceneSelection">取消选中</el-button>
            <el-button type="danger" size="small" plain class="!font-mono" @click="deleteSceneSelection">删除物体</el-button>
          </div>
        </div>
        <p class="font-mono text-[9px] leading-tight text-[var(--ia-muted)] lg:max-w-xs">
          Shift+左键点选 <code class="text-[var(--ia-accent)]">world</code> 下可编辑网格；拖轴平移后请点「保存场景」。删除会立刻写入本机并释放几何体。
        </p>
      </div>
    </header>

    <div class="flex min-h-[44vh] flex-1 flex-col md:min-h-0 md:flex-row">
      <main class="relative min-h-[44vh] flex-1 border-[var(--ia-border)] bg-black md:min-h-0 md:border-x">
        <canvas ref="canvasRef" class="h-full w-full touch-none" />
        <div
          class="pointer-events-none absolute bottom-2 left-2 max-w-[min(96%,28rem)] rounded border border-[var(--ia-border)] bg-black/70 px-2 py-1 font-mono text-[10px] text-[var(--ia-muted)]"
        >
          {{ viewHint }}
        </div>
      </main>

      <aside
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
