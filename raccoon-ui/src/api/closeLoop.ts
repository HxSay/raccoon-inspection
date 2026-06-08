import request from '@/utils/request'
import type { UavRouteDispatchPayload } from './drone'

/** 实际完成的巡检点位 */
export interface CapturedPoint {
  waypointIndex?: number
  deviceId?: number
  dataTypes?: string[]
  hasDefect?: boolean
  faultLabel?: string
}

/** 任务闭环受理入参（计划快照 + 实际执行结果） */
export interface CloseLoopReportRequest {
  taskId: string
  mapId?: number
  workOrderId?: number
  taskName?: string
  plannedDeviceIds?: number[]
  plannedWaypointCount?: number
  requiredDataTypes?: string[]
  finishedWaypointCount?: number
  capturedPoints?: CapturedPoint[]
  flightDistanceM?: number
  durationSec?: number
  telemetrySent?: number
  multimodalUploaded?: boolean
  /** 演示/调试：仅核对与报告，不自动补检 */
  skipReschedule?: boolean
  rescheduleCount?: number
  parentTaskId?: string
}

export interface DataGap {
  waypointIndex?: number
  deviceId?: number
  requiredType?: string
  message?: string
}

export interface IntegrityVerifyResult {
  taskId?: string
  missedDeviceIds?: number[]
  missedWaypointCount?: number
  dataGaps?: DataGap[]
  trackComplete?: boolean
  passed?: boolean
  completionRate?: number
}

export interface CloseLoopReport {
  taskId?: string
  taskName?: string
  terminalCount?: number
  totalDistanceM?: number
  durationSec?: number
  plannedWaypointCount?: number
  finishedWaypointCount?: number
  missedDeviceCount?: number
  dataGapCount?: number
  defectCount?: number
  completionRate?: number
  passed?: boolean
  closeStatus?: string
  executiveSummary?: string
  generateTime?: string
}

export interface CloseLoopResult {
  success?: boolean
  taskId?: string
  closeStatus?: string
  completionRate?: number
  report?: CloseLoopReport
  verify?: IntegrityVerifyResult
  rescheduled?: boolean
  reinspectTaskId?: string
  reinspectRoutePayload?: UavRouteDispatchPayload
  message?: string
}

export interface CloseLoopAudit {
  id?: number
  taskId?: string
  workOrderId?: number
  taskName?: string
  closeStatus?: string
  completionRate?: number
  missedItemsJson?: string
  reportJson?: string
  reportSummary?: string
  rescheduleTaskId?: string
  rescheduleCount?: number
  closeTime?: string
}

export const closeLoopIngest = (data: CloseLoopReportRequest) =>
  request<CloseLoopResult>({ url: '/drone/closeloop/ingest', method: 'post', data })

export const closeLoopRecent = (limit = 30) =>
  request<CloseLoopAudit[]>({ url: '/drone/closeloop/audit/recent', method: 'get', params: { limit } })

export const closeLoopLatestByTask = (taskId: string) =>
  request<CloseLoopAudit>({ url: `/drone/closeloop/audit/${taskId}`, method: 'get' })

export const closeLoopExportAudit = (limit = 200) =>
  request({
    url: '/drone/closeloop/audit/export',
    method: 'get',
    params: { limit },
    responseType: 'blob'
  } as any)
