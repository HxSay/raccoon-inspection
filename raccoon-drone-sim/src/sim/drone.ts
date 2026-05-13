import * as THREE from 'three'

/**
 * M300 无人机可视化：由基础几何体拼装，含四旋翼、机身、云台支架。
 * 真机 GLB 可后续替换 mesh，对外保持 setPose / setGimbal / setRotorRunning 接口即可。
 */

export class M300DroneModel {
  readonly root: THREE.Group
  private readonly rotors: THREE.Mesh[] = []
  private readonly gimbalPitch: THREE.Group
  private rotorSpeed = 0

  constructor() {
    this.root = new THREE.Group()

    const bodyMat = new THREE.MeshStandardMaterial({ color: 0x2a2f36, metalness: 0.5, roughness: 0.35 })
    const accentMat = new THREE.MeshStandardMaterial({ color: 0xf2c14e, metalness: 0.2, roughness: 0.5 })

    const body = new THREE.Mesh(new THREE.BoxGeometry(5, 1.4, 2.2), bodyMat)
    body.castShadow = true
    this.root.add(body)

    const armLen = 6
    const dirs = [
      new THREE.Vector3(1, 0, 1).normalize(),
      new THREE.Vector3(-1, 0, 1).normalize(),
      new THREE.Vector3(1, 0, -1).normalize(),
      new THREE.Vector3(-1, 0, -1).normalize()
    ]
    for (const d of dirs) {
      const arm = new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.45, armLen, 8), bodyMat)
      arm.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), d.clone().setY(0.05).normalize())
      arm.position.copy(d.clone().multiplyScalar(armLen * 0.45))
      arm.position.y = 0.2
      arm.castShadow = true
      this.root.add(arm)

      const motor = new THREE.Mesh(new THREE.CylinderGeometry(0.9, 0.9, 0.35, 16), accentMat)
      motor.position.copy(d.clone().multiplyScalar(armLen * 0.72))
      motor.position.y = 0.45
      this.root.add(motor)

      const rotor = new THREE.Mesh(
        new THREE.BoxGeometry(5.5, 0.06, 0.45),
        new THREE.MeshStandardMaterial({ color: 0x333333, transparent: true, opacity: 0.85 })
      )
      rotor.position.copy(motor.position)
      rotor.position.y += 0.2
      this.root.add(rotor)
      this.rotors.push(rotor)
    }

    const gimbalBase = new THREE.Mesh(new THREE.BoxGeometry(0.8, 0.5, 0.8), accentMat)
    gimbalBase.position.set(0, -0.95, 1.0)
    this.root.add(gimbalBase)

    this.gimbalPitch = new THREE.Group()
    this.gimbalPitch.position.copy(gimbalBase.position)
    this.gimbalPitch.position.y -= 0.35
    this.root.add(this.gimbalPitch)

    const cam = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.45, 0.75), bodyMat)
    cam.position.set(0, -0.2, 0.35)
    cam.castShadow = true
    this.gimbalPitch.add(cam)
  }

  /** 设置世界位姿（位置 + 机头朝向） */
  setPose(position: THREE.Vector3, yawRad: number, pitchRad = 0, rollRad = 0): void {
    this.root.position.copy(position)
    this.root.rotation.set(pitchRad, yawRad, rollRad, 'YXZ')
  }

  /** 云台俯仰（绕 X）与偏航（绕 Y，相对机身） */
  setGimbal(pitchDeg: number, yawDeg: number): void {
    this.gimbalPitch.rotation.x = THREE.MathUtils.degToRad(pitchDeg)
    this.gimbalPitch.rotation.y = THREE.MathUtils.degToRad(yawDeg)
  }

  /** 旋翼是否高速旋转（飞行中） */
  setRotorRunning(on: boolean): void {
    this.rotorSpeed = on ? 1 : 0
  }

  /** 每帧调用：旋翼动画 */
  tick(dt: number): void {
    if (this.rotorSpeed <= 0) return
    const w = 40 * this.rotorSpeed
    for (const r of this.rotors) {
      r.rotation.y += w * dt
    }
  }
}
