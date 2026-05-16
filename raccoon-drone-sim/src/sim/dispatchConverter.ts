import {
  AUTO_FLIGHT_SPEED,
  FINISH_ACTION_AUTO_RTH,
  HEADING_MODE_AUTO,
  INSPECTION_POINT_SPEED,
  MAX_FLIGHT_SPEED,
  PHOTO_GIMBAL_PITCH_DEG,
  SCENE_TO_LAT_LON
} from './constants'
import { assertWaypointLimit } from './edgeService'
import type { CloudPathPoint, DjiWaypoint, DjiWaypointMission } from './types'
import type { GeoPoint, UavRouteDispatchPayload } from '@/types/droneDispatch'

const GEO_EPS = 1e-5

/** 原样拷贝坐标，避免任何换算改变后台数值 */
function cloneGeo(p: GeoPoint): GeoPoint {
  return {
    longitude: Number(p.longitude),
    latitude: Number(p.latitude),
    height: Number(p.height)
  }
}

/** 相对起飞点的场景偏移（米） */
function geoOffsetMeters(geo: GeoPoint, origin: GeoPoint): { x: number; y: number; z: number } {
  return {
    x: (geo.latitude - origin.latitude) / SCENE_TO_LAT_LON.latPerMeter,
    z: (geo.longitude - origin.longitude) / SCENE_TO_LAT_LON.lonPerMeter,
    y: geo.height - origin.height
  }
}

export interface SceneAnchor {
  x: number
  y: number
  z: number
}

function isSameGeo(a: GeoPoint, b: GeoPoint): boolean {
  return (
    Math.abs(a.longitude - b.longitude) < GEO_EPS &&
    Math.abs(a.latitude - b.latitude) < GEO_EPS &&
    Math.abs(a.height - b.height) < 0.05
  )
}

function yawTowardNextGeo(curr: GeoPoint, next: GeoPoint | undefined): number {
  if (!next) return 0
  const dx = (next.latitude - curr.latitude) / SCENE_TO_LAT_LON.latPerMeter
  const dz = (next.longitude - curr.longitude) / SCENE_TO_LAT_LON.lonPerMeter
  return (Math.atan2(dx, dz) * 180) / Math.PI
}

/**
 * 与后台 dispatch.waypoints 完全一致；若为空则用 takeoff + landing 兜底。
 */
export function resolveDispatchFlightPath(dispatch: UavRouteDispatchPayload): GeoPoint[] {
  if (dispatch.waypoints?.length > 0) {
    return dispatch.waypoints.map(cloneGeo)
  }
  const fallback: GeoPoint[] = []
  if (dispatch.takeoff) fallback.push(cloneGeo(dispatch.takeoff))
  if (dispatch.landing) {
    const last = fallback[fallback.length - 1]
    if (!last || !isSameGeo(last, dispatch.landing)) {
      fallback.push(cloneGeo(dispatch.landing))
    }
  }
  return fallback
}

/** 第一节点是否为起飞点，用于将 photo.waypointIndex（途经点序号）映射到完整路径下标 */
function viaIndexOffset(flightPath: GeoPoint[], dispatch: UavRouteDispatchPayload): number {
  if (!dispatch.takeoff || flightPath.length === 0) return 0
  return isSameGeo(flightPath[0], dispatch.takeoff) ? 1 : 0
}

function isPhotoAtPathIndex(
  pathIndex: number,
  pathPoint: GeoPoint,
  flightPath: GeoPoint[],
  dispatch: UavRouteDispatchPayload
): boolean {
  const photos = dispatch.photoWaypoints ?? []
  if (photos.length === 0) return false

  const offset = viaIndexOffset(flightPath, dispatch)

  return photos.some((photo) => {
    if (photo.waypointIndex != null && photo.waypointIndex >= 0) {
      const mapped = photo.waypointIndex + offset
      if (mapped === pathIndex) return true
    }
    return isSameGeo(photo, pathPoint)
  })
}

function photoDeviceIdsAtPathIndex(
  pathIndex: number,
  pathPoint: GeoPoint,
  flightPath: GeoPoint[],
  dispatch: UavRouteDispatchPayload
): number[] {
  const photos = dispatch.photoWaypoints ?? []
  const offset = viaIndexOffset(flightPath, dispatch)
  const ids: number[] = []

  for (const photo of photos) {
    let hit = false
    if (photo.waypointIndex != null && photo.waypointIndex >= 0) {
      hit = photo.waypointIndex + offset === pathIndex
    } else {
      hit = isSameGeo(photo, pathPoint)
    }
    if (hit && photo.deviceIds?.length) {
      for (const id of photo.deviceIds) {
        if (id != null && !ids.includes(id)) ids.push(id)
      }
    }
  }
  return ids
}

/**
 * 将后台下发 JSON 转为仿真场景航点（经纬度与 dispatch.waypoints 一致）
 */
/**
 * 转为仿真场景航点：第一个航点落在 sceneAnchor（机巢），其余按经纬度相对偏移展开。
 */
export function dispatchToCloudPath(
  dispatch: UavRouteDispatchPayload,
  sceneAnchor?: SceneAnchor
): CloudPathPoint[] {
  const flightPath = resolveDispatchFlightPath(dispatch)
  if (flightPath.length === 0) {
    throw new Error('路径规划中无有效飞行点')
  }

  const originGeo = flightPath[0]
  const anchor = sceneAnchor ?? { x: 0, y: originGeo.height, z: 0 }

  return flightPath.map((wp, i) => {
    const off = geoOffsetMeters(wp, originGeo)
    return {
      id: `wp-${i}`,
      longitude: wp.longitude,
      latitude: wp.latitude,
      height: wp.height,
      x: anchor.x + off.x,
      y: anchor.y + off.y,
      z: anchor.z + off.z,
      isPhoto: isPhotoAtPathIndex(i, wp, flightPath, dispatch)
    }
  })
}

/** 校验转换后航点与后台 waypoints 逐点一致 */
export function assertWaypointsMatchDispatch(
  dispatch: UavRouteDispatchPayload,
  djiWaypoints: DjiWaypoint[]
): void {
  const expected = resolveDispatchFlightPath(dispatch)
  if (expected.length !== djiWaypoints.length) {
    throw new Error(
      `航点数量不一致：后台 ${expected.length} 个，转换后 ${djiWaypoints.length} 个`
    )
  }
  for (let i = 0; i < expected.length; i++) {
    const e = expected[i]
    const a = djiWaypoints[i]
    if (
      Math.abs(e.longitude - a.longitude) > GEO_EPS ||
      Math.abs(e.latitude - a.latitude) > GEO_EPS ||
      Math.abs(e.height - a.height) > 0.05
    ) {
      throw new Error(
        `第 ${i + 1} 个航点坐标不一致：后台 (${e.longitude}, ${e.latitude}, ${e.height})，` +
          `转换 (${a.longitude}, ${a.latitude}, ${a.height})`
      )
    }
  }
}

/**
 * 后台路径 JSON -> 大疆 Waypoint 任务 JSON
 * waypoints 与 dispatch.waypoints 逐点同序、同坐标
 */
export function dispatchToDjiWaypointMission(
  dispatch: UavRouteDispatchPayload,
  aircraft: 'M300_RTK' | 'QUADRUPED_INSPECTION' = 'M300_RTK'
): DjiWaypointMission {
  const flightPath = resolveDispatchFlightPath(dispatch)
  if (flightPath.length === 0) {
    throw new Error('路径规划中无有效飞行点')
  }

  assertWaypointLimit(flightPath.length)

  const waypoints: DjiWaypoint[] = flightPath.map((wp, i) => {
    const next = flightPath[i + 1]
    const isPhoto = isPhotoAtPathIndex(i, wp, flightPath, dispatch)
    const deviceIds = photoDeviceIdsAtPathIndex(i, wp, flightPath, dispatch)
    const yaw = yawTowardNextGeo(wp, next)
    const actions: DjiWaypoint['actions'] = []
    if (isPhoto) {
      actions.push({ type: 'GIMBAL_ROTATE', gimbalPitchDeg: PHOTO_GIMBAL_PITCH_DEG, gimbalYawDeg: yaw })
      actions.push({ type: 'TAKE_PHOTO' })
    }
    const off = geoOffsetMeters(wp, flightPath[0])
    const anchor = { x: 0, y: 0, z: 0 }
    return {
      index: i,
      longitude: wp.longitude,
      latitude: wp.latitude,
      height: wp.height,
      speedMps: isPhoto ? INSPECTION_POINT_SPEED : AUTO_FLIGHT_SPEED,
      actions,
      deviceIds: deviceIds.length ? deviceIds : undefined,
      scenePosition: { x: anchor.x + off.x, y: anchor.y + off.y, z: anchor.z + off.z }
    }
  })

  assertWaypointsMatchDispatch(dispatch, waypoints)

  return {
    aircraft,
    maxFlightSpeed: MAX_FLIGHT_SPEED,
    autoFlightSpeed: AUTO_FLIGHT_SPEED,
    headingMode: HEADING_MODE_AUTO,
    finishAction: FINISH_ACTION_AUTO_RTH ? 'GO_HOME' : 'NO_ACTION',
    waypoints,
    missionMeta: {
      planId: dispatch.planId,
      taskId: dispatch.taskId,
      mapId: dispatch.mapId,
      uavId: dispatch.uavId,
      algorithm: dispatch.algorithm,
      deviceVisitOrder: dispatch.deviceVisitOrder ?? [],
      estimated: dispatch.estimated,
      /** 与后台 dispatch.waypoints 完全一致，便于对照 */
      sourceFlightPath: flightPath.map(cloneGeo)
    }
  }
}
