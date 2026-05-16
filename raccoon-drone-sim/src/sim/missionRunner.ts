import * as THREE from 'three'
import { AUTO_FLIGHT_SPEED, INSPECTION_POINT_SPEED, PHOTO_GIMBAL_PITCH_DEG } from './constants'
import { buildPhotoMeta, runLocalAiDetect } from './aiDetect'
import {
  convertToDjiWaypointMission,
  fetchCloudPlannedPath,
  runPreflightChecks,
  uploadMissionToAircraft
} from './edgeService'
import { FlightPathVisualization } from './flightPath'
import { PolylinePath } from './polylinePath'
import type { StateReportService } from './stateReport'
import type {
  AiDefectResult,
  CloudPathPoint,
  MissionInspectable,
  MissionReport,
  PhotoCaptureMeta,
  TelemetryPayload
} from './types'
import type { DeployMode } from './types'

/**
 * 自主巡检全流程编排：云端拉线 -> 航点转换 -> 上传飞控 -> 自检 -> CatmullRom 沿航线飞行
 * -> 拍照点云台动作 + 本地 AI -> 1Hz 遥测上报云端 -> 任务结束自动返航。
 */

function estimateCurveLength(curve: THREE.CatmullRomCurve3, divisions = 512): number {
  let len = 0
  let prev = curve.getPointAt(0)
  for (let i = 1; i <= divisions; i++) {
    const p = curve.getPointAt(i / divisions)
    len += p.distanceTo(prev)
    prev = p
  }
  return len
}

/** 在曲线上搜索与目标点最近的归一化参数 u（用于对齐拍照点） */
function findClosestU(curve: THREE.CatmullRomCurve3, target: THREE.Vector3, samples = 1200): number {
  let best = 0
  let bestD = Infinity
  for (let i = 0; i <= samples; i++) {
    const u = i / samples
    const p = curve.getPointAt(u)
    const d = p.distanceToSquared(target)
    if (d < bestD) {
      bestD = d
      best = u
    }
  }
  return best
}

export interface MissionVisualHooks {
  /** 起飞前自检已通过（可开舱门） */
  onPreflightPassed?: () => void
  /** 已进入自主飞行主循环（可全速离巢） */
  onMissionFlightBegin?: () => void
  /** 进入返航段 */
  onRthBegin?: () => void
  /** 任务完全结束、已降落（可关舱门） */
  onMissionEnded?: () => void
}

export interface MissionRunnerOptions {
  agent: MissionInspectable
  /** 航迹标记挂接到场景该组下（与地形同级） */
  pathWorld: THREE.Group
  stateReport: StateReportService
  home: THREE.Vector3
  getDeployMode: () => DeployMode
  getBattery: () => number
  setBattery: (v: number) => void
  getRtkMode: () => number
  onStatus: (s: string) => void
  onTelemetry: (t: TelemetryPayload) => void
  /** 边缘定时上报云端（1Hz，写入 uav_location_history） */
  onCloudReport?: (t: TelemetryPayload) => void
  onPhoto: (p: PhotoCaptureMeta, ai: AiDefectResult) => void
  /** 拍照帧：用仿真相机对当前 Three 场景离屏渲染（返回 JPEG data URL） */
  captureInspectionPhoto?: () => string | null | Promise<string | null>
  onComplete: (r: MissionReport) => void
  onError: (e: Error) => void
  /** 纯可视化钩子：不改变航点、转换、遥测等业务逻辑 */
  visualHooks?: MissionVisualHooks
  /** 未指定时与输电场景一致，走 fetchCloudPlannedPath */
  fetchPlannedPath?: (deploy: DeployMode) => Promise<CloudPathPoint[]>
  /** linear：逐点直线飞行（云端规划）；catmullrom：演示用平滑曲线 */
  pathMode?: 'linear' | 'catmullrom'
  /** 地面载具返航段不大幅爬高 */
  vehicleClass?: 'uav' | 'ugv'
  djiAircraftId?: 'M300_RTK' | 'QUADRUPED_INSPECTION'
}

export class MissionRunner {
  private opts: MissionRunnerOptions
  private rafId = 0
  private lastTs = 0
  private phase: 'idle' | 'mission' | 'rth' | 'done' = 'idle'
  private curveMission: THREE.CatmullRomCurve3 | null = null
  private polylineMission: PolylinePath | null = null
  private curveRth: THREE.CatmullRomCurve3 | null = null
  private lenMission = 1
  private lenRth = 1
  private u = 0
  private paused = false
  private capturing = false
  private photoTriggers: { u: number; wpIndex: number; yawDeg: number }[] = []
  private triggered = new Set<number>()
  private flown: THREE.Vector3[] = []
  private lastPos = new THREE.Vector3()
  private distanceM = 0
  private startedAt = 0
  private finishedAt = 0
  private photos: PhotoCaptureMeta[] = []
  private aiResults: AiDefectResult[] = []
  private telemetrySent = 0
  private missionPath: CloudPathPoint[] = []
  private djiMissionJson = ''
  private pathViz: FlightPathVisualization | null = null
  private takeoffBlend = 0
  private firstFlightFrame = true

  constructor(opts: MissionRunnerOptions) {
    this.opts = opts
    const onCloud = opts.onCloudReport
    this.opts.stateReport.setCloudSink((p) => {
      this.telemetrySent++
      onCloud?.(p)
    })
  }

  isPaused(): boolean {
    return this.paused
  }

  setPaused(v: boolean): void {
    this.paused = v
    this.opts.agent.setRotorRunning(!v && (this.phase === 'mission' || this.phase === 'rth'))
  }

  reset(): void {
    this.stopLoop()
    this.clearPathViz()
    this.phase = 'idle'
    this.u = 0
    this.curveMission = null
    this.curveRth = null
    this.triggered.clear()
    this.flown = []
    this.distanceM = 0
    this.photos = []
    this.aiResults = []
    this.telemetrySent = 0
    this.capturing = false
    this.takeoffBlend = 0
    this.firstFlightFrame = true
    this.opts.agent.setRotorRunning(false)
    this.opts.onStatus('已重置')
  }

  /** 从云端拉取航线并执行到返航落地（异步入口） */
  async start(): Promise<void> {
    if (this.phase !== 'idle' && this.phase !== 'done') {
      this.opts.onError(new Error('任务进行中'))
      return
    }
    try {
      this.startedAt = Date.now()
      this.distanceM = 0
      this.flown = []
      this.photos = []
      this.aiResults = []
      this.triggered.clear()
      this.telemetrySent = 0
      this.u = 0
      this.phase = 'idle'

      const deploy = this.opts.getDeployMode()
      this.opts.onStatus('正在从云端获取规划路径…')
      const pathFetcher = this.opts.fetchPlannedPath ?? fetchCloudPlannedPath
      this.missionPath = await pathFetcher(deploy)

      const linear = this.opts.pathMode === 'linear'

      this.clearPathViz()
      this.pathViz = new FlightPathVisualization(this.missionPath, 4096, linear)
      this.opts.pathWorld.add(this.pathViz.group)

      this.opts.onStatus('正在转换为大疆 Waypoint 任务…')
      const dji = convertToDjiWaypointMission(this.missionPath, this.opts.djiAircraftId ?? 'M300_RTK')
      this.djiMissionJson = JSON.stringify(dji, null, 2)

      this.opts.onStatus('正在上传任务至飞控…')
      await uploadMissionToAircraft(dji, deploy)

      this.opts.onStatus('起飞前自检（电量 / RTK）…')
      runPreflightChecks({
        batteryPercent: this.opts.getBattery(),
        rtkMode: this.opts.getRtkMode()
      })
      this.opts.visualHooks?.onPreflightPassed?.()

      const pts = this.missionPath.map((p) => new THREE.Vector3(p.x, p.y, p.z))
      if (linear) {
        this.polylineMission = new PolylinePath(pts)
        this.curveMission = null
        this.lenMission = this.polylineMission.totalLength
        this.photoTriggers = this.missionPath
          .map((p, i) => {
            if (!p.isPhoto) return null
            const next = this.missionPath[i + 1]
            const yawDeg = next
              ? (Math.atan2(next.x - p.x, next.z - p.z) * 180) / Math.PI
              : (Math.atan2(p.x, p.z) * 180) / Math.PI
            return { u: this.polylineMission!.getUAtIndex(i), wpIndex: i, yawDeg }
          })
          .filter((x): x is { u: number; wpIndex: number; yawDeg: number } => x != null)
          .sort((a, b) => a.u - b.u)
      } else {
        this.polylineMission = null
        this.curveMission = new THREE.CatmullRomCurve3(pts, false, 'catmullrom', 0.4)
        this.lenMission = Math.max(1, estimateCurveLength(this.curveMission))
        this.photoTriggers = this.missionPath
          .map((p, i) => {
            if (!p.isPhoto) return null
            const u = findClosestU(this.curveMission!, new THREE.Vector3(p.x, p.y, p.z))
            const next = this.missionPath[i + 1]
            const yawDeg = next
              ? (Math.atan2(next.x - p.x, next.z - p.z) * 180) / Math.PI
              : (Math.atan2(p.x, p.z) * 180) / Math.PI
            return { u, wpIndex: i, yawDeg }
          })
          .filter((x): x is { u: number; wpIndex: number; yawDeg: number } => x != null)
          .sort((a, b) => a.u - b.u)
      }

      this.opts.stateReport.start()
      this.phase = 'mission'
      this.takeoffBlend = 0
      this.firstFlightFrame = true
      this.opts.agent.setRotorRunning(true)
      this.opts.onStatus(linear ? '自主巡检中（按 Waypoint 逐点直线飞行）' : '自主巡检中（CatmullRom 平滑航线）')
      this.lastPos.copy(this.opts.home)
      this.lastTs = performance.now()
      this.rafId = requestAnimationFrame(this.loop)
    } catch (e) {
      this.opts.stateReport.stop()
      this.clearPathViz()
      this.opts.onError(e instanceof Error ? e : new Error(String(e)))
      this.phase = 'idle'
      this.opts.agent.setRotorRunning(false)
    }
  }

  getDjiMissionPreview(): string {
    return this.djiMissionJson
  }

  private stopLoop(): void {
    if (this.rafId) {
      cancelAnimationFrame(this.rafId)
      this.rafId = 0
    }
    this.opts.stateReport.stop()
  }

  private loop = (): void => {
    const now = performance.now()
    const dt = Math.min(0.05, (now - this.lastTs) / 1000) || 0.016
    this.lastTs = now

    this.opts.agent.tick(dt)

    if (this.phase === 'idle' || this.phase === 'done') {
      return
    }

    const len = this.phase === 'mission' ? this.lenMission : this.lenRth

    let pos: THREE.Vector3
    let tan: THREE.Vector3
    if (this.phase === 'mission' && this.polylineMission) {
      pos = this.polylineMission.getPointAt(Math.min(1, this.u))
      tan = this.polylineMission.getTangentAt(Math.min(1, this.u))
    } else {
      const curve = this.phase === 'mission' ? this.curveMission! : this.curveRth!
      pos = curve.getPointAt(Math.min(1, this.u))
      tan = curve.getTangentAt(Math.min(1, this.u))
    }

    if (this.phase === 'mission' && this.takeoffBlend < 1 && !this.paused && !this.capturing) {
      this.takeoffBlend = Math.min(1, this.takeoffBlend + dt * 0.72)
      const tb = THREE.MathUtils.smoothstep(this.takeoffBlend, 0, 1)
      const curvePos =
        this.polylineMission?.getPointAt(Math.min(1, this.u)) ??
        this.curveMission!.getPointAt(Math.min(1, this.u))
      pos = new THREE.Vector3().lerpVectors(this.opts.home, curvePos, tb)
      const toCurve = curvePos.clone().sub(this.opts.home)
      if (toCurve.lengthSq() > 1e-6) toCurve.normalize()
      tan = new THREE.Vector3().lerpVectors(toCurve, tan, tb).normalize()
    }

    if (this.phase === 'rth') {
      const tl = THREE.MathUtils.smoothstep(this.u, 0.72, 1)
      const hangar = this.opts.home.clone().add(new THREE.Vector3(0, 1.45, 0))
      pos = new THREE.Vector3().lerpVectors(pos, hangar, tl)
    }

    const yaw = Math.atan2(tan.x, tan.z)

    if (!this.paused && !this.capturing) {
      let spd = AUTO_FLIGHT_SPEED
      for (const ph of this.photoTriggers) {
        if (Math.abs(this.u - ph.u) < 0.012) spd = Math.min(spd, INSPECTION_POINT_SPEED)
      }
      const du = (spd * dt) / len
      this.u = Math.min(1, this.u + du)
      this.distanceM += pos.distanceTo(this.lastPos)
      this.lastPos.copy(pos)

      const drain = 0.015 * spd * dt
      this.opts.setBattery(Math.max(0, this.opts.getBattery() - drain))
    }

    if (this.firstFlightFrame && this.phase === 'mission') {
      this.firstFlightFrame = false
      this.opts.visualHooks?.onMissionFlightBegin?.()
    }

    this.opts.agent.setPose(pos, yaw)
    if (!this.capturing) {
      this.opts.agent.setGimbal(-12, 0)
    }

    if (this.flown.length === 0 || this.flown[this.flown.length - 1].distanceToSquared(pos) > 4) {
      this.flown.push(pos.clone())
      if (this.flown.length > 600) this.flown.shift()
    }

    if (this.pathViz) {
      const uDisp = this.phase === 'mission' ? this.u : 1
      if (this.phase === 'mission' && this.polylineMission) {
        this.pathViz.updateProgressLinear(this.flown, this.polylineMission, uDisp)
      } else if (this.curveMission) {
        this.pathViz.updateProgress(this.flown, this.curveMission, uDisp)
      }
    }

    const progress = this.phase === 'mission' ? this.u * 0.85 : 0.85 + this.u * 0.15
    const telem: TelemetryPayload = {
      t: Date.now(),
      position: { x: pos.x, y: pos.y, z: pos.z },
      altitudeM: pos.y,
      batteryPercent: this.opts.getBattery(),
      speedMps: this.paused || this.capturing ? 0 : AUTO_FLIGHT_SPEED,
      rtkMode: this.opts.getRtkMode(),
      missionProgress: Math.min(1, progress),
      phase: this.phase
    }
    this.opts.stateReport.sample(telem)
    this.opts.onTelemetry(telem)

    void this.maybePhoto(pos)

    if (!this.paused && !this.capturing && this.u >= 1 && this.phase === 'mission') {
      void this.beginRth(pos)
    }
    if (!this.paused && !this.capturing && this.u >= 1 && this.phase === 'rth') {
      this.finishMission()
    }

    if (this.phase === 'mission' || this.phase === 'rth') {
      this.rafId = requestAnimationFrame(this.loop)
    }
  }

  private async maybePhoto(pos: THREE.Vector3): Promise<void> {
    if (this.phase !== 'mission' || this.capturing || this.paused) return
    for (const ph of this.photoTriggers) {
      if (this.triggered.has(ph.wpIndex)) continue
      if (this.u + 0.0005 >= ph.u) {
        this.triggered.add(ph.wpIndex)
        this.capturing = true
        this.opts.agent.setRotorRunning(true)
        this.opts.onStatus(`拍照航点 #${ph.wpIndex}：调整云台并触发快门…`)
        const steps = 12
        for (let s = 0; s <= steps; s++) {
          const t = s / steps
          const pitch = -12 + (PHOTO_GIMBAL_PITCH_DEG + 12) * t
          this.opts.agent.setGimbal(pitch, ph.yawDeg * t)
          await new Promise((r) => setTimeout(r, 40))
        }
        let imageDataUrl: string | undefined
        const capFn = this.opts.captureInspectionPhoto
        if (capFn) {
          try {
            const raw = await Promise.resolve(capFn())
            if (raw) imageDataUrl = raw
          } catch (e) {
            console.warn('[MissionRunner] captureInspectionPhoto', e)
          }
        }
        const meta = buildPhotoMeta({
          waypointIndex: ph.wpIndex,
          position: { x: pos.x, y: pos.y, z: pos.z },
          gimbalPitchDeg: PHOTO_GIMBAL_PITCH_DEG,
          gimbalYawDeg: ph.yawDeg,
          imageDataUrl
        })
        this.photos.push(meta)
        this.opts.onStatus('本地 AI 缺陷检测中（不上传原图）…')
        const ai = await runLocalAiDetect(meta)
        this.aiResults.push(ai)
        this.opts.onPhoto(meta, ai)
        this.capturing = false
        this.opts.onStatus(
          this.polylineMission ? '自主巡检中（按 Waypoint 逐点直线飞行）' : '自主巡检中（CatmullRom 平滑航线）'
        )
        break
      }
    }
  }

  private beginRth(from: THREE.Vector3): void {
    this.opts.visualHooks?.onRthBegin?.()
    this.opts.onStatus('航线段完成，自动返航（RTH）…')
    this.phase = 'rth'
    this.u = 0
    const mid = new THREE.Vector3().lerpVectors(from, this.opts.home, 0.5)
    const yLift = this.opts.vehicleClass === 'ugv' ? 0.55 : 25
    mid.y += yLift
    this.curveRth = new THREE.CatmullRomCurve3([from.clone(), mid, this.opts.home.clone()], false, 'catmullrom', 0.25)
    this.lenRth = Math.max(1, estimateCurveLength(this.curveRth, 256))
    this.lastPos.copy(from)
  }

  private finishMission(): void {
    this.phase = 'done'
    this.opts.visualHooks?.onMissionEnded?.()
    this.stopLoop()
    this.opts.agent.setRotorRunning(false)
    this.opts.agent.setPose(this.opts.home.clone(), 0)
    this.opts.agent.setGimbal(0, 0)
    this.finishedAt = Date.now()
    this.opts.onStatus('任务完成，已降落至起飞点')
    const report: MissionReport = {
      startedAt: this.startedAt,
      finishedAt: this.finishedAt,
      durationSec: (this.finishedAt - this.startedAt) / 1000,
      distanceM: this.distanceM,
      photos: [...this.photos],
      aiResults: [...this.aiResults],
      telemetrySent: this.telemetrySent,
      bufferedWhileOffline: this.opts.stateReport.getBufferedCount()
    }
    this.opts.onComplete(report)
  }

  dispose(): void {
    this.stopLoop()
    this.clearPathViz()
  }

  private clearPathViz(): void {
    if (this.pathViz) {
      this.opts.pathWorld.remove(this.pathViz.group)
      this.pathViz.dispose()
      this.pathViz = null
    }
  }
}
