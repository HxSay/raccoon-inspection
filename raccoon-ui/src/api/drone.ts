import request from '@/utils/request'

export interface GeoPoint {
  longitude: number
  latitude: number
  height: number
}

/** 拍照航点：绑定途经航点 + 巡检设备；坐标由服务端解析 */
export interface PhotoWaypoint extends GeoPoint {
  /** 途经航点下标（从 0 起） */
  waypointIndex?: number
  deviceIds?: number[]
}

export interface RoutePlanCreateRequest {
  taskId?: number
  mapId: number
  uavId: number
  startPoint: string
  endPoint: string
  pathPoints?: GeoPoint[]
  photoPoints?: PhotoWaypoint[]
  /** 兼容旧版；新数据以 photoPoints[].deviceIds 为准 */
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

export interface UavRouteDispatchPayload {
  planId?: number
  taskId?: number
  mapId: number
  uavId: number
  algorithm: string
  takeoff?: GeoPoint
  landing?: GeoPoint
  waypoints: GeoPoint[]
  photoWaypoints: PhotoWaypoint[]
  deviceVisitOrder: number[]
  estimated?: {
    distanceM: number
    durationSec: number
    batteryPct: number
  }
}

export interface RoutePlanView {
  plan: UavRoutePlan
  dispatch: UavRouteDispatchPayload
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

/** 按无人机 ID + 路径规划 ID（planId）获取下发 JSON */
export const droneRoutePlanGetDispatch = (params: { uavId: number; planId: number }) =>
  request({ url: '/drone/route-plan/dispatch', method: 'get', params })

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
