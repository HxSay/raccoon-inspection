import * as THREE from 'three'

const STORAGE_PREFIX = 'raccoon-drone-sim-scene-v1-'

export type ScenePersistTab = 'patrol' | 'substation' | 'thermal'

export interface PersistedTransform {
  x: number
  y: number
  z: number
  rotYdeg: number
  sx: number
  sy: number
  sz: number
}

export interface ScenePersistState {
  version: 1
  removedPaths: string[]
  transforms: Record<string, PersistedTransform>
}

function isLockedChain(o: THREE.Object3D): boolean {
  let p: THREE.Object3D | null = o
  while (p) {
    if (p.userData.noScenePick === true) return true
    p = p.parent
  }
  return false
}

function isPersistable(o: THREE.Object3D): o is THREE.Mesh | THREE.Line | THREE.LineSegments | THREE.Points {
  return (
    o instanceof THREE.Mesh ||
    o instanceof THREE.Line ||
    o instanceof THREE.LineSegments ||
    o instanceof THREE.Points
  )
}

/** 自 world 向下的稳定路径（类型 + 在父级 children 中的下标链），用于跨刷新识别同一网格 */
export function getStablePathToWorld(obj: THREE.Object3D, world: THREE.Group): string {
  const segs: string[] = []
  let o: THREE.Object3D | null = obj
  while (o && o !== world) {
    const p = o.parent
    if (!p) break
    const idx = p.children.indexOf(o)
    segs.push(`${o.type}:${idx}`)
    o = p
  }
  segs.reverse()
  return segs.join('/')
}

function disposeSubtreeResources(root: THREE.Object3D): void {
  root.traverse((c) => {
    if (c instanceof THREE.Mesh || c instanceof THREE.Line || c instanceof THREE.LineSegments || c instanceof THREE.Points) {
      c.geometry?.dispose()
      const m = c.material as THREE.Material | THREE.Material[] | undefined
      if (!m) return
      const list = Array.isArray(m) ? m : [m]
      for (const mat of list) {
        const std = mat as THREE.MeshStandardMaterial & { userData?: { skipDispose?: boolean } }
        if (!std.userData?.skipDispose) mat.dispose()
      }
    }
  })
}

export function loadSceneState(tab: ScenePersistTab): ScenePersistState | null {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + tab)
    if (!raw) return null
    const o = JSON.parse(raw) as ScenePersistState
    if (o.version !== 1 || !Array.isArray(o.removedPaths) || !o.transforms || typeof o.transforms !== 'object') return null
    return o
  } catch {
    return null
  }
}

export function clearSceneState(tab: ScenePersistTab): void {
  localStorage.removeItem(STORAGE_PREFIX + tab)
}

export function clearAllSceneStates(): void {
  ;(['patrol', 'substation', 'thermal'] as const).forEach((t) => clearSceneState(t))
}

/** 采集当前可编辑物体位姿（不含锁定链上的对象） */
export function capturePersistableTransforms(world: THREE.Group): Record<string, PersistedTransform> {
  const transforms: Record<string, PersistedTransform> = {}
  world.traverse((o) => {
    if (!isPersistable(o)) return
    if (isLockedChain(o)) return
    const path = getStablePathToWorld(o, world)
    const e = new THREE.Euler().setFromQuaternion(o.quaternion, 'YXZ')
    transforms[path] = {
      x: o.position.x,
      y: o.position.y,
      z: o.position.z,
      rotYdeg: (e.y * 180) / Math.PI,
      sx: o.scale.x,
      sy: o.scale.y,
      sz: o.scale.z
    }
  })
  return transforms
}

export function saveSceneState(tab: ScenePersistTab, world: THREE.Group, removedPaths: string[]): void {
  const transforms = capturePersistableTransforms(world)
  for (const rp of removedPaths) {
    delete transforms[rp]
  }
  const state: ScenePersistState = {
    version: 1,
    removedPaths: [...new Set(removedPaths)],
    transforms
  }
  localStorage.setItem(STORAGE_PREFIX + tab, JSON.stringify(state))
}

/** 先按路径删除，再应用保存的位姿（每次删除后重算路径，避免兄弟下标漂移导致漏删） */
export function applySceneState(world: THREE.Group, tab: ScenePersistTab): void {
  const state = loadSceneState(tab)
  if (!state) return

  const toRemove = new Set(state.removedPaths)
  while (toRemove.size > 0) {
    let removedOne = false
    const candidates: THREE.Object3D[] = []
    world.traverse((o) => {
      if (!isPersistable(o)) return
      if (isLockedChain(o)) return
      candidates.push(o)
    })
    for (const o of candidates) {
      const path = getStablePathToWorld(o, world)
      if (toRemove.has(path)) {
        toRemove.delete(path)
        o.parent?.remove(o)
        disposeSubtreeResources(o)
        removedOne = true
        break
      }
    }
    if (!removedOne) break
  }

  world.updateMatrixWorld(true)

  world.traverse((o) => {
    if (!isPersistable(o)) return
    if (isLockedChain(o)) return
    const path = getStablePathToWorld(o, world)
    const t = state.transforms[path]
    if (!t) return
    o.position.set(t.x, t.y, t.z)
    o.rotation.set(0, THREE.MathUtils.degToRad(t.rotYdeg), 0, 'YXZ')
    o.scale.set(t.sx, t.sy, t.sz)
    o.updateMatrixWorld(true)
  })
}

/** 合并删除路径并写回本地（删除物体时调用） */
export function appendRemovalAndPersist(tab: ScenePersistTab, world: THREE.Group, path: string): void {
  const prev = loadSceneState(tab)
  const removed = new Set(prev?.removedPaths ?? [])
  removed.add(path)
  saveSceneState(tab, world, [...removed])
}
