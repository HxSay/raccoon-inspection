import * as THREE from 'three'

/**
 * 四足巡检机器狗（程序化低模）：与 M300DroneModel 相同 MissionInspectable 接口，供 MissionRunner 复用航线/派发逻辑。
 */
export class RobotDogModel {
  readonly root: THREE.Group
  private readonly headGroup: THREE.Group
  /** 巡检视线：局部 +Z 为拍摄方向，挂于头部 */
  private readonly inspectionViewRig = new THREE.Object3D()
  private readonly legs: { upper: THREE.Mesh; lower: THREE.Mesh; side: number; phase: number }[] = []
  private walkActive = false
  private walkPhase = 0
  /** 模型包围盒最低点相对 root 的 Y（用于贴地：世界地面 y 时 root.y = y - soleMinY） */
  private soleMinY = 0.01

  constructor() {
    this.root = new THREE.Group()
    this.headGroup = new THREE.Group()

    const bodyMat = new THREE.MeshStandardMaterial({
      color: 0xe8ecf0,
      roughness: 0.42,
      metalness: 0.18,
      envMapIntensity: 0.55
    })
    const darkMat = new THREE.MeshStandardMaterial({
      color: 0x3a4048,
      roughness: 0.48,
      metalness: 0.35,
      envMapIntensity: 0.45
    })
    const accentBlue = new THREE.MeshStandardMaterial({
      color: 0x1e6fd0,
      emissive: 0x0a3a78,
      emissiveIntensity: 0.35,
      roughness: 0.28,
      metalness: 0.2
    })

    const body = new THREE.Mesh(new THREE.BoxGeometry(0.95, 0.38, 0.52), bodyMat)
    body.position.y = 0.42
    body.castShadow = true
    this.root.add(body)

    this.headGroup.position.set(0.52, 0.48, 0)
    this.root.add(this.headGroup)
    const head = new THREE.Mesh(new THREE.BoxGeometry(0.36, 0.28, 0.32), bodyMat)
    head.castShadow = true
    this.headGroup.add(head)
    const eyeL = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.04, 12), accentBlue)
    eyeL.rotation.z = Math.PI / 2
    eyeL.position.set(0.14, 0.04, 0.12)
    this.headGroup.add(eyeL)
    const eyeR = eyeL.clone()
    eyeR.position.z = -0.12
    this.headGroup.add(eyeR)

    this.inspectionViewRig.position.set(0.2, 0.06, 0)
    this.inspectionViewRig.rotation.set(0, -Math.PI / 2, 0)
    this.headGroup.add(this.inspectionViewRig)

    const hipY = 0.38
    const corners: [number, number][] = [
      [0.38, 0.22],
      [0.38, -0.22],
      [-0.42, 0.22],
      [-0.42, -0.22]
    ]
    let idx = 0
    for (const [hx, hz] of corners) {
      const upper = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.26, 0.14), darkMat)
      upper.position.set(hx, hipY - 0.1, hz)
      upper.castShadow = true
      this.root.add(upper)
      const lower = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.22, 0.12), darkMat)
      lower.position.set(hx, 0.12, hz)
      lower.castShadow = true
      this.root.add(lower)
      this.legs.push({ upper, lower, side: hx > 0 ? 1 : -1, phase: idx * 0.5 * Math.PI })
      idx++
    }

    this.root.updateMatrixWorld(true)
    const box = new THREE.Box3().setFromObject(this.root)
    this.soleMinY = box.min.y
  }

  getInspectionViewRig(): THREE.Object3D {
    return this.inspectionViewRig
  }

  setPose(position: THREE.Vector3, yawRad: number, pitchRad = 0, rollRad = 0): void {
    this.root.position.set(position.x, position.y - this.soleMinY, position.z)
    this.root.rotation.set(pitchRad, yawRad, rollRad, 'YXZ')
  }

  setGimbal(pitchDeg: number, yawDeg: number): void {
    this.headGroup.rotation.x = THREE.MathUtils.degToRad(pitchDeg)
    this.headGroup.rotation.y = THREE.MathUtils.degToRad(yawDeg)
  }

  setRotorRunning(on: boolean): void {
    this.walkActive = on
  }

  tick(dt: number): void {
    if (!this.walkActive) return
    this.walkPhase += dt * 7.2
    const s = Math.sin(this.walkPhase)
    for (const L of this.legs) {
      const swing = s * L.side * 0.22
      L.upper.rotation.x = swing
      L.lower.rotation.x = -swing * 0.85
      L.lower.position.y = 0.12 + Math.abs(swing) * 0.04
    }
  }

  dispose(): void {
    this.root.traverse((o) => {
      if (o instanceof THREE.Mesh) {
        o.geometry.dispose()
        const m = o.material
        if (!Array.isArray(m)) m.dispose()
        else m.forEach((x) => x.dispose())
      }
    })
  }
}
