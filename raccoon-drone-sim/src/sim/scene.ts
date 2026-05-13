import * as THREE from 'three'
import { Sky } from 'three/examples/jsm/objects/Sky.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { createGrassTexture, createTerrainRoughnessTexture, createGalvanizedMetalTexture, createConcreteTexture, disposeTexture } from './textures'

/**
 * 写实风格电网山地巡检场景：程序化地形起伏 + 贴图、角钢塔、导线、物理天空与 IBL 环境光。
 * 依赖 WebGLRenderer 生成 PMREM（调用方传入 renderer）。
 */

export interface PowerlineSceneBundle {
  scene: THREE.Scene
  world: THREE.Group
  /** 起飞 / 返航锚点（与 edgeService 航线首点 XZ 对齐） */
  homePosition: THREE.Vector3
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

/**
 * 角钢输电塔：混凝土基础 + 钢塔身 + 横担 + 绝缘子串 + 接地引下线（简化）。
 */
function createLatticeTower(
  x: number,
  z: number,
  height: number,
  world: THREE.Group,
  metalMap: THREE.Texture,
  concMap: THREE.Texture
): THREE.Vector3 {
  const tower = new THREE.Group()
  tower.position.set(x, 0, z)

  const base = new THREE.Mesh(
    new THREE.BoxGeometry(7, 2.2, 7),
    new THREE.MeshStandardMaterial({ map: concMap, roughness: 0.9, metalness: 0.08 })
  )
  base.position.y = 1.1
  base.castShadow = true
  base.receiveShadow = true
  tower.add(base)

  const steel = new THREE.MeshStandardMaterial({
    map: metalMap,
    metalness: 0.62,
    roughness: 0.38,
    envMapIntensity: 1
  })

  const legH = height * 0.92
  const spread = 2.8
  const legs = [
    [-1, -1],
    [1, -1],
    [-1, 1],
    [1, 1]
  ] as const
  for (const [lx, lz] of legs) {
    const leg = new THREE.Mesh(new THREE.BoxGeometry(0.55, legH, 0.55), steel)
    leg.position.set(lx * spread, 2.2 + legH / 2, lz * spread)
    leg.castShadow = true
    tower.add(leg)
    const brace = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.12, 4.2, 6), steel)
    brace.position.set(lx * 1.4, 5 + Math.random() * 4, lz * 1.4)
    brace.rotation.z = lx * 0.35
    brace.castShadow = true
    tower.add(brace)
  }

  const mast = new THREE.Mesh(new THREE.BoxGeometry(1.1, legH * 0.95, 1.1), steel)
  mast.position.set(0, 2.2 + (legH * 0.95) / 2, 0)
  mast.castShadow = true
  tower.add(mast)

  for (let k = 0; k < 3; k++) {
    const y = 2.8 + legH * (0.28 + k * 0.24)
    const arm = new THREE.Mesh(new THREE.BoxGeometry(20, 0.55, 0.55), steel)
    arm.position.set(0, y, 0)
    arm.castShadow = true
    tower.add(arm)
    for (const side of [-1, 1]) {
      for (let j = 0; j < 4; j++) {
        const ins = new THREE.Mesh(
          new THREE.CylinderGeometry(0.22, 0.28, 1.1, 10),
          new THREE.MeshStandardMaterial({ color: 0xd8e6ef, roughness: 0.35, metalness: 0.05 })
        )
        ins.position.set(side * (3.5 + j * 2.4), y - 0.65 - j * 0.15, 0)
        ins.castShadow = true
        tower.add(ins)
      }
    }
  }

  world.add(tower)
  return new THREE.Vector3(x, height, z)
}

function createWires(tops: THREE.Vector3[], world: THREE.Group, metal: THREE.Texture): void {
  const mat = new THREE.MeshStandardMaterial({
    map: metal,
    color: 0x2a2a2a,
    metalness: 0.75,
    roughness: 0.28,
    envMapIntensity: 0.6
  })
  for (let i = 0; i < tops.length - 1; i++) {
    const a = tops[i].clone()
    const b = tops[i + 1].clone()
    const mid = new THREE.Vector3().addVectors(a, b).multiplyScalar(0.5)
    mid.y -= 5.5
    const curve = new THREE.QuadraticBezierCurve3(a, mid, b)
    const tube = new THREE.Mesh(new THREE.TubeGeometry(curve, 48, 0.14, 8, false), mat)
    tube.castShadow = true
    world.add(tube)
  }
}

export function createPowerlineScene(renderer: THREE.WebGLRenderer): PowerlineSceneBundle {
  const scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0xc5d8ea, 0.00105)

  const world = new THREE.Group()
  scene.add(world)

  const grass = createGrassTexture()
  const groundRough = createTerrainRoughnessTexture()
  const metal = createGalvanizedMetalTexture()
  const conc = createConcreteTexture()

  const terrainGeo = new THREE.PlaneGeometry(720, 720, 160, 160)
  terrainGeo.rotateX(-Math.PI / 2)
  displaceTerrain(terrainGeo, 0.62)

  const terrainMat = new THREE.MeshStandardMaterial({
    map: grass,
    roughnessMap: groundRough,
    roughness: 1,
    metalness: 0,
    envMapIntensity: 0.06
  })
  const terrain = new THREE.Mesh(terrainGeo, terrainMat)
  terrain.receiveShadow = true
  terrain.castShadow = false
  world.add(terrain)

  const heights = [58, 64, 60, 62, 59]
  const xs = [-95, -15, 65, 145, 225]
  const tops: THREE.Vector3[] = []
  for (let i = 0; i < xs.length; i++) {
    tops.push(createLatticeTower(xs[i], -35, heights[i], world, metal, conc))
  }
  createWires(tops, world, metal)

  const sky = new Sky()
  sky.scale.setScalar(450000)
  scene.add(sky)
  const sunVec = new THREE.Vector3()
  const phi = THREE.MathUtils.degToRad(90 - 38)
  const theta = THREE.MathUtils.degToRad(185)
  sunVec.setFromSphericalCoords(1, phi, theta)
  sky.material.uniforms['sunPosition'].value.copy(sunVec)

  const hemi = new THREE.HemisphereLight(0xcfe8ff, 0x3d4a2a, 0.35)
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
  scene.background = new THREE.Color(0xb8cee8)

  const homePosition = new THREE.Vector3(0, 3, 40)
  const terminalPosition = new THREE.Vector3(-42, 0, 72)

  const textures: THREE.Texture[] = [grass, groundRough, metal, conc]

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
        const m = obj.material
        if (Array.isArray(m)) m.forEach((x) => x.dispose())
        else m.dispose()
      }
    })
  }

  return { scene, world, homePosition, terminalPosition, dispose }
}
