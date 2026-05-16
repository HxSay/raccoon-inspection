import * as THREE from 'three'

function hitGroundFirst(
  raycaster: THREE.Raycaster,
  world: THREE.Group,
  origin: THREE.Vector3,
  ignore: Set<THREE.Object3D>
): THREE.Vector3 | null {
  const dir = new THREE.Vector3(0, -1, 0)
  raycaster.set(origin, dir)
  const hits = raycaster.intersectObject(world, true)
  for (const h of hits) {
    let o: THREE.Object3D | null = h.object
    while (o) {
      if (ignore.has(o)) break
      o = o.parent
    }
    if (o) continue
    return h.point.clone()
  }
  return null
}

/** 将物体底面对齐到地表（沿 -Y 射线） */
export function snapObjectBottomToTerrain(
  obj: THREE.Object3D,
  world: THREE.Group,
  raycaster: THREE.Raycaster,
  ignore: Set<THREE.Object3D> = new Set()
): void {
  ignore.add(obj)
  obj.updateMatrixWorld(true)
  const box = new THREE.Box3().setFromObject(obj)
  const minY = box.min.y
  const center = box.getCenter(new THREE.Vector3())
  const top = new THREE.Vector3(center.x, center.y + 800, center.z)
  const ground = hitGroundFirst(raycaster, world, top, ignore)
  ignore.delete(obj)
  if (!ground) return
  const delta = ground.y - minY
  obj.position.y += delta
  obj.updateMatrixWorld(true)
}

/** 在 XZ 上放置：从高空向下找地，再把物体移到该 XZ 并使底贴地 */
export function placeOnTerrainAt(
  obj: THREE.Object3D,
  world: THREE.Group,
  raycaster: THREE.Raycaster,
  xz: THREE.Vector2,
  ignore: Set<THREE.Object3D>
): void {
  ignore.add(obj)
  const top = new THREE.Vector3(xz.x, 800, xz.y)
  const ground = hitGroundFirst(raycaster, world, top, ignore)
  ignore.delete(obj)
  if (!ground) {
    obj.position.set(xz.x, 0, xz.y)
    snapObjectBottomToTerrain(obj, world, raycaster, ignore)
    return
  }
  obj.position.set(ground.x, ground.y + 50, ground.z)
  obj.updateMatrixWorld(true)
  snapObjectBottomToTerrain(obj, world, raycaster, ignore)
}
