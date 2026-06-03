import { sceneToGps } from '@/sim/aiDetect'
import type { TelemetryPayload } from '@/sim/types'
import type { HxResult } from '@/types/droneDispatch'

const DRONE_API_BASE = import.meta.env.VITE_DRONE_API_BASE ?? '/api/drone'

export interface RobotTelemetryReport {
  uavId: number
  channel: string
  longitude?: number
  latitude?: number
  height?: number
  batteryPct?: number
  enduranceMin?: number
  assignedTaskCount?: number
  cpuPct?: number
  memoryPct?: number
  online?: boolean
  flightStatus?: string
  faultStatus?: string
  faultMessage?: string
}

const lastReportMs: Record<string, number> = {}

function shouldReport(key: string, intervalMs: number): boolean {
  const now = Date.now()
  const last = lastReportMs[key] ?? 0
  if (now - last < intervalMs) return false
  lastReportMs[key] = now
  return true
}

async function postReport(report: RobotTelemetryReport): Promise<void> {
  try {
    const res = await fetch(`${DRONE_API_BASE}/inspection-robot/telemetry`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(report),
      keepalive: true
    })
    if (!res.ok) return
    const body = (await res.json()) as HxResult<unknown>
    if (body.code !== 200) {
      console.warn('[robot-telemetry]', body.msg)
    }
  } catch {
    /* 网络拥塞或 drone 未启动时静默，避免 Uncaught (in promise) 刷屏 */
  }
}

/**
 * 待机心跳：每机每 3s 一次，避免每帧上报导致浏览器 ERR_NO_BUFFER_SPACE。
 */
export function reportRobotPresenceHeartbeat(
  uavId: number,
  telem: TelemetryPayload,
  online: boolean,
  opts?: { faultStatus?: string; faultMessage?: string }
): void {
  if (!shouldReport(`presence-${uavId}`, 5000)) return
  const gps = sceneToGps(telem.position.x, telem.position.y, telem.position.z)
  void postReport({
    uavId,
    channel: 'POSITION,RUNTIME',
    longitude: Math.round(gps.longitude * 1_000_000) / 1_000_000,
    latitude: Math.round(gps.latitude * 1_000_000) / 1_000_000,
    height: Math.round(gps.altitudeM * 100) / 100,
    online,
    flightStatus: telem.phase ?? 'STANDBY',
    faultStatus: opts?.faultStatus ?? 'NONE',
    faultMessage: opts?.faultMessage
  })
}

/** 仿真任务循环：位置 + 运行状态（飞行中约 2Hz） */
export function reportRobotPosition10Hz(
  uavId: number,
  telem: TelemetryPayload,
  online: boolean,
  opts?: { faultStatus?: string; faultMessage?: string }
): void {
  if (!shouldReport(`pos-${uavId}`, 500)) return
  const gps = sceneToGps(telem.position.x, telem.position.y, telem.position.z)
  void postReport({
    uavId,
    channel: 'POSITION,RUNTIME',
    longitude: Math.round(gps.longitude * 1_000_000) / 1_000_000,
    latitude: Math.round(gps.latitude * 1_000_000) / 1_000_000,
    height: Math.round(gps.altitudeM * 100) / 100,
    online,
    flightStatus: telem.phase,
    faultStatus: opts?.faultStatus ?? 'NONE',
    faultMessage: opts?.faultMessage
  })
}

/** 电量 1Hz */
export function reportRobotBattery1Hz(uavId: number, batteryPct: number, enduranceMin: number): void {
  if (!shouldReport(`bat-${uavId}`, 1000)) return
  void postReport({
    uavId,
    channel: 'BATTERY',
    batteryPct,
    enduranceMin
  })
}

/** 负载 1Hz（边缘 CPU/内存 + 任务数） */
export function reportRobotLoad1Hz(
  uavId: number,
  cpuPct: number,
  memoryPct: number,
  assignedTaskCount: number
): void {
  if (!shouldReport(`load-${uavId}`, 1000)) return
  void postReport({
    uavId,
    channel: 'LOAD',
    cpuPct,
    memoryPct,
    assignedTaskCount
  })
}
