import * as THREE from 'three'
import { Sky } from 'three/examples/jsm/objects/Sky.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { createTerrainRoughnessTexture, createGalvanizedMetalTexture, createConcreteTexture, disposeTexture } from './textures'
import { PATROL_LANE_COUNT, PATROL_LANE_Z_SPACING_M } from './constants'
import { PATROL_CORRIDOR_Z0, PATROL_TOWER_HEIGHTS, PATROL_TOWER_XS } from './scenePatrolLayout'
import { createTowerCoordMarkers } from './towerCoordMarkers'

/**
 * 写实风格电网巡检场景：草原式起伏地表、单/多排角钢塔与导线、物理天空与 IBL。
 * 依赖 WebGLRenderer 生成 PMREM（调用方传入 renderer）。
 */

export interface PowerlineSceneBundle {
  scene: THREE.Scene
  world: THREE.Group
  /** 起飞 / 返航锚点（与 edgeService 航线首点 XZ 对齐） */
  homePosition: THREE.Vector3
  /** 各并排走廊的起飞位（索引与 `fetchCloudPlannedPath(..., laneIndex)` 一致） */
  corridorHomes: THREE.Vector3[]
  /** 边缘控制终端部署世界坐标（用于场景摆放） */
  terminalPosition: THREE.Vector3
  dispose: () => void
}

/**
 * 柔和丘陵：以长波为主、振幅压低，避免「刀劈山脊」与过大坡度。
 * @param ampScale 整体高度系数（约 0.55～0.75 为平缓丘陵带）
 */
function displaceTerrain(geo: THREE.PlaneGeometry, ampScale: number): void {
  const pos = geo.attributes.position as THREE.BufferAttribute
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i)
    const z = pos.getZ(i)
    const nx = x * 0.0034
    const nz = z * 0.0034
    const h =
      Math.sin(nx * 0.88 + nz * 0.66) * 7.5 +
      Math.sin(nx * 0.42 - nz * 0.31) * 5.2 +
      Math.sin(nx * 1.55 + nz * 1.28) * 2.8 +
      Math.sin(nx * 3.1 + nz * 2.75) * 1.1
    pos.setY(i, h * ampScale)
  }
  pos.needsUpdate = true
  geo.computeVertexNormals()
}

/** 单基杆塔上的挂线点（世界坐标）：左右各三相，索引 0=下相、1=中相、2=上相 */
interface TowerWireAnchors {
  left: THREE.Vector3[]
  right: THREE.Vector3[]
}

/**
 * 在塔局部坐标系中创建一串悬式绝缘子（碟形简化），锚点挂在串的最下端（导线挂点）。
 */
function addInsulatorString(
  tower: THREE.Group,
  localX: number,
  localYBeam: number,
  localZ: number,
  discCount: number,
  discSpacing: number,
  discRadius: number,
  insMat: THREE.MeshStandardMaterial
): THREE.Object3D {
  const g = new THREE.Group()
  g.position.set(localX, localYBeam, localZ)
  tower.add(g)
  let y = -0.15
  for (let i = 0; i < discCount; i++) {
    const d = Math.max(0.12, discRadius * (0.92 - i * 0.02))
    const disc = new THREE.Mesh(
      new THREE.CylinderGeometry(d * 0.35, d * 0.42, discSpacing * 0.92, 10),
      insMat
    )
    disc.position.y = y
    disc.castShadow = true
    g.add(disc)
    y -= discSpacing
  }
  const anchor = new THREE.Object3D()
  anchor.position.y = y + discSpacing * 0.35
  g.add(anchor)
  return anchor
}

/** 角钢斜材：薄方梁连接两点（局部坐标） */
function addBrace(
  tower: THREE.Group,
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
  tower.add(mesh)
}

/**
 * 猫头 / 酒杯塔简化：四腿格构、三层横担（中相最长）、绝缘子串底端挂线。
 * 线路走向为 +X，横担沿 ±Z 伸出。
 */
function createTransmissionTower(
  x: number,
  z: number,
  height: number,
  world: THREE.Group,
  steel: THREE.MeshStandardMaterial,
  concMat: THREE.MeshStandardMaterial,
  insMat: THREE.MeshStandardMaterial
): TowerWireAnchors {
  const tower = new THREE.Group()
  tower.position.set(x, 0, z)

  const base = new THREE.Mesh(new THREE.BoxGeometry(8.5, 2.4, 8.5), concMat)
  base.position.y = 1.2
  base.castShadow = true
  base.receiveShadow = true
  tower.add(base)

  const y0 = 2.4
  const hBody = height * 0.88
  const yTop = y0 + hBody
  const baseHalf = 3.6
  const topHalf = 1.05

  const corners = [
    new THREE.Vector3(-baseHalf, y0, -baseHalf),
    new THREE.Vector3(baseHalf, y0, -baseHalf),
    new THREE.Vector3(baseHalf, y0, baseHalf),
    new THREE.Vector3(-baseHalf, y0, baseHalf)
  ]
  const topCorners = [
    new THREE.Vector3(-topHalf, yTop, -topHalf),
    new THREE.Vector3(topHalf, yTop, -topHalf),
    new THREE.Vector3(topHalf, yTop, topHalf),
    new THREE.Vector3(-topHalf, yTop, topHalf)
  ]

  for (let i = 0; i < 4; i++) {
    addBrace(tower, steel, corners[i], topCorners[i], 0.38)
    addBrace(tower, steel, corners[i], topCorners[(i + 1) % 4], 0.32)
  }
  for (let i = 0; i < 4; i++) {
    addBrace(tower, steel, corners[i], corners[(i + 1) % 4], 0.28)
  }
  const midRingY = y0 + hBody * 0.45
  const midHalf = (baseHalf + topHalf) * 0.55
  const midCorners = [
    new THREE.Vector3(-midHalf, midRingY, -midHalf),
    new THREE.Vector3(midHalf, midRingY, -midHalf),
    new THREE.Vector3(midHalf, midRingY, midHalf),
    new THREE.Vector3(-midHalf, midRingY, midHalf)
  ]
  for (let i = 0; i < 4; i++) {
    addBrace(tower, steel, corners[i], midCorners[i], 0.3)
    addBrace(tower, steel, midCorners[i], topCorners[i], 0.3)
    addBrace(tower, steel, midCorners[i], midCorners[(i + 1) % 4], 0.26)
  }

  const mast = new THREE.Mesh(new THREE.BoxGeometry(1.0, hBody * 0.22, 1.0), steel)
  mast.position.set(0, y0 + hBody * 0.52, 0)
  mast.castShadow = true
  tower.add(mast)

  /** 三层横担：下、中、上 —— 半臂长沿 Z（中相最长） */
  const armLevels: { yRel: number; halfLen: number }[] = [
    { yRel: 0.34, halfLen: 6.8 },
    { yRel: 0.52, halfLen: 10.2 },
    { yRel: 0.7, halfLen: 5.6 }
  ]

  const anchors: TowerWireAnchors = { left: [], right: [] }

  for (const arm of armLevels) {
    const yArm = y0 + hBody * arm.yRel
    const beam = new THREE.Mesh(
      new THREE.BoxGeometry(0.55, 0.55, arm.halfLen * 2 + 1.2),
      steel
    )
    beam.position.set(0, yArm, 0)
    beam.castShadow = true
    tower.add(beam)

    const discN = 10
    const discGap = 0.32
    const rDisc = 0.26
    const anchorL = addInsulatorString(tower, 0, yArm - 0.28, -arm.halfLen, discN, discGap, rDisc, insMat)
    const anchorR = addInsulatorString(tower, 0, yArm - 0.28, arm.halfLen, discN, discGap, rDisc, insMat)

    tower.updateMatrixWorld(true)
    const wl = new THREE.Vector3()
    const wr = new THREE.Vector3()
    anchorL.getWorldPosition(wl)
    anchorR.getWorldPosition(wr)
    anchors.left.push(wl)
    anchors.right.push(wr)
  }

  world.add(tower)
  return anchors
}

/** 悬链：两挂点间下垂，使用 CatmullRom 过起点、中点、终点 */
function addWireSpan(
  a: THREE.Vector3,
  b: THREE.Vector3,
  world: THREE.Group,
  mat: THREE.MeshStandardMaterial,
  spacerMat: THREE.MeshStandardMaterial,
  sagScale: number
): void {
  const mid = new THREE.Vector3().addVectors(a, b).multiplyScalar(0.5)
  const span = a.distanceTo(b)
  mid.y -= Math.min(8.5, span * sagScale)
  const curve = new THREE.CatmullRomCurve3([a.clone(), mid, b.clone()], false, 'catmullrom', 0.35)
  const tubularSegments = Math.max(48, Math.floor(span * 1.2))
  const tube = new THREE.Mesh(new THREE.TubeGeometry(curve, tubularSegments, 0.052, 6, false), mat)
  tube.castShadow = true
  world.add(tube)

  const nSp = Math.max(2, Math.floor(span / 14))
  for (let s = 1; s <= nSp; s++) {
    const u = s / (nSp + 1)
    const p = curve.getPointAt(u)
    const tan = curve.getTangentAt(u)
    const sp = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.08, 0.55), spacerMat)
    sp.position.copy(p)
    sp.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), tan.clone().normalize())
    sp.castShadow = true
    world.add(sp)
  }
}

function buildLineBetweenTowers(
  anchors: TowerWireAnchors[],
  world: THREE.Group,
  mat: THREE.MeshStandardMaterial,
  spacerMat: THREE.MeshStandardMaterial
): void {
  for (let i = 0; i < anchors.length - 1; i++) {
    const A = anchors[i]
    const B = anchors[i + 1]
    for (let k = 0; k < 3; k++) {
      addWireSpan(A.left[k], B.left[k], world, mat, spacerMat, 0.11)
      addWireSpan(A.right[k], B.right[k], world, mat, spacerMat, 0.11)
    }
  }
}

export function createPowerlineScene(renderer: THREE.WebGLRenderer): PowerlineSceneBundle {
  const scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0xd4e8dc, 0.0009)

  const world = new THREE.Group()
  scene.add(world)

  const groundRough = createTerrainRoughnessTexture()
  groundRough.repeat.set(28, 28)
  const metal = createGalvanizedMetalTexture()
  const conc = createConcreteTexture()

  const terrainGeo = new THREE.PlaneGeometry(720, 720, 192, 192)
  terrainGeo.rotateX(-Math.PI / 2)
  displaceTerrain(terrainGeo, 0.58)

  const terrainMat = new THREE.MeshStandardMaterial({
    roughnessMap: groundRough,
    color: 0x5f8f52,
    roughness: 0.92,
    metalness: 0.02,
    envMapIntensity: 0.18
  })
  const terrain = new THREE.Mesh(terrainGeo, terrainMat)
  terrain.receiveShadow = true
  terrain.castShadow = false
  world.add(terrain)

  const heights = [...PATROL_TOWER_HEIGHTS]
  const xs = [...PATROL_TOWER_XS]

  const concMat = new THREE.MeshStandardMaterial({ map: conc, roughness: 0.92, metalness: 0.06 })
  const steel = new THREE.MeshStandardMaterial({
    map: metal,
    metalness: 0.58,
    roughness: 0.4,
    envMapIntensity: 0.85
  })
  const insMat = new THREE.MeshStandardMaterial({
    color: 0xe6eef2,
    roughness: 0.42,
    metalness: 0.02,
    envMapIntensity: 0.35
  })
  const wireMat = new THREE.MeshStandardMaterial({
    map: metal,
    color: 0x1e1e1e,
    metalness: 0.55,
    roughness: 0.42,
    envMapIntensity: 0.35
  })
  const spacerMat = new THREE.MeshStandardMaterial({ color: 0x151515, metalness: 0.35, roughness: 0.65 })

  const markSkip = (m: THREE.MeshStandardMaterial) => {
    m.userData.skipDispose = true
  }
  ;[concMat, steel, insMat, wireMat, spacerMat].forEach(markSkip)

  const corridorZ0 = PATROL_CORRIDOR_Z0
  for (let lane = 0; lane < PATROL_LANE_COUNT; lane++) {
    const zRow = corridorZ0 + lane * PATROL_LANE_Z_SPACING_M
    const towerAnchors: TowerWireAnchors[] = []
    for (let i = 0; i < xs.length; i++) {
      const h = heights[i] + (PATROL_LANE_COUNT > 1 ? (lane - 1) * 1.1 : 0)
      towerAnchors.push(createTransmissionTower(xs[i], zRow, h, world, steel, concMat, insMat))
    }
    buildLineBetweenTowers(towerAnchors, world, wireMat, spacerMat)
  }

  const sky = new Sky()
  sky.scale.setScalar(450000)
  scene.add(sky)
  const sunVec = new THREE.Vector3()
  const phi = THREE.MathUtils.degToRad(90 - 38)
  const theta = THREE.MathUtils.degToRad(185)
  sunVec.setFromSphericalCoords(1, phi, theta)
  sky.material.uniforms['sunPosition'].value.copy(sunVec)

  const hemi = new THREE.HemisphereLight(0xb8daf8, 0x4a6b38, 0.4)
  scene.add(hemi)

  const sun = new THREE.DirectionalLight(0xfff5e6, 0.78)
  sun.position.copy(sunVec).multiplyScalar(220)
  sun.castShadow = true
  sun.shadow.mapSize.set(4096, 4096)
  sun.shadow.bias = -0.00025
  sun.shadow.normalBias = 0.02
  sun.shadow.camera.near = 30
  sun.shadow.camera.far = 520
  sun.shadow.camera.left = -280
  sun.shadow.camera.right = 280
  sun.shadow.camera.top = 280
  sun.shadow.camera.bottom = -280
  scene.add(sun)

  const pmrem = new THREE.PMREMGenerator(renderer)
  pmrem.compileEquirectangularShader()
  const envRT = pmrem.fromScene(new RoomEnvironment(), 0.04)
  scene.environment = envRT.texture
  scene.background = new THREE.Color(0xb5d4ec)

  const towerMarkers = createTowerCoordMarkers(world)

  const corridorHomes: THREE.Vector3[] = []
  for (let lane = 0; lane < PATROL_LANE_COUNT; lane++) {
    corridorHomes.push(new THREE.Vector3(0, 3, 40 + lane * PATROL_LANE_Z_SPACING_M))
  }
  const homePosition = corridorHomes[0]!.clone()
  const terminalPosition = new THREE.Vector3(-42, 0, 72)

  const textures: THREE.Texture[] = [groundRough, metal, conc]

  const dispose = () => {
    towerMarkers.dispose()
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
    insMat.dispose()
    wireMat.dispose()
    spacerMat.dispose()
  }

  return { scene, world, homePosition, corridorHomes, terminalPosition, dispose }
}
