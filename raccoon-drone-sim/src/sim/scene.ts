import * as THREE from 'three'
import { Sky } from 'three/examples/jsm/objects/Sky.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { createTerrainRoughnessTexture, createGalvanizedMetalTexture, createConcreteTexture, disposeTexture } from './textures'
import { PATROL_LANE_COUNT, PATROL_LANE_Z_SPACING_M } from './constants'
import {
  PATROL_CORRIDOR_Z0,
  PATROL_GROUND_STATION,
  PATROL_NEST_HOME,
  PATROL_TOWER_HEIGHTS,
  PATROL_TOWER_XS
} from './scenePatrolLayout'
import { createTowerCoordMarkers } from './towerCoordMarkers'
import { createPortalTower, wireTipsToLineAnchors, type LineTowerWireAnchors } from './portalTower'

/**
 * 写实风格电网巡检场景：草原式起伏地表、构架塔（与变电站同款）与导线、物理天空与 IBL。
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
  const sagM = Math.min(span * 0.028, Math.max(8.5, span * sagScale))
  mid.y -= sagM
  const curve = new THREE.CatmullRomCurve3([a.clone(), mid, b.clone()], false, 'catmullrom', 0.35)
  const tubularSegments = Math.min(128, Math.max(48, Math.floor(span * 0.35)))
  const tube = new THREE.Mesh(new THREE.TubeGeometry(curve, tubularSegments, 0.052, 6, false), mat)
  tube.castShadow = true
  world.add(tube)

  const nSp = Math.min(10, Math.max(2, Math.floor(span / 28)))
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
  anchors: LineTowerWireAnchors[],
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
  scene.fog = new THREE.FogExp2(0xd4e8dc, 0.00075)

  const world = new THREE.Group()
  scene.add(world)

  const groundRough = createTerrainRoughnessTexture()
  groundRough.repeat.set(28, 28)
  const metal = createGalvanizedMetalTexture()
  const conc = createConcreteTexture()

  const terrainSize = 1400
  const terrainGeo = new THREE.PlaneGeometry(terrainSize, terrainSize, 160, 160)
  terrainGeo.rotateX(-Math.PI / 2)
  displaceTerrain(terrainGeo, 0.55)

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
  ;[concMat, steel, insLight, insDark, wireMat, spacerMat].forEach(markSkip)

  const corridorZ0 = PATROL_CORRIDOR_Z0
  for (let lane = 0; lane < PATROL_LANE_COUNT; lane++) {
    const zRow = corridorZ0 + lane * PATROL_LANE_Z_SPACING_M
    const towerAnchors: LineTowerWireAnchors[] = []
    for (let i = 0; i < xs.length; i++) {
      const h = heights[i] + (PATROL_LANE_COUNT > 1 ? (lane - 1) * 1.1 : 0)
      const { wireTips } = createPortalTower(world, xs[i], zRow, h, steel, concMat, insLight, insDark)
      towerAnchors.push(wireTipsToLineAnchors(wireTips))
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
  sun.shadow.camera.far = 900
  sun.shadow.camera.left = -520
  sun.shadow.camera.right = 520
  sun.shadow.camera.top = 520
  sun.shadow.camera.bottom = -520
  scene.add(sun)

  const pmrem = new THREE.PMREMGenerator(renderer)
  pmrem.compileEquirectangularShader()
  const envRT = pmrem.fromScene(new RoomEnvironment(), 0.04)
  scene.environment = envRT.texture
  scene.background = new THREE.Color(0xb5d4ec)

  const towerMarkers = createTowerCoordMarkers(world)

  const corridorHomes: THREE.Vector3[] = []
  for (let lane = 0; lane < PATROL_LANE_COUNT; lane++) {
    corridorHomes.push(
      new THREE.Vector3(
        PATROL_NEST_HOME.x,
        PATROL_NEST_HOME.y,
        PATROL_NEST_HOME.z + lane * PATROL_LANE_Z_SPACING_M
      )
    )
  }
  const homePosition = corridorHomes[0]!.clone()
  const terminalPosition = new THREE.Vector3(
    PATROL_GROUND_STATION.x,
    PATROL_GROUND_STATION.y,
    PATROL_GROUND_STATION.z
  )

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
    insLight.dispose()
    insDark.dispose()
    wireMat.dispose()
    spacerMat.dispose()
  }

  return { scene, world, homePosition, corridorHomes, terminalPosition, dispose }
}
