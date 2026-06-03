import { PATROL_CORRIDOR_Z0, PATROL_TOWER_XS } from './scenePatrolLayout'

/** 杆塔编号 1~5 是否开启火情模拟 */
const fireByTower = new Map<number, boolean>()

export function setTowerFireSimulation(towerIndex: number, enabled: boolean): void {
  if (towerIndex < 1 || towerIndex > PATROL_TOWER_XS.length) return
  fireByTower.set(towerIndex, enabled)
}

export function isTowerFireEnabled(towerIndex: number): boolean {
  return fireByTower.get(towerIndex) === true
}

export function listFireEnabledTowers(): number[] {
  const out: number[] = []
  for (let i = 1; i <= PATROL_TOWER_XS.length; i++) {
    if (isTowerFireEnabled(i)) out.push(i)
  }
  return out
}

/** 根据场景 X 坐标匹配最近杆塔（拍照点/航点） */
export function resolveTowerIndexFromSceneX(x: number, z = PATROL_CORRIDOR_Z0): number | null {
  let bestIdx = -1
  let bestD = 120
  PATROL_TOWER_XS.forEach((tx, i) => {
    const dx = Math.abs(x - tx)
    const dz = Math.abs(z - PATROL_CORRIDOR_Z0)
    const d = dx + dz * 0.15
    if (d < bestD) {
      bestD = d
      bestIdx = i
    }
  })
  return bestIdx >= 0 ? bestIdx + 1 : null
}

export function towerScenePosition(towerIndex: number): { x: number; y: number; z: number } | null {
  const i = towerIndex - 1
  if (i < 0 || i >= PATROL_TOWER_XS.length) return null
  return { x: PATROL_TOWER_XS[i]!, y: 8, z: PATROL_CORRIDOR_Z0 }
}
