/** 与 raccoon-common SimulationMissionReportDTO 对齐 */
export interface SimulationMissionReport {
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
  aiResults?: Array<{
    photoId?: string
    hasDefect?: boolean
    label?: string
    confidence?: number
  }>
  multimodalSamples?: Array<{
    waypointIndex?: number
    modalityType?: string
    payload?: Record<string, unknown>
    previewDataUrl?: string
  }>
}
