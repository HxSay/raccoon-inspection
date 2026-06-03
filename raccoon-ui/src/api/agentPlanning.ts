import request from '@/utils/request'
import type { SimulationMissionReport } from './agentPlanningTypes'

export type { SimulationMissionReport }

export const completeAgentSimulation = (data: {
  workOrderId?: number
  dispatchTaskId?: string
  remark?: string
  missionReport?: SimulationMissionReport
}) =>
  request({
    url: '/cmms/agent/planning/complete-simulation',
    method: 'post',
    data
  })

export const AGENT_ACTIVE_WORK_ORDER_KEY = 'raccoon_agent_active_work_order_id'
export const AGENT_ACTIVE_DISPATCH_TASK_KEY = 'raccoon_agent_active_dispatch_task_id'

/** 裁剪仿真上报载荷，避免超大 base64 撑爆请求 */
export function slimMissionReport(report: {
  startedAt?: number
  finishedAt?: number
  durationSec?: number
  distanceM?: number
  photos?: Array<{
    id?: string
    waypointIndex?: number
    timestamp?: number
    gps?: { latitude?: number; longitude?: number; altitudeM?: number }
    imageDataUrl?: string
  }>
  aiResults?: SimulationMissionReport['aiResults']
  multimodalSamples?: Array<{
    id?: string
    waypointIndex?: number
    modalityType?: string
    payload?: Record<string, unknown>
    previewDataUrl?: string
  }>
  telemetrySent?: number
  multimodalUpload?: { sessionId?: number; sampleCount?: number } | { error?: string }
}): SimulationMissionReport {
  const trimUrl = (url?: string) =>
    url && url.length <= 900 ? url : url ? url.slice(0, 900) : undefined

  return {
    durationSec: report.durationSec,
    distanceM: report.distanceM,
    photoCount: report.photos?.length ?? 0,
    telemetrySent: report.telemetrySent,
    multimodalUploaded:
      report.multimodalUpload != null && !('error' in (report.multimodalUpload as object)),
    multimodalError:
      report.multimodalUpload && 'error' in report.multimodalUpload
        ? String((report.multimodalUpload as { error: string }).error)
        : undefined,
    photos: (report.photos ?? []).map((p) => ({
      id: p.id,
      waypointIndex: p.waypointIndex,
      latitude: p.gps?.latitude,
      longitude: p.gps?.longitude,
      altitudeM: p.gps?.altitudeM,
      previewDataUrl: trimUrl(p.imageDataUrl)
    })),
    aiResults: report.aiResults,
    multimodalSamples: (report.multimodalSamples ?? []).map((s) => ({
      waypointIndex: s.waypointIndex,
      modalityType: s.modalityType,
      payload: s.payload,
      previewDataUrl: trimUrl(s.previewDataUrl)
    }))
  }
}
