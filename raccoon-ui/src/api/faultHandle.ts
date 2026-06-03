import request from '@/utils/request'
import type { UavRouteDispatchPayload } from '@/api/drone'

export interface FaultEventReport {
  deviceId?: number
  terminalId?: number
  mapId?: number
  faultType: string
  confidence?: number
  description?: string
  dataUrl?: string
  extData?: Record<string, unknown>
  /** 仿真演示：true 时不做 5 分钟同设备同类型合并 */
  skipDedupe?: boolean
}

export interface FaultHandleResult {
  eventId?: string
  status?: string
  level?: string
  message?: string
  plan?: {
    action?: string
    reinspectDeadline?: string
    expandScope?: boolean
    pauseOtherTasks?: boolean
  }
  dispatch?: {
    success?: boolean
    reinspectTaskId?: string
    assignedTerminalId?: number
    message?: string
    routePayload?: UavRouteDispatchPayload
    expandedDeviceCount?: number
  }
  expandedScope?: {
    primaryDeviceId?: number
    relatedDeviceIds?: number[]
  }
}

export interface FaultAuditVO {
  id?: number
  eventId?: string
  faultLevel?: string
  responseAction?: string
  reinspectTaskId?: string
  handleResult?: string
  handleTime?: string
}

export const faultIngest = (data: FaultEventReport) =>
  request<FaultHandleResult>({ url: '/drone/fault/ingest', method: 'post', data })

export const faultOfflineScenario = (terminalId: number, offlineDurationSec = 60) =>
  request<FaultHandleResult>({
    url: '/drone/fault/scenario/offline',
    method: 'post',
    params: { terminalId, offlineDurationSec }
  })

export const faultDisasterScenario = (data: {
  mapId?: number
  alertType?: string
  description?: string
  severity?: number
}) => request<FaultHandleResult>({ url: '/drone/fault/scenario/disaster', method: 'post', data })

export const faultRecentAudits = (limit = 20) =>
  request<FaultAuditVO[]>({ url: '/drone/fault/audit/recent', method: 'get', params: { limit } })
