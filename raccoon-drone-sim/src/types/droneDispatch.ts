/** 与 raccoon-cloud-drone UavRouteDispatchPayload 对齐 */

export interface GeoPoint {
  longitude: number
  latitude: number
  height: number
}

export interface PhotoWaypoint extends GeoPoint {
  waypointIndex?: number
  deviceIds?: number[]
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

export interface HxResult<T> {
  code: number
  msg: string
  data: T
}
