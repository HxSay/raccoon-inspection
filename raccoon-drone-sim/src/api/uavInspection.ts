import type { MultimodalMissionContext } from '@/sim/multimodalTypes'

export interface UavInspectionUploadSession extends MultimodalMissionContext {
  startedAt: string
  finishedAt: string
  distanceM: number
}

export interface UavInspectionUploadSample {
  waypointIndex: number
  modalityType: string
  capturedAt: string
  longitude?: number
  latitude?: number
  height?: number
  payload: Record<string, unknown>
}

export interface UavInspectionUploadBody {
  session: UavInspectionUploadSession
  samples: UavInspectionUploadSample[]
}

export interface UavInspectionUploadResult {
  sessionId: number
  sampleCount: number
}

interface HxResult<T> {
  code: number
  msg?: string
  data?: T
}

const IOT_API_BASE = import.meta.env.VITE_IOT_API_BASE ?? '/api/iot-data'

export async function postUavInspectionMultimodal(
  body: UavInspectionUploadBody
): Promise<UavInspectionUploadResult> {
  const res = await fetch(`${IOT_API_BASE}/uav-inspection/multimodal/upload`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`)
  }
  const json = (await res.json()) as HxResult<UavInspectionUploadResult>
  if (json.code !== 200 || !json.data) {
    throw new Error(json.msg || '多模态巡检结果上报失败')
  }
  return json.data
}
