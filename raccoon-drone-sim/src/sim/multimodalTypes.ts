/** 边缘多模态巡检模态类型（与云端 uav_inspection_sample.modality_type 一致） */
export type MultimodalModalityType = 'VISIBLE' | 'THERMAL' | 'AUDIO' | 'VIBRATION' | 'TEMPERATURE'

export interface MultimodalSample {
  id: string
  waypointIndex: number
  modalityType: MultimodalModalityType
  capturedAt: number
  gps: { latitude: number; longitude: number; altitudeM: number }
  /** 模态结构化载荷（上传云端 JSON） */
  payload: Record<string, unknown>
  /** 本地预览用 data URL（可见光/热成像，可选） */
  previewDataUrl?: string
}

export interface MultimodalMissionContext {
  uavId: number
  taskId?: number
  planId?: number
  mapId?: number
}
