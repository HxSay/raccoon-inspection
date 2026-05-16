import request from '@/utils/request'

export interface GeoPoint {
  longitude: number
  latitude: number
  height: number
}

export interface RoutePlanCreateRequest {
  taskId?: number
  mapId: number
  uavId: number
  startPoint: string
  endPoint: string
  pathPoints?: GeoPoint[]
  photoPoints?: GeoPoint[]
  visitOrder?: number[]
  algorithm: string
}

export interface UavRoutePlan {
  id?: number
  taskId?: number
  mapId: number
  uavId: number
  startPoint: string
  endPoint: string
  totalDistance: number
  estimatedTime: number
  estimatedBattery: number
  algorithm: string
  pathPoints: string
  photoPoints: string
  visitOrder: string
  createTime?: string
}

export interface RoutePlanPageResult {
  records: UavRoutePlan[]
  total: number
  current: number
  size: number
}

export const droneRoutePlanCreate = (data: RoutePlanCreateRequest) =>
  request({ url: '/drone/route-plan', method: 'post', data })

export const droneRoutePlanGetById = (id: number) =>
  request({ url: `/drone/route-plan/${id}`, method: 'get' })

export const droneRoutePlanPage = (params: {
  current?: number
  size?: number
  uavId?: number
  taskId?: number
}) => request({ url: '/drone/route-plan/page', method: 'get', params })

export interface UavMapOption {
  id: number
  mapName: string
  sceneType?: string
  remark?: string
}

export interface UavInfoOption {
  id: number
  uavName: string
  uavCode?: string
  mapId: number
  status?: number
}

export const droneMapOptions = () => request({ url: '/drone/options/maps', method: 'get' })

export const droneUavOptions = (mapId?: number) =>
  request({ url: '/drone/options/uavs', method: 'get', params: mapId != null ? { mapId } : {} })
