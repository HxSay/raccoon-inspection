/** 云端下发的路径点（地理/场景统一用米制局部坐标） */
export interface CloudPathPoint {
  id: string
  /** 东向 X（米） */
  x: number
  /** 高度（米） */
  y: number
  /** 北向 Z（米） */
  z: number
  /** 是否为拍照点 */
  isPhoto: boolean
}

/** 大疆 Waypoint 任务中单点动作（仿真结构，可与真机 JSON 对齐扩展） */
export interface DjiWaypointAction {
  type: 'TAKE_PHOTO' | 'GIMBAL_ROTATE'
  gimbalPitchDeg?: number
  gimbalYawDeg?: number
}

/** 大疆 Waypoint 任务中的单航点（仿真） */
export interface DjiWaypoint {
  index: number
  x: number
  y: number
  z: number
  /** 飞向该点时的目标速度（接近巡检点时降为 INSPECTION_POINT_SPEED） */
  speedMps: number
  actions: DjiWaypointAction[]
}

/** 大疆 Waypoint 任务整体配置（与边缘端转换结果对齐的字段子集） */
export interface DjiWaypointMission {
  aircraft: 'M300_RTK'
  maxFlightSpeed: number
  autoFlightSpeed: number
  headingMode: 'AUTO'
  finishAction: 'GO_HOME' | 'NO_ACTION'
  waypoints: DjiWaypoint[]
}

/** 拍照元数据（不上传原图，仅记录元数据用于报告） */
export interface PhotoCaptureMeta {
  id: string
  waypointIndex: number
  timestamp: number
  gps: { latitude: number; longitude: number; altitudeM: number }
  gimbal: { pitchDeg: number; yawDeg: number; rollDeg: number }
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
