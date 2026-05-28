import type { InspectionTask } from '@/api/droneNlp'
import type { UavRouteDispatchPayload } from '@/api/drone'

/** 父页面 → 仿真 iframe：下发巡检航线 */
export const MSG_INSPECTION_DISPATCH = 'RACCOON_INSPECTION_DISPATCH'
/** 仿真 → 父页面：航线已加载 */
export const MSG_INSPECTION_DISPATCH_ACK = 'RACCOON_INSPECTION_DISPATCH_ACK'
/** 仿真 → 父页面：任务状态 */
export const MSG_INSPECTION_STATUS = 'RACCOON_INSPECTION_STATUS'
/** 仿真 → 父页面：任务完成（含上报结果摘要） */
export const MSG_INSPECTION_MISSION_COMPLETE = 'RACCOON_INSPECTION_MISSION_COMPLETE'
/** 仿真 → 父页面：任务异常 */
export const MSG_INSPECTION_MISSION_ERROR = 'RACCOON_INSPECTION_MISSION_ERROR'

export interface InspectionDispatchMessage {
  type: typeof MSG_INSPECTION_DISPATCH
  dispatch: UavRouteDispatchPayload
  autoStart?: boolean
  /** 自然语言原文（仿真侧展示用） */
  userInput?: string
}

export interface InspectionMissionCompleteMessage {
  type: typeof MSG_INSPECTION_MISSION_COMPLETE
  summary: {
    durationSec: number
    distanceM: number
    photoCount: number
    telemetrySent: number
    multimodalUploaded?: boolean
    multimodalError?: string
  }
}

export function inspectionTaskToDispatch(task: InspectionTask): UavRouteDispatchPayload {
  const deviceVisitOrder: number[] = []
  for (const pw of task.photoWaypoints ?? []) {
    for (const id of pw.deviceIds ?? []) {
      if (id != null && !deviceVisitOrder.includes(id)) {
        deviceVisitOrder.push(id)
      }
    }
  }
  return {
    planId: task.planId,
    taskId: task.taskId,
    mapId: task.mapId!,
    uavId: task.uavId!,
    algorithm: task.algorithm ?? 'RRT*',
    takeoff: task.takeoff,
    landing: task.landing,
    waypoints: task.waypoints ?? [],
    photoWaypoints: task.photoWaypoints ?? [],
    deviceVisitOrder,
    estimated: task.estimated
  }
}

export function postDispatchToSimIframe(
  iframe: HTMLIFrameElement | null | undefined,
  dispatch: UavRouteDispatchPayload,
  options?: { autoStart?: boolean; userInput?: string }
): boolean {
  const win = iframe?.contentWindow
  if (!win) return false
  const msg: InspectionDispatchMessage = {
    type: MSG_INSPECTION_DISPATCH,
    dispatch,
    autoStart: options?.autoStart !== false,
    userInput: options?.userInput
  }
  win.postMessage(msg, '*')
  return true
}

export function formatTaskSummary(task: InspectionTask, slots?: { areaName?: string; deviceNames?: string[] }): string {
  const devices =
    slots?.deviceNames?.join('、') ||
    task.photoWaypoints?.flatMap((p) => p.deviceIds ?? []).join(',') ||
    '—'
  return [
    `区域：${slots?.areaName ?? '—'}`,
    `设备：${devices}`,
    `无人机 #${task.uavId ?? '—'}，地图 #${task.mapId ?? '—'}`,
    `算法：${task.algorithm ?? 'RRT*'}`,
    task.estimated
      ? `预估：${task.estimated.distanceM?.toFixed(1)} m / ${task.estimated.durationSec} s / 电量 ${task.estimated.batteryPct?.toFixed(2)}%`
      : ''
  ]
    .filter(Boolean)
    .join('\n')
}
