import * as THREE from 'three'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { createConcreteTexture, createGalvanizedMetalTexture, disposeTexture } from './textures'

/**
 * 火电站厂区（沙盘式、偏真实布局）：浅灰硬化地坪、厂区环路、两座自然通风冷却塔（混凝土双曲面）、
 * 一座后排双曲/高耸排烟囱（红白警示带）、长条汽机房、少量卧式罐与栈桥。地面 Y=0。
 */

/** 厂区地坪高度（世界坐标） */
export const THERMAL_PLANT_GROUND_Y = 0

export interface ThermalPlantSceneBundle {
  scene: THREE.Scene
  world: THREE.Group
  homePosition: THREE.Vector3
  dispose: () => void
}

function markSkip(m: THREE.MeshStandardMaterial) {
  m.userData.skipDispose = true
}

function addRoadDashes(
  world: THREE.Group,
  x0: number,
  z0: number,
  x1: number,
  z1: number,
  y: number,
  dashLen: number,
  gapLen: number,
  mat: THREE.MeshStandardMaterial
): void {
  const dx = x1 - x0
  const dz = z1 - z0
  const len = Math.hypot(dx, dz) || 0.01
  const ux = dx / len
  const uz = dz / len
  const step = dashLen + gapLen
  let s = 0
  while (s + dashLen <= len) {
    const mx = x0 + ux * (s + dashLen * 0.5)
    const mz = z0 + uz * (s + dashLen * 0.5)
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.02, dashLen), mat)
    mesh.position.set(mx, y, mz)
    mesh.rotation.y = -Math.atan2(dx, dz)
    mesh.receiveShadow = true
    world.add(mesh)
    s += step
  }
}

function addRoadStrip(
  world: THREE.Group,
  x0: number,
  z0: number,
  x1: number,
  z1: number,
  halfWidth: number,
  y: number,
  roadMat: THREE.MeshStandardMaterial
): void {
  const dx = x1 - x0
  const dz = z1 - z0
  const len = Math.hypot(dx, dz) || 0.01
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(halfWidth * 2, 0.06, len), roadMat)
  mesh.position.set((x0 + x1) * 0.5, y, (z0 + z1) * 0.5)
  mesh.rotation.y = -Math.atan2(dx, dz)
  mesh.receiveShadow = true
  mesh.castShadow = false
  world.add(mesh)
}

function smoothstep01(t: number): number {
  const x = THREE.MathUtils.clamp(t, 0, 1)
  return x * x * (3 - 2 * x)
}

function hyperboloidProfile(
  H: number,
  segments: number,
  rBase: number,
  rThroat: number,
  rTop: number,
  throatNormY: number
): THREE.Vector2[] {
  const pts: THREE.Vector2[] = []
  for (let i = 0; i <= segments; i++) {
    const t = i / segments
    const y = t * H
    let r: number
    if (t <= throatNormY + 1e-6) {
      const u = throatNormY < 1e-6 ? 1 : t / throatNormY
      r = rBase + (rThroat - rBase) * smoothstep01(u)
    } else {
      const u = (1 - throatNormY) < 1e-6 ? 1 : (t - throatNormY) / (1 - throatNormY)
      r = rThroat + (rTop - rThroat) * smoothstep01(u)
    }
    pts.push(new THREE.Vector2(r, y))
  }
  return pts
}

function radiusAtY(profile: THREE.Vector2[], y: number): number {
  const first = profile[0]!
  if (y <= first.y) return first.x
  const last = profile[profile.length - 1]!
  if (y >= last.y) return last.x
  for (let i = 0; i < profile.length - 1; i++) {
    const p0 = profile[i]!
    const p1 = profile[i + 1]!
    if (y <= p1.y) {
      const t = (y - p0.y) / (p1.y - p0.y + 1e-9)
      return THREE.MathUtils.lerp(p0.x, p1.x, t)
    }
  }
  return last.x
}

/** 自然通风冷却塔：混凝土双曲面 + 白口缘 + 简易蒸汽团 */
function addCoolingTower(
  world: THREE.Group,
  x: number,
  z: number,
  shellMat: THREE.MeshStandardMaterial,
  rimMat: THREE.MeshStandardMaterial,
  steamMat: THREE.MeshStandardMaterial
): void {
  const H = 14.5
  const profile = hyperboloidProfile(H, 36, 5.35, 2.35, 4.55, 0.37)
  const geo = new THREE.LatheGeometry(profile, 40)
  const shell = new THREE.Mesh(geo, shellMat)
  shell.position.set(x, 0, z)
  shell.castShadow = true
  shell.receiveShadow = true
  world.add(shell)

  const topR = profile[profile.length - 1]?.x ?? 2.7
  const rim = new THREE.Mesh(new THREE.TorusGeometry(topR + 0.06, 0.2, 8, 42), rimMat)
  rim.rotation.x = Math.PI / 2
  rim.position.set(x, H, z)
  rim.castShadow = true
  world.add(rim)

  for (let s = 0; s < 3; s++) {
    const sc = 1.4 + s * 0.85
    const puff = new THREE.Mesh(new THREE.SphereGeometry(sc, 14, 12), steamMat)
    puff.position.set(x + (s - 1) * 0.35, H + 1.2 + s * 1.1, z + (s % 2) * 0.2)
    world.add(puff)
  }
}

/** 卧式圆柱罐（轻油 / 压缩空气区常见） */
function addHorizontalTank(
  world: THREE.Group,
  cx: number,
  cy: number,
  cz: number,
  length: number,
  radius: number,
  rotY: number,
  mat: THREE.MeshStandardMaterial,
  bandMat: THREE.MeshStandardMaterial
): void {
  const tank = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, length, 20), mat)
  tank.rotation.z = Math.PI / 2
  tank.rotation.y = rotY
  tank.position.set(cx, cy, cz)
  tank.castShadow = true
  tank.receiveShadow = true
  world.add(tank)
  const ring = new THREE.Mesh(new THREE.TorusGeometry(radius * 1.02, 0.08, 8, 28), bandMat)
  ring.rotation.x = Math.PI / 2
  ring.rotation.y = rotY
  ring.position.set(cx, cy, cz)
  world.add(ring)
}

/** 后排烟囱：双曲面混凝土壳 + 红白警示带 + 底部进风格栅 */
function addChimney(
  world: THREE.Group,
  x: number,
  z: number,
  H: number,
  shellMat: THREE.MeshStandardMaterial,
  rimDark: THREE.MeshStandardMaterial,
  bandRed: THREE.MeshStandardMaterial,
  bandWhite: THREE.MeshStandardMaterial,
  padMat: THREE.MeshStandardMaterial,
  grateMat: THREE.MeshStandardMaterial,
  rBase: number,
  rThroat: number,
  rTop: number,
  throatT: number
): void {
  const profile = hyperboloidProfile(H, 44, rBase, rThroat, rTop, throatT)
  const geo = new THREE.LatheGeometry(profile, 40)
  const shell = new THREE.Mesh(geo, shellMat)
  shell.position.set(x, 0, z)
  shell.castShadow = true
  shell.receiveShadow = true
  world.add(shell)

  const topR = profile[profile.length - 1]!.x
  const rim = new THREE.Mesh(new THREE.TorusGeometry(topR + 0.05, 0.22, 8, 44), rimDark)
  rim.rotation.x = Math.PI / 2
  rim.position.set(x, H, z)
  rim.castShadow = true
  world.add(rim)

  const r0 = profile[0]!.x
  const pad = new THREE.Mesh(new THREE.CylinderGeometry(r0 * 1.02, r0 * 1.08, 0.42, 22), padMat)
  pad.position.set(x, 0.2, z)
  pad.receiveShadow = true
  pad.castShadow = true
  world.add(pad)

  const nSlat = 20
  for (let i = 0; i < nSlat; i++) {
    const ang = (i / nSlat) * Math.PI * 2
    const slat = new THREE.Mesh(new THREE.BoxGeometry(0.12, 1.85, 0.09), grateMat)
    slat.position.set(x + Math.cos(ang) * (r0 * 0.84), 1.05, z + Math.sin(ang) * (r0 * 0.84))
    slat.rotation.y = -ang
    slat.castShadow = true
    world.add(slat)
  }

  const y0 = H * 0.62
  const stripeH = 0.36
  const gap = 0.1
  let k = 0
  for (let yy = y0; yy < H - 0.55; yy += stripeH + gap) {
    const R = radiusAtY(profile, yy + stripeH * 0.5)
    const mat = k % 2 === 0 ? bandRed : bandWhite
    const tor = new THREE.Mesh(new THREE.TorusGeometry(R + 0.04, stripeH * 0.46, 10, 48), mat)
    tor.rotation.x = Math.PI / 2
    tor.position.set(x, yy + stripeH * 0.5, z)
    tor.castShadow = true
    world.add(tor)
    k++
  }
}

/** 汽机 / 锅炉岛：长条体量 + 深灰压型钢板顶 + 立面窗带 */
function addTurbineHall(
  world: THREE.Group,
  cx: number,
  cz: number,
  length: number,
  depth: number,
  wallH: number,
  wallMat: THREE.MeshStandardMaterial,
  roofMat: THREE.MeshStandardMaterial,
  winMat: THREE.MeshStandardMaterial
): void {
  const body = new THREE.Mesh(new THREE.BoxGeometry(length, wallH, depth), wallMat)
  body.position.set(cx, wallH * 0.5, cz)
  body.castShadow = true
  body.receiveShadow = true
  world.add(body)

  const roof = new THREE.Mesh(new THREE.BoxGeometry(length + 0.25, 0.75, depth + 0.25), roofMat)
  roof.position.set(cx, wallH + 0.38, cz)
  roof.castShadow = true
  world.add(roof)

  const n = Math.max(8, Math.floor(length / 2.0))
  for (let i = 0; i < n; i++) {
    const wx = cx - length * 0.5 + 1.0 + (i / (n - 1)) * (length - 2.0)
    const win = new THREE.Mesh(new THREE.PlaneGeometry(0.95, 0.48), winMat)
    win.position.set(wx, wallH * 0.52, cz + depth * 0.5 + 0.03)
    world.add(win)
  }

  const steamPipe = new THREE.Mesh(new THREE.CylinderGeometry(0.55, 0.62, wallH * 0.35, 14), wallMat)
  steamPipe.position.set(cx + length * 0.25, wallH + 1.1, cz - depth * 0.35)
  steamPipe.castShadow = true
  world.add(steamPipe)
}

/** 栈桥输煤：贴近主厂房高度，斜向连接 */
function addCoalConveyor(
  world: THREE.Group,
  ax: number,
  az: number,
  bx: number,
  bz: number,
  yDeck: number,
  mat: THREE.MeshStandardMaterial
): void {
  const mid = new THREE.Vector3().addVectors(new THREE.Vector3(ax, 0, az), new THREE.Vector3(bx, 0, bz)).multiplyScalar(0.5)
  const dx = bx - ax
  const dz = bz - az
  const len = Math.hypot(dx, dz) || 0.01
  const deck = new THREE.Mesh(new THREE.BoxGeometry(len, 0.32, 0.95), mat)
  deck.position.set(mid.x, yDeck, mid.z)
  deck.rotation.y = -Math.atan2(dx, dz)
  deck.castShadow = true
  world.add(deck)
  for (const [px, pz] of [
    [ax, az],
    [bx, bz]
  ] as const) {
    const leg = new THREE.Mesh(new THREE.BoxGeometry(0.32, yDeck - 0.2, 0.32), mat)
    leg.position.set(px, (yDeck - 0.2) * 0.5, pz)
    leg.castShadow = true
    world.add(leg)
  }
}

export function createThermalPlantScene(renderer: THREE.WebGLRenderer): ThermalPlantSceneBundle {
  const scene = new THREE.Scene()
  scene.fog = new THREE.Fog(0xc8ced6, 55, 240)

  const world = new THREE.Group()
  scene.add(world)

  const concTex = createConcreteTexture()
  concTex.repeat.set(56, 36)
  const metalTex = createGalvanizedMetalTexture()
  metalTex.repeat.set(6, 6)

  const floorMat = new THREE.MeshStandardMaterial({
    map: concTex,
    color: 0xd8dadf,
    roughness: 0.92,
    metalness: 0.025,
    envMapIntensity: 0.28
  })
  const roadMat = new THREE.MeshStandardMaterial({
    color: 0x3c4048,
    roughness: 0.9,
    metalness: 0.05,
    envMapIntensity: 0.16
  })
  const dashYellow = new THREE.MeshStandardMaterial({
    color: 0xeec928,
    roughness: 0.55,
    metalness: 0.1,
    envMapIntensity: 0.2
  })
  const coolingConcrete = new THREE.MeshStandardMaterial({
    map: concTex,
    color: 0xb9c2cc,
    roughness: 0.88,
    metalness: 0.04,
    envMapIntensity: 0.32
  })
  const coolingRim = new THREE.MeshStandardMaterial({ color: 0xe8ecf0, roughness: 0.5, metalness: 0.06 })
  const steamMat = new THREE.MeshStandardMaterial({
    color: 0xf8fafc,
    transparent: true,
    opacity: 0.38,
    roughness: 1,
    metalness: 0,
    depthWrite: false,
    envMapIntensity: 0.15
  })
  const tankBody = new THREE.MeshStandardMaterial({
    map: metalTex,
    color: 0xb4b8bf,
    roughness: 0.45,
    metalness: 0.45,
    envMapIntensity: 0.55
  })
  const tankBand = new THREE.MeshStandardMaterial({ color: 0x2a4a7a, roughness: 0.42, metalness: 0.3 })
  const chimneyConcrete = new THREE.MeshStandardMaterial({
    map: concTex,
    color: 0xaeb4bc,
    roughness: 0.9,
    metalness: 0.035,
    envMapIntensity: 0.26
  })
  const chimneyRimDark = new THREE.MeshStandardMaterial({ color: 0x22252c, roughness: 0.8, metalness: 0.08 })
  const stackBandRed = new THREE.MeshStandardMaterial({ color: 0xcf2a28, roughness: 0.48, metalness: 0.16 })
  const stackBandWhite = new THREE.MeshStandardMaterial({ color: 0xededed, roughness: 0.5, metalness: 0.06 })
  const chimneyPad = new THREE.MeshStandardMaterial({ color: 0x5c5650, roughness: 0.64, metalness: 0.22 })
  const chimneyGrate = new THREE.MeshStandardMaterial({ color: 0x353940, roughness: 0.85, metalness: 0.12 })
  const hallWall = new THREE.MeshStandardMaterial({
    map: concTex,
    color: 0xc5c9d0,
    roughness: 0.82,
    metalness: 0.04,
    envMapIntensity: 0.3
  })
  const hallRoof = new THREE.MeshStandardMaterial({ color: 0x4a5058, roughness: 0.72, metalness: 0.18 })
  const hallWin = new THREE.MeshStandardMaterial({
    color: 0x9eb8d8,
    emissive: 0x3a5070,
    emissiveIntensity: 0.35,
    roughness: 0.4,
    metalness: 0.05
  })
  const auxWall = new THREE.MeshStandardMaterial({ color: 0xd0d4db, roughness: 0.76, metalness: 0.05 })
  const conveyorMat = new THREE.MeshStandardMaterial({ color: 0x5c636d, roughness: 0.58, metalness: 0.38 })

  const allMark: THREE.MeshStandardMaterial[] = [
    floorMat,
    roadMat,
    dashYellow,
    coolingConcrete,
    coolingRim,
    steamMat,
    tankBody,
    tankBand,
    chimneyConcrete,
    chimneyRimDark,
    stackBandRed,
    stackBandWhite,
    chimneyPad,
    chimneyGrate,
    hallWall,
    hallRoof,
    hallWin,
    auxWall,
    conveyorMat
  ]
  allMark.forEach(markSkip)

  const floor = new THREE.Mesh(new THREE.PlaneGeometry(118, 82), floorMat)
  floor.rotation.x = -Math.PI / 2
  floor.receiveShadow = true
  world.add(floor)

  const ry = 0.032
  const hw = 2.4
  addRoadStrip(world, -52, 0, 40, 0, hw, ry, roadMat)
  addRoadStrip(world, -14, -28, -14, 32, hw * 0.9, ry, roadMat)
  addRoadStrip(world, -14, 0, 28, 0, hw * 0.85, ry, roadMat)
  addRoadDashes(world, -52, 0, 40, 0, ry + 0.018, 2.0, 1.2, dashYellow)
  addRoadDashes(world, -14, -28, -14, 32, ry + 0.018, 2.0, 1.2, dashYellow)

  addCoolingTower(world, -36, 8, coolingConcrete, coolingRim, steamMat)
  addCoolingTower(world, -36, -8, coolingConcrete, coolingRim, steamMat)

  addChimney(world, 2, -34, 38, chimneyConcrete, chimneyRimDark, stackBandRed, stackBandWhite, chimneyPad, chimneyGrate, 3.1, 1.35, 2.5, 0.33)

  addTurbineHall(world, 8, 6, 46, 13, 10.5, hallWall, hallRoof, hallWin)

  addHorizontalTank(world, 32, 2.2, 10, 9, 1.35, 0.15, tankBody, tankBand)
  addHorizontalTank(world, 32, 2.2, -4, 8, 1.2, -0.1, tankBody, tankBand)

  addCoalConveyor(world, -20, 14, 22, 8, 6.2, conveyorMat)

  const auxGrid: { x: number; z: number; w: number; d: number; h: number }[] = [
    { x: -8, z: 22, w: 5, d: 4.5, h: 4.2 },
    { x: -2, z: 22, w: 5, d: 4.5, h: 4.2 },
    { x: 4, z: 22, w: 5, d: 4.5, h: 4.2 },
    { x: -8, z: 28, w: 5, d: 4.5, h: 3.6 },
    { x: -2, z: 28, w: 5, d: 4.5, h: 5.0 },
    { x: 4, z: 28, w: 5, d: 4.5, h: 3.8 }
  ]
  for (const a of auxGrid) {
    const b = new THREE.Mesh(new THREE.BoxGeometry(a.w, a.h, a.d), auxWall)
    b.position.set(a.x, a.h * 0.5, a.z)
    b.castShadow = true
    b.receiveShadow = true
    world.add(b)
  }

  scene.add(new THREE.AmbientLight(0xa8b6c8, 0.48))
  const sun = new THREE.DirectionalLight(0xfff4ea, 0.88)
  sun.position.set(-40, 52, 28)
  sun.castShadow = true
  sun.shadow.mapSize.set(3072, 3072)
  sun.shadow.bias = -0.0002
  sun.shadow.camera.near = 2
  sun.shadow.camera.far = 150
  sun.shadow.camera.left = -65
  sun.shadow.camera.right = 65
  sun.shadow.camera.top = 65
  sun.shadow.camera.bottom = -65
  scene.add(sun)

  const hemi = new THREE.HemisphereLight(0xd0daf0, 0x8e96a0, 0.36)
  scene.add(hemi)

  const pmrem = new THREE.PMREMGenerator(renderer)
  pmrem.compileEquirectangularShader()
  const envRT = pmrem.fromScene(new RoomEnvironment(), 0.045)
  scene.environment = envRT.texture
  scene.background = new THREE.Color(0xc5ccd6)

  const homePosition = new THREE.Vector3(-12, THERMAL_PLANT_GROUND_Y, -14)

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
    allMark.forEach((m) => m.dispose())
  }

  return { scene, world, homePosition, dispose }
}
