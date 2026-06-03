import request from '@/utils/request'

export interface FaultEventReport {
  deviceId?: number
  terminalId?: number
  mapId?: number
  faultType: string
  confidence?: number
  description?: string
  dataUrl?: string
  extData?: Record<string, unknown>
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
