import type { Object3D, Vector3 } from 'three'

/** 云端下发的路径点 */
export interface CloudPathPoint {
  id: string
  /** WGS84 经度 / 纬度 / 高度（米），与 raccoon-cloud-drone 下发一致 */
  longitude?: number
  latitude?: number
  height?: number
  /** 仿真场景局部坐标（米）：x 东向、y 高度、z 北向 */
  x: number
  y: number
  z: number
  /** 是否为拍照点 */
  isPhoto: boolean
}

/** 可被 MissionRunner 驱动的巡检载具（无人机 / 机器狗等） */
export interface MissionInspectable {
  setPose(position: Vector3, yawRad: number, pitchRad?: number, rollRad?: number): void
  setGimbal(pitchDeg: number, yawDeg: number): void
  setRotorRunning(on: boolean): void
  tick(dt: number): void
  /** 巡检快门光轴：局部 +Z 为视线方向，世界矩阵在拍照帧需已更新 */
  getInspectionViewRig?(): Object3D | null
}

/** 大疆 Waypoint 任务中单点动作（仿真结构，可与真机 JSON 对齐扩展） */
export interface DjiWaypointAction {
  type: 'TAKE_PHOTO' | 'GIMBAL_ROTATE'
  gimbalPitchDeg?: number
  gimbalYawDeg?: number
}

/** 大疆 Waypoint 任务中的单航点 */
export interface DjiWaypoint {
  index: number
  /** WGS84，与后台 dispatch.waypoints 同序同值 */
  longitude: number
  latitude: number
  height: number
  /** 飞向该点时的目标速度（接近巡检点时降为 INSPECTION_POINT_SPEED） */
  speedMps: number
  actions: DjiWaypointAction[]
  /** 本点关联的巡检设备 ID（来自 photoWaypoints） */
  deviceIds?: number[]
  /** 仿真场景坐标（米），仅本地 Three.js 飞行用，勿与经纬度对照 */
  scenePosition?: { x: number; y: number; z: number }
}

/** 大疆 Waypoint 任务整体配置（与边缘端转换结果对齐的字段子集） */
export interface DjiWaypointMission {
  aircraft: 'M300_RTK' | 'QUADRUPED_INSPECTION'
  maxFlightSpeed: number
  autoFlightSpeed: number
  headingMode: 'AUTO'
  finishAction: 'GO_HOME' | 'NO_ACTION'
  waypoints: DjiWaypoint[]
  /** 来自 raccoon-cloud-drone 路径规划的元数据 */
  missionMeta?: {
    planId?: number
    taskId?: number
    mapId?: number
    uavId?: number
    algorithm?: string
    deviceVisitOrder?: number[]
    estimated?: {
      distanceM: number
      durationSec: number
      batteryPct: number
    }
    /** 后台原始飞行点序列（与 dispatch.waypoints 一致） */
    sourceFlightPath?: { longitude: number; latitude: number; height: number }[]
  }
}

/** 拍照元数据；imageDataUrl 为仿真离屏渲染截图（JPEG data URL），不上传云端 */
export interface PhotoCaptureMeta {
  id: string
  waypointIndex: number
  timestamp: number
  gps: { latitude: number; longitude: number; altitudeM: number }
  gimbal: { pitchDeg: number; yawDeg: number; rollDeg: number }
  /** 本机渲染的巡检画面（仿真相机） */
  imageDataUrl?: string
}

/** 本地 AI 检测结果 */
export interface AiDefectResult {
  photoId: string
  hasDefect: boolean
  label: string
  confidence: number
  inferenceMs: number
}

/** 遥测上报载荷（边缘 -> 云端） */
export interface TelemetryPayload {
  t: number
  position: { x: number; y: number; z: number }
  altitudeM: number
  batteryPercent: number
  speedMps: number
  rtkMode: number
  missionProgress: number
  phase: string
}

/** 部署模式 */
export type DeployMode = 'groundStation' | 'onboard'

/** 任务完成报告（弹窗展示） */
export interface MissionReport {
  startedAt: number
  finishedAt: number
  durationSec: number
  distanceM: number
  photos: PhotoCaptureMeta[]
  aiResults: AiDefectResult[]
  telemetrySent: number
  bufferedWhileOffline: number
}
