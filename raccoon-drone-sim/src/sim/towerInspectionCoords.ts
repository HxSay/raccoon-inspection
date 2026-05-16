import { PATROL_LANE_Z_SPACING_M } from './constants'
import { sceneToGps } from './aiDetect'
import { portalTowerMiddleArmWorldY } from './portalTower'
import {
  PATROL_CORRIDOR_Z0,
  PATROL_GROUND_STATION,
  PATROL_NEST_HOME,
  PATROL_TOWER_HEIGHTS,
  PATROL_TOWER_XS
} from './scenePatrolLayout'

export interface TowerCoordRow {
  index: number
  label: string
  scene: { x: number; y: number; z: number }
  longitude: number
  latitude: number
  /** WGS84 高度：航点填后台；杆塔中心行为塔脚海拔 */
  height: number
  role: 'tower_center' | 'photo_inspection' | 'drone_nest' | 'ground_station' | 'home_flight'
  /** 杆塔构架全高（米），仅展示用 */
  structuralHeight?: number
}

function toRow(
  partial: Omit<TowerCoordRow, 'longitude' | 'latitude' | 'height'> & { scene: { x: number; y: number; z: number } }
): TowerCoordRow {
  const gps = sceneToGps(partial.scene.x, partial.scene.y, partial.scene.z)
  return {
    ...partial,
    longitude: round6(gps.longitude),
    latitude: round6(gps.latitude),
    height: Math.round(gps.altitudeM * 100) / 100
  }
}

/** 5 基杆塔 + 推荐巡检拍照点（lane 0） */
export function getPatrolTowerCoordinates(laneIndex = 0): TowerCoordRow[] {
  const zRow = PATROL_CORRIDOR_Z0 + laneIndex * PATROL_LANE_Z_SPACING_M
  const rows: TowerCoordRow[] = []

  PATROL_TOWER_XS.forEach((x, i) => {
    const h = PATROL_TOWER_HEIGHTS[i]! + (laneIndex > 0 ? (laneIndex - 1) * 1.1 : 0)
    const center = toRow({
      index: i + 1,
      label: `杆塔 ${i + 1}`,
      scene: { x, y: 4, z: zRow },
      role: 'tower_center'
    })
    center.structuralHeight = h
    rows.push(center)

    const photoY = Math.max(portalTowerMiddleArmWorldY(h) + 8, 36)
    rows.push(
      toRow({
        index: i + 1,
        label: `杆塔 ${i + 1} · 巡检拍照`,
        scene: { x, y: photoY, z: zRow - 12 },
        role: 'photo_inspection'
      })
    )
  })

  return rows
}

/** 机巢、地面站（与场景模型摆放一致） */
export function getPatrolFacilityCoordinates(laneIndex = 0): TowerCoordRow[] {
  const z = PATROL_NEST_HOME.z + laneIndex * PATROL_LANE_Z_SPACING_M
  return [
    toRow({
      index: 0,
      label: '无人机巢',
      scene: { x: PATROL_NEST_HOME.x, y: PATROL_NEST_HOME.y, z },
      role: 'drone_nest'
    }),
    toRow({
      index: 0,
      label: '无人机地面站',
      scene: { x: PATROL_GROUND_STATION.x, y: PATROL_GROUND_STATION.y, z: PATROL_GROUND_STATION.z },
      role: 'ground_station'
    })
  ]
}

export function getPatrolHomeCoordinates(laneIndex = 0): TowerCoordRow[] {
  const z = PATROL_NEST_HOME.z + laneIndex * PATROL_LANE_Z_SPACING_M
  return [
    toRow({
      index: 0,
      label: '起降航点（建议）',
      scene: { x: PATROL_NEST_HOME.x, y: 35, z },
      role: 'home_flight'
    })
  ]
}

export function getPatrolReferenceTable(laneIndex = 0): TowerCoordRow[] {
  return [
    ...getPatrolFacilityCoordinates(laneIndex),
    ...getPatrolHomeCoordinates(laneIndex),
    ...getPatrolTowerCoordinates(laneIndex)
  ]
}

function round6(n: number) {
  return Math.round(n * 1_000_000) / 1_000_000
}

export function formatCoordForBackend(row: TowerCoordRow): string {
  return `${row.longitude},${row.latitude},${row.height}`
}
