import * as THREE from 'three'
import { towerScenePosition } from './sceneHazard'

const fires = new Map<number, THREE.Group>()

function makeFireGroup(): THREE.Group {
  const g = new THREE.Group()
  const light = new THREE.PointLight(0xff6622, 2.2, 45, 2)
  light.position.set(0, 6, 0)
  g.add(light)
  const core = new THREE.Mesh(
    new THREE.ConeGeometry(2.5, 10, 8),
    new THREE.MeshBasicMaterial({ color: 0xff4400, transparent: true, opacity: 0.75 })
  )
  core.position.y = 5
  g.add(core)
  const glow = new THREE.Mesh(
    new THREE.SphereGeometry(4, 12, 12),
    new THREE.MeshBasicMaterial({ color: 0xffaa00, transparent: true, opacity: 0.35 })
  )
  glow.position.y = 4
  g.add(glow)
  g.userData.fireAnim = { t: 0 }
  return g
}

export function syncTowerFireVisuals(world: THREE.Group, enabledTowers: number[]): void {
  const enabled = new Set(enabledTowers)
  for (const [idx, grp] of fires) {
    if (!enabled.has(idx)) {
      world.remove(grp)
      disposeFire(grp)
      fires.delete(idx)
    }
  }
  for (const idx of enabled) {
    if (fires.has(idx)) continue
    const pos = towerScenePosition(idx)
    if (!pos) continue
    const grp = makeFireGroup()
    grp.position.set(pos.x, pos.y, pos.z)
    world.add(grp)
    fires.set(idx, grp)
  }
}

export function tickTowerFireVisuals(dt: number): void {
  for (const grp of fires.values()) {
    const anim = grp.userData.fireAnim as { t: number } | undefined
    if (!anim) continue
    anim.t += dt
    const s = 1 + Math.sin(anim.t * 8) * 0.12
    grp.scale.set(s, 1 + Math.sin(anim.t * 5) * 0.2, s)
  }
}

function disposeFire(grp: THREE.Group): void {
  grp.traverse((o) => {
    if (o instanceof THREE.Mesh) {
      o.geometry?.dispose()
      const m = o.material
      if (Array.isArray(m)) m.forEach((x) => x.dispose())
      else m?.dispose()
    }
  })
}

export function clearTowerFireVisuals(world: THREE.Group): void {
  for (const grp of fires.values()) {
    world.remove(grp)
    disposeFire(grp)
  }
  fires.clear()
}
