import * as THREE from 'three'

/** 输电线路挂线：左右各三相（沿 ±Z），索引 0=下、1=中、2=上 */
export interface LineTowerWireAnchors {
  left: THREE.Vector3[]
  right: THREE.Vector3[]
}

function addBrace(
  parent: THREE.Group,
  steel: THREE.MeshStandardMaterial,
  a: THREE.Vector3,
  b: THREE.Vector3,
  thickness: number
): void {
  const dir = new THREE.Vector3().subVectors(b, a)
  const len = dir.length()
  if (len < 1e-3) return
  const mid = new THREE.Vector3().addVectors(a, b).multiplyScalar(0.5)
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(thickness, thickness, len), steel)
  mesh.position.copy(mid)
  mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), dir.clone().normalize())
  mesh.castShadow = true
  parent.add(mesh)
}

/** 悬式绝缘子串，返回挂点（塔局部坐标） */
function addInsulatorHang(
  parent: THREE.Object3D,
  localYTop: number,
  localZ: number,
  discCount: number,
  mat: THREE.MeshStandardMaterial
): THREE.Vector3 {
  const g = new THREE.Group()
  g.position.set(0, localYTop, localZ)
  parent.add(g)
  let y = -0.12
  const spacing = 0.38
  for (let i = 0; i < discCount; i++) {
    const r = 0.22 - i * 0.008
    const disc = new THREE.Mesh(
      new THREE.CylinderGeometry(r * 0.42, r * 0.48, spacing * 0.9, 10),
      mat
    )
    disc.position.y = y
    disc.castShadow = true
    g.add(disc)
    y -= spacing
  }
  return new THREE.Vector3(0, localYTop + y + spacing * 0.45, localZ)
}

/**
 * 站内主构架塔（猫头式格构）：三层横担 + 绝缘子串。
 * 与变电站场景一致，供输电巡检场地复用。
 */
export function createPortalTower(
  world: THREE.Group,
  tx: number,
  tz: number,
  H: number,
  steel: THREE.MeshStandardMaterial,
  conc: THREE.MeshStandardMaterial,
  insLight: THREE.MeshStandardMaterial,
  insDark: THREE.MeshStandardMaterial
): { wireTips: THREE.Vector3[] } {
  const tower = new THREE.Group()
  tower.position.set(tx, 0, tz)
  world.add(tower)

  const base = new THREE.Mesh(new THREE.BoxGeometry(6.2, 1.9, 6.2), conc)
  base.position.y = 0.95
  base.castShadow = true
  base.receiveShadow = true
  tower.add(base)

  const corners: THREE.Vector3[] = []
  const h = H
  const steps = 9
  for (let i = 0; i <= steps; i++) {
    const t = i / steps
    const y = 1.9 + t * (h - 2)
    const w = THREE.MathUtils.lerp(2.85, 1.15, t)
    corners.push(new THREE.Vector3(w, y, w))
    corners.push(new THREE.Vector3(-w, y, w))
    corners.push(new THREE.Vector3(-w, y, -w))
    corners.push(new THREE.Vector3(w, y, -w))
  }
  for (let i = 0; i < steps; i++) {
    for (let c = 0; c < 4; c++) {
      const i0 = i * 4 + c
      const i1 = i * 4 + ((c + 1) % 4)
      const i2 = (i + 1) * 4 + c
      const i3 = (i + 1) * 4 + ((c + 1) % 4)
      addBrace(tower, steel, corners[i0], corners[i2], 0.22)
      addBrace(tower, steel, corners[i0], corners[i1], 0.2)
      addBrace(tower, steel, corners[i0], corners[i3], 0.18)
    }
  }

  const armYs = [h * 0.52, h * 0.68, h * 0.84]
  const armLens = [6.2, 9.2, 6.8]
  const tips: THREE.Vector3[] = []

  for (let li = 0; li < 3; li++) {
    const y0 = armYs[li]
    const half = armLens[li]
    const beam = new THREE.Mesh(new THREE.BoxGeometry(0.48, 0.52, half * 2 + 0.4), steel)
    beam.position.set(0, y0, 0)
    beam.castShadow = true
    tower.add(beam)

    for (const sgn of [-1, 1]) {
      const zTip = sgn * (half - 0.2)
      const tip = addInsulatorHang(tower, y0 - 0.28, zTip, li === 1 ? 12 : 10, li === 1 ? insDark : insLight)
      tips.push(new THREE.Vector3(tip.x + tx, tip.y, tip.z + tz))
    }
  }

  return { wireTips: tips }
}

/** 将构架塔 6 个挂点转为输电线路左右三相锚点 */
export function wireTipsToLineAnchors(wireTips: THREE.Vector3[]): LineTowerWireAnchors {
  return {
    left: [wireTips[0]!, wireTips[2]!, wireTips[4]!],
    right: [wireTips[1]!, wireTips[3]!, wireTips[5]!]
  }
}

/** 中相绝缘子挂点世界高度（与 createPortalTower 几何一致） */
export function portalTowerMiddleArmWorldY(H: number): number {
  return H * 0.68 - 0.28
}
