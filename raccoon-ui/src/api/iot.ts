import request from '@/utils/request'

export interface UavInspectionSession {
  id: number
  uavId: number
  taskId?: number
  planId?: number
  mapId?: number
  startedAt: string
  finishedAt: string
  distanceM?: number
  sampleCount: number
  createTime?: string
}

export interface UavInspectionSample {
  id: number
  sessionId: number
  waypointIndex: number
  modalityType: string
  capturedAt: string
  longitude?: number
  latitude?: number
  height?: number
  payload: Record<string, unknown>
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export const iotInspectionSessionPage = (params: {
  current?: number
  size?: number
  uavId?: number
  taskId?: number
  planId?: number
}) =>
  request({
    url: '/iot-data/uav-inspection/session/page',
    method: 'get',
    params
  })

export const iotInspectionSamples = (sessionId: number) =>
  request({
    url: `/iot-data/uav-inspection/session/${sessionId}/samples`,
    method: 'get'
  })
