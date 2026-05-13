import * as THREE from 'three'

/**
 * 电力巡检场景：山地、输电塔、导线 —— 全部使用基础几何体，无外部模型资源。
 */

export interface PowerlineSceneBundle {
  scene: THREE.Scene
  /** 场景根节点，便于整体位移/调试 */
  world: THREE.Group
  /** 建议的起飞/返航点（世界坐标） */
  homePosition: THREE.Vector3
  dispose: () => void
}

/**
 * 创建山地：多层挤压形状 + 绿色地面
 */
function createTerrain(world: THREE.Group): void {
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(800, 800, 1, 1),
    new THREE.MeshStandardMaterial({ color: 0x3d6b3d, roughness: 0.9, metalness: 0 })
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = 0
  ground.receiveShadow = true
  world.add(ground)

  const hillMat = new THREE.MeshStandardMaterial({ color: 0x5c4033, roughness: 1, flatShading: true })
  const positions = [
    { x: -120, z: -80, s: 1.2 },
    { x: 90, z: -40, s: 0.9 },
    { x: -60, z: 100, s: 1.0 },
    { x: 140, z: 80, s: 0.85 }
  ]
  for (const p of positions) {
    const hill = new THREE.Mesh(new THREE.ConeGeometry(45 * p.s, 35 * p.s, 7, 1), hillMat)
    hill.position.set(p.x, 18 * p.s, p.z)
    hill.castShadow = true
    hill.receiveShadow = true
    world.add(hill)
  }
}

/**
 * 输电塔：塔身圆柱 + 横担方梁
 */
function createTower(x: number, z: number, height: number, world: THREE.Group): THREE.Vector3 {
  const tower = new THREE.Group()
  tower.position.set(x, 0, z)
  const leg = new THREE.Mesh(
    new THREE.CylinderGeometry(2.2, 3.5, height, 8),
    new THREE.MeshStandardMaterial({ color: 0x6b6f7a, metalness: 0.35, roughness: 0.45 })
  )
  leg.position.y = height / 2
  leg.castShadow = true
  tower.add(leg)
  const armMat = new THREE.MeshStandardMaterial({ color: 0x8a9099, metalness: 0.4, roughness: 0.35 })
  for (let i = 0; i < 3; i++) {
    const y = height * (0.35 + i * 0.22)
    const arm = new THREE.Mesh(new THREE.BoxGeometry(22, 0.8, 0.8), armMat)
    arm.position.set(0, y, 0)
    arm.castShadow = true
    tower.add(arm)
  }
  world.add(tower)
  return new THREE.Vector3(x, height, z)
}

/**
 * 在塔顶之间拉简易导线（细圆柱）
 */
function createWires(tops: THREE.Vector3[], world: THREE.Group): void {
  const mat = new THREE.MeshStandardMaterial({ color: 0x222222, metalness: 0.6, roughness: 0.3 })
  for (let i = 0; i < tops.length - 1; i++) {
    const a = tops[i]
    const b = tops[i + 1]
    const mid = new THREE.Vector3().addVectors(a, b).multiplyScalar(0.5)
    mid.y -= 4 // 悬垂
    const curve = new THREE.QuadraticBezierCurve3(a.clone(), mid, b.clone())
    const tube = new THREE.Mesh(new THREE.TubeGeometry(curve, 24, 0.12, 6, false), mat)
    tube.castShadow = true
    world.add(tube)
  }
}

/**
 * 初始化场景、光照、雾效
 */
export function createPowerlineScene(): PowerlineSceneBundle {
  const scene = new THREE.Scene()
  scene.background = new THREE.Color(0x87b8e8)
  scene.fog = new THREE.Fog(0x87b8e8, 120, 520)

  const world = new THREE.Group()
  scene.add(world)

  createTerrain(world)

  const heights = [55, 62, 58, 60, 56]
  const xs = [-80, 0, 80, 160, 240]
  const tops: THREE.Vector3[] = []
  for (let i = 0; i < xs.length; i++) {
    tops.push(createTower(xs[i], -20, heights[i], world))
  }
  createWires(tops, world)

  const amb = new THREE.AmbientLight(0xffffff, 0.45)
  scene.add(amb)
  const sun = new THREE.DirectionalLight(0xffffff, 0.85)
  sun.position.set(120, 200, 80)
  sun.castShadow = true
  sun.shadow.mapSize.set(2048, 2048)
  sun.shadow.camera.near = 10
  sun.shadow.camera.far = 600
  sun.shadow.camera.left = -300
  sun.shadow.camera.right = 300
  sun.shadow.camera.top = 300
  sun.shadow.camera.bottom = -300
  scene.add(sun)

  const homePosition = new THREE.Vector3(0, 3, 40)

  const dispose = () => {
    scene.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry.dispose()
        const m = obj.material
        if (Array.isArray(m)) m.forEach((x) => x.dispose())
        else m.dispose()
      }
    })
  }

  return { scene, world, homePosition, dispose }
}
