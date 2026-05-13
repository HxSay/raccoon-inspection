import * as THREE from 'three'
import { Sky } from 'three/examples/jsm/objects/Sky.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { createGalvanizedMetalTexture, createConcreteTexture, disposeTexture } from './textures'

/**
 * 独立「变电站」程序化场景：站内硬化地面、围墙、主构架塔、门架、主变、支柱绝缘子/刀闸、母线与悬链。
 * 与输电线路巡检场地（scene.ts）无共享网格，仅复用程序化贴图工具。
 */

export interface SubstationSceneBundle {
  scene: THREE.Scene
  dispose: () => void
}

function markSkip(m: THREE.MeshStandardMaterial) {
  m.userData.skipDispose = true
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

function addSagWire(
  world: THREE.Group,
  a: THREE.Vector3,
  b: THREE.Vector3,
  mat: THREE.MeshStandardMaterial,
  radius: number,
  sagFactor: number
): void {
  const dist = a.distanceTo(b)
  if (dist < 0.08) return
  const mid = new THREE.Vector3().addVectors(a, b).multiplyScalar(0.5)
  mid.y -= dist * sagFactor
  const curve = new THREE.CatmullRomCurve3([a.clone(), mid, b.clone()])
  const tube = new THREE.Mesh(new THREE.TubeGeometry(curve, 28, radius, 6, false), mat)
  tube.castShadow = true
  world.add(tube)
}

/** 深色瓷绝缘子串（简化碟片），返回串底端挂点（局部） */
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

function createGantry(
  world: THREE.Group,
  x: number,
  z: number,
  beamY: number,
  span: number,
  legH: number,
  steel: THREE.MeshStandardMaterial,
  conc: THREE.MeshStandardMaterial,
  insDark: THREE.MeshStandardMaterial
): { beamLeft: THREE.Vector3; beamRight: THREE.Vector3; beamMid: THREE.Vector3 } {
  const g = new THREE.Group()
  g.position.set(x, 0, z)
  world.add(g)

  const pad = new THREE.Mesh(new THREE.BoxGeometry(5, 0.35, 4), conc)
  pad.position.y = 0.175
  pad.receiveShadow = true
  g.add(pad)

  const legT = 0.38
  const half = span * 0.5
  for (const sx of [-half, half]) {
    const leg = new THREE.Mesh(new THREE.BoxGeometry(legT, legH, legT), steel)
    leg.position.set(sx, legH * 0.5 + 0.35, 0)
    leg.castShadow = true
    g.add(leg)
  }

  const beam = new THREE.Mesh(new THREE.BoxGeometry(span + 0.6, 0.55, 0.55), steel)
  beam.position.set(0, beamY, 0)
  beam.castShadow = true
  g.add(beam)

  const hangY = beamY - 0.35
  const tipZ = 0.55
  const left = addInsulatorHang(g, hangY, -half + tipZ, 10, insDark)
  const right = addInsulatorHang(g, hangY, half - tipZ, 10, insDark)
  const mid = addInsulatorHang(g, hangY, 0, 8, insDark)
  return {
    beamLeft: new THREE.Vector3(x + left.x, left.y, z + left.z),
    beamRight: new THREE.Vector3(x + right.x, right.y, z + right.z),
    beamMid: new THREE.Vector3(x + mid.x, mid.y, z + mid.z)
  }
}

function createTransformer(
  world: THREE.Group,
  x: number,
  z: number,
  bodyW: number,
  bodyH: number,
  tankMat: THREE.MeshStandardMaterial,
  finMat: THREE.MeshStandardMaterial,
  conc: THREE.MeshStandardMaterial
): THREE.Vector3 {
  const pad = new THREE.Mesh(new THREE.BoxGeometry(bodyW + 2.2, 0.4, bodyW + 1.6), conc)
  pad.position.set(x, 0.2, z)
  pad.receiveShadow = true
  world.add(pad)

  const body = new THREE.Mesh(new THREE.BoxGeometry(bodyW, bodyH, bodyW * 0.85), tankMat)
  body.position.set(x, bodyH * 0.5 + 0.4, z)
  body.castShadow = true
  world.add(body)

  const finCount = 14
  for (let i = 0; i < finCount; i++) {
    const fx = x + bodyW * 0.52
    const fz = z + (i / (finCount - 1) - 0.5) * bodyW * 0.75
    const fin = new THREE.Mesh(new THREE.BoxGeometry(0.12, bodyH * 0.92, 0.22), finMat)
    fin.position.set(fx, body.position.y, fz)
    fin.castShadow = true
    world.add(fin)
  }

  const bushingY = 0.4 + bodyH + 0.45
  return new THREE.Vector3(x, bushingY, z)
}

function createDisconnectorRow(
  world: THREE.Group,
  z: number,
  steel: THREE.MeshStandardMaterial,
  insDark: THREE.MeshStandardMaterial,
  conc: THREE.MeshStandardMaterial
): THREE.Vector3[] {
  const tops: THREE.Vector3[] = []
  const xs = [-6, 2, 10, 18, 26]
  for (const x of xs) {
    const base = new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.42, 0.5, 8), conc)
    base.position.set(x, 0.25, z)
    base.receiveShadow = true
    world.add(base)

    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.14, 0.16, 5.2, 8), steel)
    pole.position.set(x, 2.85, z)
    pole.castShadow = true
    world.add(pole)

    let hy = 5.6
    for (let k = 0; k < 6; k++) {
      const rib = new THREE.Mesh(new THREE.CylinderGeometry(0.26, 0.3, 0.28, 10), insDark)
      rib.position.set(x, hy, z)
      rib.castShadow = true
      world.add(rib)
      hy += 0.26
    }
    tops.push(new THREE.Vector3(x, hy - 0.1, z))
  }
  return tops
}

/**
 * 站内主构架塔（猫头式简化）：三层横担 + 绝缘子串底端挂点（局部坐标，相对塔根）。
 */
function createPortalTower(
  world: THREE.Group,
  tx: number,
  tz: number,
  H: number,
  steel: THREE.MeshStandardMaterial,
  conc: THREE.MeshStandardMaterial,
  insLight: THREE.MeshStandardMaterial,
  insDark: THREE.MeshStandardMaterial
): {
  wireTips: THREE.Vector3[]
} {
  const tower = new THREE.Group()
  tower.position.set(tx, 0, tz)
  world.add(tower)

  const base = new THREE.Mesh(new THREE.BoxGeometry(6.2, 1.9, 6.2), conc)
  base.position.y = 0.95
  base.castShadow = true
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

export function createSubstationScene(renderer: THREE.WebGLRenderer): SubstationSceneBundle {
  const scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0xd8e4f5, 0.0085)

  const world = new THREE.Group()
  scene.add(world)

  const metalMap = createGalvanizedMetalTexture()
  const concMap = createConcreteTexture()

  const steel = new THREE.MeshStandardMaterial({
    map: metalMap,
    color: 0x9aa3ad,
    metalness: 0.52,
    roughness: 0.44,
    envMapIntensity: 0.75
  })
  const concMat = new THREE.MeshStandardMaterial({ map: concMap, color: 0xc4cad1, roughness: 0.9, metalness: 0.04 })
  const groundMat = new THREE.MeshStandardMaterial({
    map: concMap,
    color: 0xd8dde3,
    roughness: 0.88,
    metalness: 0.02,
    envMapIntensity: 0.12
  })
  const tankMat = new THREE.MeshStandardMaterial({
    color: 0x6d737a,
    roughness: 0.55,
    metalness: 0.35,
    envMapIntensity: 0.45
  })
  const finMat = new THREE.MeshStandardMaterial({ color: 0x5a6066, roughness: 0.48, metalness: 0.4 })
  const insLight = new THREE.MeshStandardMaterial({
    color: 0xd8e2ea,
    roughness: 0.38,
    metalness: 0.05,
    envMapIntensity: 0.3
  })
  const insDark = new THREE.MeshStandardMaterial({
    color: 0x1a1c1f,
    roughness: 0.32,
    metalness: 0.12,
    envMapIntensity: 0.25
  })
  const wireMat = new THREE.MeshStandardMaterial({
    map: metalMap,
    color: 0x121418,
    metalness: 0.48,
    roughness: 0.45,
    envMapIntensity: 0.35
  })
  ;[steel, concMat, groundMat, tankMat, finMat, insLight, insDark, wireMat].forEach(markSkip)

  const ground = new THREE.Mesh(new THREE.PlaneGeometry(112, 112), groundMat)
  ground.rotation.x = -Math.PI / 2
  ground.receiveShadow = true
  world.add(ground)

  const wallH = 1.35
  const wallT = 0.38
  const extent = 44
  const wallY = wallH * 0.5
  const wallMat = concMat
  const mkWall = (cx: number, cz: number, len: number, rotY: number) => {
    const w = new THREE.Mesh(new THREE.BoxGeometry(len, wallH, wallT), wallMat)
    w.position.set(cx, wallY, cz)
    w.rotation.y = rotY
    w.castShadow = true
    w.receiveShadow = true
    world.add(w)
  }
  mkWall(0, -extent, extent * 2 + wallT, 0)
  mkWall(0, extent, extent * 2 + wallT, 0)
  mkWall(-extent, 0, extent * 2 + wallT, Math.PI / 2)
  mkWall(extent, 0, extent * 2 + wallT, Math.PI / 2)

  const towerCx = -14
  const towerCz = -10
  const towerH = 40
  const { wireTips } = createPortalTower(world, towerCx, towerCz, towerH, steel, concMat, insLight, insDark)

  const g1 = createGantry(world, 20, -6, 15.5, 22, 15.2, steel, concMat, insDark)
  const g2 = createGantry(world, 30, 12, 12.5, 16, 12.5, steel, concMat, insDark)

  const tTop1 = createTransformer(world, 8, 22, 4.2, 5.2, tankMat, finMat, concMat)
  const tTop2 = createTransformer(world, -4, 26, 3.6, 4.4, tankMat, finMat, concMat)
  const tTop3 = createTransformer(world, -14, 22, 3.4, 4.0, tankMat, finMat, concMat)

  const discTops = createDisconnectorRow(world, 32, steel, insDark, concMat)

  world.updateMatrixWorld(true)

  const sag = 0.095
  const wr = 0.045
  for (let i = 0; i < 3; i++) {
    const a = wireTips[i * 2]
    const b = wireTips[i * 2 + 1]
    const mid = new THREE.Vector3().addVectors(a, b).multiplyScalar(0.5)
    mid.y -= a.distanceTo(b) * sag * 0.35
    addSagWire(world, a, mid, wireMat, wr * 0.85, sag * 0.8)
    addSagWire(world, mid, b, wireMat, wr * 0.85, sag * 0.8)
  }

  addSagWire(world, wireTips[0], g1.beamLeft, wireMat, wr, sag)
  addSagWire(world, wireTips[2], g1.beamMid, wireMat, wr, sag)
  addSagWire(world, wireTips[4], g1.beamRight, wireMat, wr, sag)

  addSagWire(world, g1.beamLeft, g2.beamLeft, wireMat, wr * 0.9, sag * 1.05)
  addSagWire(world, g1.beamMid, g2.beamMid, wireMat, wr * 0.9, sag * 1.05)
  addSagWire(world, g1.beamRight, g2.beamRight, wireMat, wr * 0.9, sag * 1.05)

  addSagWire(world, g2.beamLeft, tTop1, wireMat, wr * 0.75, sag * 1.2)
  addSagWire(world, g2.beamMid, tTop2, wireMat, wr * 0.75, sag * 1.2)
  addSagWire(world, g2.beamRight, tTop3, wireMat, wr * 0.75, sag * 1.2)

  if (discTops.length >= 3) {
    addSagWire(world, g2.beamMid, discTops[2], wireMat, wr * 0.55, sag * 1.4)
    addSagWire(world, discTops[1], discTops[3], wireMat, wr * 0.4, sag * 0.9)
  }

  const sky = new Sky()
  sky.scale.setScalar(420000)
  scene.add(sky)
  const sunVec = new THREE.Vector3()
  const phi = THREE.MathUtils.degToRad(90 - 36)
  const theta = THREE.MathUtils.degToRad(178)
  sunVec.setFromSphericalCoords(1, phi, theta)
  sky.material.uniforms['sunPosition'].value.copy(sunVec)

  scene.add(new THREE.HemisphereLight(0xd2e8ff, 0x6a7268, 0.42))
  const sun = new THREE.DirectionalLight(0xfff6ea, 0.82)
  sun.position.copy(sunVec).multiplyScalar(160)
  sun.castShadow = true
  sun.shadow.mapSize.set(3072, 3072)
  sun.shadow.bias = -0.0002
  sun.shadow.normalBias = 0.018
  sun.shadow.camera.near = 1
  sun.shadow.camera.far = 180
  sun.shadow.camera.left = -70
  sun.shadow.camera.right = 70
  sun.shadow.camera.top = 70
  sun.shadow.camera.bottom = -70
  scene.add(sun)

  const pmrem = new THREE.PMREMGenerator(renderer)
  pmrem.compileEquirectangularShader()
  const envRT = pmrem.fromScene(new RoomEnvironment(), 0.035)
  scene.environment = envRT.texture
  scene.background = new THREE.Color(0xb4cce8)

  const textures: THREE.Texture[] = [metalMap, concMap]

  const dispose = () => {
    pmrem.dispose()
    envRT.dispose()
    sky.geometry.dispose()
    ;(sky.material as THREE.Material).dispose()
    textures.forEach((t) => disposeTexture(t))
    scene.environment = null
    scene.background = null
    scene.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry.dispose()
        const m = obj.material as THREE.MeshStandardMaterial & { userData?: { skipDispose?: boolean } }
        if (Array.isArray(m)) {
          m.forEach((x) => {
            if (!x.userData?.skipDispose) x.dispose()
          })
        } else if (!m.userData?.skipDispose) {
          m.dispose()
        }
      }
    })
    steel.dispose()
    concMat.dispose()
    groundMat.dispose()
    tankMat.dispose()
    finMat.dispose()
    insLight.dispose()
    insDark.dispose()
    wireMat.dispose()
  }

  return { scene, dispose }
}
