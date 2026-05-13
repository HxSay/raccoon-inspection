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

/**
 * 模拟云端下发规划路径（JSON）。生产环境此处对接真实云端接口。
 */
export async function fetchCloudPlannedPath(deployMode: DeployMode): Promise<CloudPathPoint[]> {
  await netDelay(deployMode)
  // 一条沿输电走廊的示例航线，含若干拍照点
  return [
    { id: 'wp0', x: 0, y: 35, z: 40, isPhoto: false },
    { id: 'wp1', x: 40, y: 38, z: 10, isPhoto: true },
    { id: 'wp2', x: 100, y: 42, z: -15, isPhoto: false },
    { id: 'wp3', x: 160, y: 45, z: -30, isPhoto: true },
    { id: 'wp4', x: 220, y: 40, z: -25, isPhoto: false },
    { id: 'wp5', x: 260, y: 36, z: 5, isPhoto: true },
    { id: 'wp6', x: 200, y: 34, z: 35, isPhoto: false }
  ]
}

/**
 * 火电站室内廊道巡检示例航线（地面高度 ~1.3m，与 thermalPlantScene 坐标一致）。
 */
export async function fetchThermalPlantCloudPath(deployMode: DeployMode): Promise<CloudPathPoint[]> {
  await netDelay(deployMode)
  return [
    { id: 'h0', x: -12, y: 1.32, z: -14, isPhoto: false },
    { id: 'h1', x: -10, y: 1.32, z: 2, isPhoto: true },
    { id: 'h2', x: -8, y: 1.32, z: 12, isPhoto: false },
    { id: 'h3', x: -6, y: 1.32, z: 22, isPhoto: true },
    { id: 'h4', x: 2, y: 1.32, z: 18, isPhoto: false },
    { id: 'h5', x: 8, y: 1.32, z: 8, isPhoto: true },
    { id: 'h6', x: 4, y: 1.32, z: -6, isPhoto: false }
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
