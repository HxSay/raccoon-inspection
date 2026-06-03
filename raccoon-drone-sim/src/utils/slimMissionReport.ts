import type { MissionReport } from '@/sim/types'

export interface SlimMissionReportPayload {
  durationSec?: number
  distanceM?: number
  photoCount?: number
  telemetrySent?: number
  multimodalUploaded?: boolean
  multimodalError?: string
  photos?: Array<{
    id?: string
    waypointIndex?: number
    latitude?: number
    longitude?: number
    altitudeM?: number
    previewDataUrl?: string
  }>
  aiResults?: MissionReport['aiResults']
  multimodalSamples?: Array<{
    waypointIndex?: number
    modalityType?: string
    payload?: Record<string, unknown>
    previewDataUrl?: string
  }>
  anomalyEvents?: MissionReport['anomalyEvents']
}

const trimUrl = (url?: string) => (url && url.length <= 900 ? url : url ? url.slice(0, 900) : undefined)

export function slimMissionReport(report: MissionReport): SlimMissionReportPayload {
  const upload = report.multimodalUpload
  return {
    durationSec: report.durationSec,
    distanceM: report.distanceM,
    photoCount: report.photos?.length ?? 0,
    telemetrySent: report.telemetrySent,
    multimodalUploaded: upload != null && !('error' in upload),
    multimodalError: upload && 'error' in upload ? String(upload.error) : undefined,
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
    })),
    anomalyEvents: report.anomalyEvents
  }
}
