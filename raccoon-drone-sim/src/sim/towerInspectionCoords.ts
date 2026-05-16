import { PATROL_LANE_Z_SPACING_M } from './constants'
import { sceneToGps } from './aiDetect'
import { PATROL_CORRIDOR_Z0, PATROL_TOWER_HEIGHTS, PATROL_TOWER_XS } from './scenePatrolLayout'

export interface TowerCoordRow {
  index: number
  label: string
  scene: { x: number; y: number; z: number }
  longitude: number
  latitude: number
  height: number
  role: 'tower_center' | 'photo_inspection' | 'home_nest' | 'home_flight'
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

/** 中相绝缘子挂点世界高度 Y */
function middleArmWorldY(towerHeight: number): number {
  const y0 = 2.4
  const hBody = towerHeight * 0.88
  return y0 + hBody * 0.52 - 0.28
}

/** 5 基杆塔 + 推荐巡检拍照点（lane 0） */
export function getPatrolTowerCoordinates(laneIndex = 0): TowerCoordRow[] {
  const zRow = PATROL_CORRIDOR_Z0 + laneIndex * PATROL_LANE_Z_SPACING_M
  const rows: TowerCoordRow[] = []

  PATROL_TOWER_XS.forEach((x, i) => {
    const h = PATROL_TOWER_HEIGHTS[i]! + (laneIndex > 0 ? (laneIndex - 1) * 1.1 : 0)
    rows.push(
      toRow({
        index: i + 1,
        label: `杆塔 ${i + 1}`,
        scene: { x, y: 4, z: zRow },
        role: 'tower_center'
      })
    )

    const photoY = Math.max(middleArmWorldY(h) + 8, 36)
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

export function getPatrolHomeCoordinates(laneIndex = 0): TowerCoordRow[] {
  const z = 40 + laneIndex * PATROL_LANE_Z_SPACING_M
  return [
    toRow({
      index: 0,
      label: '机巢（地面）',
      scene: { x: 0, y: 3, z },
      role: 'home_nest'
    }),
    toRow({
      index: 0,
      label: '起降航点（建议）',
      scene: { x: 0, y: 35, z },
      role: 'home_flight'
    })
  ]
}

export function getPatrolReferenceTable(laneIndex = 0): TowerCoordRow[] {
  return [...getPatrolHomeCoordinates(laneIndex), ...getPatrolTowerCoordinates(laneIndex)]
}

function round6(n: number) {
  return Math.round(n * 1_000_000) / 1_000_000
}

export function formatCoordForBackend(row: TowerCoordRow): string {
  return `${row.longitude},${row.latitude},${row.height}`
}
