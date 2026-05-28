import request from '@/utils/request'
import type { GeoPoint, PhotoWaypoint, UavRouteDispatchPayload } from '@/api/drone'

/** LLM 槽位中间结果 */
export interface LlmTaskSlotResult {
  taskType?: string
  areaName?: string
  deviceNames?: string[]
  priority?: string
  planTime?: string
  remark?: string
  parseSource?: string
}

/** 标准化巡检任务（与 drone 下发 JSON 一致） */
export interface InspectionTask {
  planId?: number
  taskId?: number
  mapId?: number
  uavId?: number
  algorithm?: string
  takeoff?: GeoPoint
  landing?: GeoPoint
  waypoints?: GeoPoint[]
  photoWaypoints?: PhotoWaypoint[]
  estimated?: UavRouteDispatchPayload['estimated']
}

export interface NlpTaskParseResponse {
  needFollowUp: boolean
  followUpQuestion?: string
  slots?: LlmTaskSlotResult
  task?: InspectionTask
  parseSource?: string
}

export const nlpTaskParse = (userInput: string) =>
  request<NlpTaskParseResponse>({
    url: '/drone/nlp/task-parse',
    method: 'post',
    data: { userInput }
  })
