import {
  AUTO_FLIGHT_SPEED,
  BATTERY_MIN_TAKEOFF_PERCENT,
  CLOUD_MISSION_FETCH_DELAY_MS,
  DEPLOY_EXTRA_LATENCY_MS,
  DJI_MAX_WAYPOINTS,
  ERR_BATTERY_LOW,
  ERR_RTK_NOT_FIXED,
  FINISH_ACTION_AUTO_RTH,
  HEADING_MODE_AUTO,
  INSPECTION_POINT_SPEED,
  MAX_FLIGHT_SPEED,
  PATROL_LANE_Z_SPACING_M,
  PHOTO_GIMBAL_PITCH_DEG,
  RTK_MODE_FIXED
} from './constants'
import type { CloudPathPoint, DjiWaypoint, DjiWaypointMission } from './types'
import type { DeployMode } from './types'

/**
 * 将仿真用的水平朝向（度）换算为拍照动作中的云台偏航（相对机身简化：等于航线机头角）。
 */
function yawTowardNext(curr: CloudPathPoint, next: CloudPathPoint | undefined): number {
  if (!next) return 0
  const dx = next.x - curr.x
  const dz = next.z - curr.z
  return (Math.atan2(dx, dz) * 180) / Math.PI
}

/**
 * 校验航点数量不超过 M300 65535 上限（长航线分航段时也应在边缘端拆分）。
 */
export function assertWaypointLimit(count: number): void {
  if (count > DJI_MAX_WAYPOINTS) {
    throw new Error(`航点数量 ${count} 超过 M300 上限 ${DJI_MAX_WAYPOINTS}，请在边缘端拆分任务`)
  }
}

/**
 * 模拟云端 HTTP：固定延迟 + 部署模式附加 RTT/2。
 */
async function netDelay(deployMode: DeployMode): Promise<void> {
  const extra = DEPLOY_EXTRA_LATENCY_MS[deployMode]
  const ms = CLOUD_MISSION_FETCH_DELAY_MS + extra
  await new Promise((r) => setTimeout(r, ms))
}

/** 基准走廊（lane 0）航点；多机时整条沿 Z 平移 `laneIndex * PATROL_LANE_Z_SPACING_M` */
const PATROL_BASE_CLOUD_PATH: CloudPathPoint[] = [
  { id: 'wp0', x: 0, y: 35, z: 40, isPhoto: false },
  { id: 'wp1', x: 40, y: 38, z: 10, isPhoto: true },
  { id: 'wp2', x: 100, y: 42, z: -15, isPhoto: false },
  { id: 'wp3', x: 160, y: 45, z: -30, isPhoto: true },
  { id: 'wp4', x: 220, y: 40, z: -25, isPhoto: false },
  { id: 'wp5', x: 260, y: 36, z: 5, isPhoto: true },
  { id: 'wp6', x: 200, y: 34, z: 35, isPhoto: false }
]

/**
 * 模拟云端下发规划路径（JSON）。生产环境此处对接真实云端接口。
 * @param laneIndex 并排走廊索引，与 `scene.corridorHomes` 及杆塔列 Z 偏移一致。
 */
export async function fetchCloudPlannedPath(deployMode: DeployMode, laneIndex = 0): Promise<CloudPathPoint[]> {
  await netDelay(deployMode)
  const dz = laneIndex * PATROL_LANE_Z_SPACING_M
  return PATROL_BASE_CLOUD_PATH.map((p) => ({
    ...p,
    z: p.z + dz,
    id: `${p.id}-L${laneIndex}`
  }))
}

/**
 * 火电站厂区地面巡检示例航线（y 为地坪 THERMAL_PLANT_GROUND_Y，与 thermalPlantScene 一致）。
 */
export async function fetchThermalPlantCloudPath(deployMode: DeployMode): Promise<CloudPathPoint[]> {
  await netDelay(deployMode)
  const y = 0
  return [
    { id: 'h0', x: -12, y, z: -14, isPhoto: false },
    { id: 'h1', x: -8, y, z: 2, isPhoto: true },
    { id: 'h2', x: -4, y, z: 14, isPhoto: false },
    { id: 'h3', x: 2, y, z: 22, isPhoto: true },
    { id: 'h4', x: 10, y, z: 16, isPhoto: false },
    { id: 'h5', x: 14, y, z: 4, isPhoto: true },
    { id: 'h6', x: 6, y, z: -8, isPhoto: false }
  ]
}

/**
 * 边缘端：将云端路径点转换为大疆 Waypoint 任务（速度、航向模式、返航、拍照动作）。
 */
export function convertToDjiWaypointMission(
  path: CloudPathPoint[],
  aircraft: 'M300_RTK' | 'QUADRUPED_INSPECTION' = 'M300_RTK'
): DjiWaypointMission {
  assertWaypointLimit(path.length)
  const waypoints: DjiWaypoint[] = path.map((p, i) => {
    const next = path[i + 1]
    const yaw = yawTowardNext(p, next)
    const speedMps = p.isPhoto ? INSPECTION_POINT_SPEED : AUTO_FLIGHT_SPEED
    const actions: DjiWaypoint['actions'] = []
    if (p.isPhoto) {
      actions.push({ type: 'GIMBAL_ROTATE', gimbalPitchDeg: PHOTO_GIMBAL_PITCH_DEG, gimbalYawDeg: yaw })
      actions.push({ type: 'TAKE_PHOTO' })
    }
    return {
      index: i,
      x: p.x,
      y: p.y,
      z: p.z,
      speedMps,
      actions
    }
  })
  return {
    aircraft,
    maxFlightSpeed: MAX_FLIGHT_SPEED,
    autoFlightSpeed: AUTO_FLIGHT_SPEED,
    headingMode: HEADING_MODE_AUTO,
    finishAction: FINISH_ACTION_AUTO_RTH ? 'GO_HOME' : 'NO_ACTION',
    waypoints
  }
}

/**
 * 模拟上传任务至飞控（异步），可扩展为真实 MQTT/SDK 调用。
 */
export async function uploadMissionToAircraft(_mission: DjiWaypointMission, deployMode: DeployMode): Promise<void> {
  await new Promise((r) => setTimeout(r, 120 + DEPLOY_EXTRA_LATENCY_MS[deployMode] * 0.5))
}

export interface PreflightContext {
  batteryPercent: number
  /** RTK 模式：2 为固定解，其余视为未就绪 */
  rtkMode: number
}

/**
 * 起飞前自检：电量、RTK —— 与边缘端抛错文案对齐。
 */
export function runPreflightChecks(ctx: PreflightContext): void {
  if (ctx.batteryPercent < BATTERY_MIN_TAKEOFF_PERCENT) {
    throw new Error(ERR_BATTERY_LOW)
  }
  if (ctx.rtkMode !== RTK_MODE_FIXED) {
    throw new Error(ERR_RTK_NOT_FIXED)
  }
}

/** 兼容常量导出给 UI 展示阈值 */
export { BATTERY_MIN_TAKEOFF_PERCENT as PREFLIGHT_BATTERY_MIN } from './constants'
export { RTK_MODE_FIXED as PREFLIGHT_RTK_OK } from './constants'
