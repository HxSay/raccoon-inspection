import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { createCarbonTexture, createGalvanizedMetalTexture, disposeTexture } from './textures'

/**
 * 大疆 M300 外观：优先加载 `/models/m300.glb`（用户自备）；失败则使用带 PBR 贴图的拼装机体。
 * 对外保持 setPose / setGimbal / setRotorRunning / tick 不变，任务逻辑无需感知模型来源。
 */

export class M300DroneModel {
  readonly root: THREE.Group
  private readonly rotors: THREE.Mesh[] = []
  private readonly gimbalPitch: THREE.Group
  private rotorSpeed = 0
  private bodyTex: THREE.Texture | null = null
  private metalTex: THREE.Texture | null = null
  private gltfMode = false

  constructor() {
    this.root = new THREE.Group()
    this.gimbalPitch = new THREE.Group()
    this.bodyTex = createCarbonTexture()
    this.metalTex = createGalvanizedMetalTexture()
    this.buildProceduralBody()
  }

  /**
   * 尝试加载外部 GLB（写实模型）。成功则替换子节点；失败静默保留拼装模型。
   */
  async tryLoadExternalModel(url = '/models/m300.glb'): Promise<boolean> {
    return new Promise((resolve) => {
      const loader = new GLTFLoader()
      loader.load(
        url,
        (gltf) => {
          this.disposeRootContents()
          this.rotors.length = 0
          const s = gltf.scene
          const box = new THREE.Box3().setFromObject(s)
          const size = new THREE.Vector3()
          box.getSize(size)
          const scale = 12 / Math.max(size.x, size.y, size.z, 0.001)
          s.scale.setScalar(scale)
          box.setFromObject(s)
          const c = new THREE.Vector3()
          box.getCenter(c)
          s.position.sub(c)
          s.traverse((o) => {
            if (o instanceof THREE.Mesh) {
              o.castShadow = true
              o.receiveShadow = true
            }
          })
          this.root.add(s)
          this.gimbalPitch = new THREE.Group()
          this.root.add(this.gimbalPitch)
          this.gltfMode = true
          disposeTexture(this.bodyTex)
          disposeTexture(this.metalTex)
          this.bodyTex = null
          this.metalTex = null
          resolve(true)
        },
        undefined,
        () => resolve(false)
      )
    })
  }

  private disposeRootContents(): void {
    while (this.root.children.length > 0) {
      const ch = this.root.children[0]
      this.root.remove(ch)
      ch.traverse((o) => {
        if (o instanceof THREE.Mesh) {
          o.geometry.dispose()
          const m = o.material
          if (!Array.isArray(m)) m.dispose()
          else m.forEach((x) => x.dispose())
        }
      })
    }
  }

  private buildProceduralBody(): void {
    const bodyMat = new THREE.MeshStandardMaterial({
      map: this.bodyTex!,
      metalness: 0.45,
      roughness: 0.38,
      envMapIntensity: 0.9
    })
    const accentMat = new THREE.MeshStandardMaterial({
      map: this.metalTex!,
      color: 0xf0c040,
      metalness: 0.55,
      roughness: 0.35
    })

    const fuselage = new THREE.Mesh(new THREE.BoxGeometry(5.8, 1.55, 2.45), bodyMat)
    fuselage.castShadow = true
    this.root.add(fuselage)

    const nose = new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.9, 2.2, 12, 1, false, 0, Math.PI), bodyMat)
    nose.rotation.z = Math.PI / 2
    nose.position.set(3.4, 0, 0)
    nose.castShadow = true
    this.root.add(nose)

    const armLen = 6.5
    const dirs = [
      new THREE.Vector3(1, 0, 1).normalize(),
      new THREE.Vector3(-1, 0, 1).normalize(),
      new THREE.Vector3(1, 0, -1).normalize(),
      new THREE.Vector3(-1, 0, -1).normalize()
    ]
    for (const d of dirs) {
      const arm = new THREE.Mesh(new THREE.CylinderGeometry(0.38, 0.48, armLen, 10), bodyMat)
      arm.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), d.clone().setY(0.06).normalize())
      arm.position.copy(d.clone().multiplyScalar(armLen * 0.42))
      arm.position.y = 0.25
      arm.castShadow = true
      this.root.add(arm)

      const motor = new THREE.Mesh(new THREE.CylinderGeometry(0.95, 0.95, 0.42, 20), accentMat)
      motor.position.copy(d.clone().multiplyScalar(armLen * 0.74))
      motor.position.y = 0.48
      this.root.add(motor)

      const rotor = new THREE.Mesh(
        new THREE.BoxGeometry(5.8, 0.07, 0.48),
        new THREE.MeshStandardMaterial({ color: 0x1a1a1a, metalness: 0.35, roughness: 0.55, transparent: true, opacity: 0.9 })
      )
      rotor.position.copy(motor.position)
      rotor.position.y += 0.22
      this.root.add(rotor)
      this.rotors.push(rotor)
    }

    const skidL = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.12, 4.2), accentMat)
    skidL.position.set(-0.9, -0.95, 0)
    skidL.castShadow = true
    this.root.add(skidL)
    const skidR = skidL.clone()
    skidR.position.x = 0.9
    this.root.add(skidR)

    const gimbalBase = new THREE.Mesh(new THREE.BoxGeometry(0.85, 0.55, 0.85), accentMat)
    gimbalBase.position.set(0, -0.95, 1.05)
    this.root.add(gimbalBase)

    this.gimbalPitch.position.copy(gimbalBase.position)
    this.gimbalPitch.position.y -= 0.38
    this.root.add(this.gimbalPitch)

    const cam = new THREE.Mesh(new THREE.BoxGeometry(0.58, 0.48, 0.78), bodyMat)
    cam.position.set(0, -0.22, 0.38)
    cam.castShadow = true
    this.gimbalPitch.add(cam)
  }

  setPose(position: THREE.Vector3, yawRad: number, pitchRad = 0, rollRad = 0): void {
    this.root.position.copy(position)
    this.root.rotation.set(pitchRad, yawRad, rollRad, 'YXZ')
  }

  setGimbal(pitchDeg: number, yawDeg: number): void {
    this.gimbalPitch.rotation.x = THREE.MathUtils.degToRad(pitchDeg)
    this.gimbalPitch.rotation.y = THREE.MathUtils.degToRad(yawDeg)
  }

  setRotorRunning(on: boolean): void {
    this.rotorSpeed = on ? 1 : 0
  }

  tick(dt: number): void {
    if (this.gltfMode || this.rotorSpeed <= 0) return
    const w = 48 * this.rotorSpeed
    for (const r of this.rotors) {
      r.rotation.y += w * dt
    }
  }

  dispose(): void {
    disposeTexture(this.bodyTex)
    disposeTexture(this.metalTex)
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
