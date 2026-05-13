import * as THREE from 'three'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { createConcreteTexture, createGalvanizedMetalTexture, disposeTexture } from './textures'

/**
 * 火电站室内廊道巡检场景（程序化）：浅灰地坪 + 黄线通道、蓝色机组、管道、高侧窗漫射光。
 * 与输电 / 变电站场景独立；home 与 edgeService.fetchThermalPlantCloudPath 首点对齐。
 */

export interface ThermalPlantSceneBundle {
  scene: THREE.Scene
  world: THREE.Group
  homePosition: THREE.Vector3
  dispose: () => void
}

function markSkip(m: THREE.MeshStandardMaterial) {
  m.userData.skipDispose = true
}

function addYellowLine(world: THREE.Group, x0: number, z0: number, x1: number, z1: number, y: number, mat: THREE.MeshStandardMaterial): void {
  const dx = x1 - x0
  const dz = z1 - z0
  const len = Math.hypot(dx, dz) || 0.01
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.02, len), mat)
  mesh.position.set((x0 + x1) * 0.5, y, (z0 + z1) * 0.5)
  mesh.rotation.y = -Math.atan2(dx, dz)
  mesh.receiveShadow = true
  world.add(mesh)
}

export function createThermalPlantScene(renderer: THREE.WebGLRenderer): ThermalPlantSceneBundle {
  const scene = new THREE.Scene()
  scene.fog = new THREE.Fog(0xe8ecf0, 28, 120)

  const world = new THREE.Group()
  scene.add(world)

  const concTex = createConcreteTexture()
  concTex.repeat.set(48, 28)
  const metalTex = createGalvanizedMetalTexture()
  metalTex.repeat.set(6, 6)

  const floorMat = new THREE.MeshStandardMaterial({
    map: concTex,
    color: 0xc5cad1,
    roughness: 0.88,
    metalness: 0.04,
    envMapIntensity: 0.35
  })
  const yellowMat = new THREE.MeshStandardMaterial({
    color: 0xe8c820,
    roughness: 0.55,
    metalness: 0.08,
    envMapIntensity: 0.25
  })
  const bluePaint = new THREE.MeshStandardMaterial({
    color: 0x2a5a9e,
    roughness: 0.48,
    metalness: 0.22,
    envMapIntensity: 0.45
  })
  const railYellow = new THREE.MeshStandardMaterial({ color: 0xf5d030, roughness: 0.5, metalness: 0.15 })
  const pipeGreen = new THREE.MeshStandardMaterial({ color: 0x3d6b52, roughness: 0.42, metalness: 0.18 })
  const pipeWhite = new THREE.MeshStandardMaterial({ color: 0xeceef2, roughness: 0.55, metalness: 0.08 })
  const wallMat = new THREE.MeshStandardMaterial({ color: 0xd8dde4, roughness: 0.82, metalness: 0.02 })
  const cabinetMat = new THREE.MeshStandardMaterial({ color: 0xc4c8ce, roughness: 0.62, metalness: 0.12 })
  const winEmis = new THREE.MeshStandardMaterial({
    color: 0xeef6ff,
    emissive: 0xa8c8f8,
    emissiveIntensity: 0.55,
    roughness: 0.35,
    metalness: 0
  })
  ;[floorMat, yellowMat, bluePaint, railYellow, pipeGreen, pipeWhite, wallMat, cabinetMat, winEmis].forEach(markSkip)

  const floor = new THREE.Mesh(new THREE.PlaneGeometry(88, 52), floorMat)
  floor.rotation.x = -Math.PI / 2
  floor.receiveShadow = true
  world.add(floor)

  const lineY = 0.018
  addYellowLine(world, -10, -22, -10, 24, lineY, yellowMat)
  addYellowLine(world, 6, -22, 6, 24, lineY, yellowMat)

  for (let i = 0; i < 5; i++) {
    const z = -18 + i * 9
    const g = new THREE.Group()
    g.position.set(-28, 0, z)
    world.add(g)
    const base = new THREE.Mesh(new THREE.CylinderGeometry(2.8, 3.8, 3.2, 20), bluePaint)
    base.position.y = 1.6
    base.castShadow = true
    g.add(base)
    const top = new THREE.Mesh(new THREE.BoxGeometry(5.2, 4.2, 4.8), bluePaint)
    top.position.y = 4.8
    top.castShadow = true
    g.add(top)
    const rail = new THREE.Mesh(new THREE.BoxGeometry(5.6, 0.14, 0.22), railYellow)
    rail.position.y = 6.75
    rail.castShadow = true
    g.add(rail)
  }

  const backWall = new THREE.Mesh(new THREE.BoxGeometry(88, 14, 0.6), wallMat)
  backWall.position.set(0, 7, -26)
  backWall.receiveShadow = true
  world.add(backWall)

  for (let w = 0; w < 8; w++) {
    const win = new THREE.Mesh(new THREE.PlaneGeometry(3.2, 2.4), winEmis)
    win.position.set(-32 + w * 8.5, 8.5, -25.65)
    world.add(win)
  }

  for (let p = 0; p < 5; p++) {
    const pg = new THREE.Mesh(new THREE.CylinderGeometry(0.45, 0.52, 22 + p * 2, 10), p % 2 === 0 ? pipeGreen : pipeWhite)
    pg.position.set(-18 + p * 3.5, 12, -24 + p * 0.4)
    pg.rotation.z = 0.12 + p * 0.04
    pg.castShadow = true
    world.add(pg)
  }

  for (let c = 0; c < 4; c++) {
    const cab = new THREE.Mesh(new THREE.BoxGeometry(2.2, 3.6, 1.1), cabinetMat)
    cab.position.set(22, 1.8, -12 + c * 6)
    cab.castShadow = true
    world.add(cab)
  }

  const pillarMat = wallMat
  for (const px of [-32, 32]) {
    const col = new THREE.Mesh(new THREE.CylinderGeometry(0.65, 0.75, 12, 12), pillarMat)
    col.position.set(px, 6, 0)
    col.castShadow = true
    world.add(col)
  }

  scene.add(new THREE.AmbientLight(0xb8c4d8, 0.55))
  const sun = new THREE.DirectionalLight(0xfff5ec, 0.95)
  sun.position.set(-8, 26, 18)
  sun.castShadow = true
  sun.shadow.mapSize.set(2048, 2048)
  sun.shadow.camera.near = 2
  sun.shadow.camera.far = 90
  sun.shadow.camera.left = -50
  sun.shadow.camera.right = 50
  sun.shadow.camera.top = 50
  sun.shadow.camera.bottom = -50
  scene.add(sun)

  const hemi = new THREE.HemisphereLight(0xd8e6ff, 0x8a9098, 0.4)
  scene.add(hemi)

  const pmrem = new THREE.PMREMGenerator(renderer)
  pmrem.compileEquirectangularShader()
  const envRT = pmrem.fromScene(new RoomEnvironment(), 0.045)
  scene.environment = envRT.texture
  scene.background = new THREE.Color(0xe4e9ef)

  const homePosition = new THREE.Vector3(-12, 1.32, -14)

  const textures: THREE.Texture[] = [concTex, metalTex]

  const dispose = () => {
    pmrem.dispose()
    envRT.dispose()
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
    floorMat.dispose()
    yellowMat.dispose()
    bluePaint.dispose()
    railYellow.dispose()
    pipeGreen.dispose()
    pipeWhite.dispose()
    wallMat.dispose()
    cabinetMat.dispose()
    winEmis.dispose()
  }

  return { scene, world, homePosition, dispose }
}
