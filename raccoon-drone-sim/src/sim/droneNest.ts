import * as THREE from 'three'
import { createConcreteTexture, createGalvanizedMetalTexture, disposeTexture } from './textures'

/**
 * 无人机机巢（写实风格）：混凝土基座 + 金属舱体 + 对开舱门。
 * 仅负责外观与舱门动画，不参与边缘业务状态机。
 */
export class DroneNest {
  readonly root: THREE.Group
  private readonly doorL: THREE.Group
  private readonly doorR: THREE.Group
  private doorOpen = 0
  private doorTarget = 0
  private metalTex: THREE.Texture | null = null
  private concTex: THREE.Texture | null = null

  constructor() {
    this.root = new THREE.Group()

    this.metalTex = createGalvanizedMetalTexture()
    this.concTex = createConcreteTexture()

    const pad = new THREE.Mesh(
      new THREE.BoxGeometry(14, 0.6, 12),
      new THREE.MeshStandardMaterial({
        map: this.concTex,
        roughness: 0.92,
        metalness: 0.05
      })
    )
    pad.position.y = 0.3
    pad.receiveShadow = true
    pad.castShadow = true
    this.root.add(pad)

    const shellMat = new THREE.MeshStandardMaterial({
      map: this.metalTex,
      metalness: 0.55,
      roughness: 0.42,
      envMapIntensity: 0.85
    })

    const back = new THREE.Mesh(new THREE.BoxGeometry(12, 4.5, 1.2), shellMat)
    back.position.set(0, 2.8, -5.2)
    back.castShadow = true
    this.root.add(back)

    const roof = new THREE.Mesh(new THREE.BoxGeometry(12.4, 0.35, 10.8), shellMat)
    roof.position.set(0, 5.05, 0)
    roof.castShadow = true
    this.root.add(roof)

    const sideL = new THREE.Mesh(new THREE.BoxGeometry(0.35, 4.5, 10.6), shellMat)
    sideL.position.set(-5.95, 2.8, 0)
    sideL.castShadow = true
    this.root.add(sideL)

    const sideR = new THREE.Mesh(new THREE.BoxGeometry(0.35, 4.5, 10.6), shellMat)
    sideR.position.set(5.95, 2.8, 0)
    sideR.castShadow = true
    this.root.add(sideR)

    this.doorL = new THREE.Group()
    this.doorL.position.set(-3, 2.2, 5.35)
    this.doorR = new THREE.Group()
    this.doorR.position.set(3, 2.2, 5.35)

    const doorGeo = new THREE.BoxGeometry(3.1, 3.8, 0.28)
    const dl = new THREE.Mesh(doorGeo, shellMat)
    dl.position.x = 1.55
    dl.castShadow = true
    this.doorL.add(dl)
    const dr = new THREE.Mesh(doorGeo, shellMat)
    dr.position.x = -1.55
    dr.castShadow = true
    this.doorR.add(dr)

    this.root.add(this.doorL, this.doorR)

    const rail = new THREE.Mesh(new THREE.BoxGeometry(8, 0.12, 0.4), shellMat)
    rail.position.set(0, 0.95, 4.2)
    this.root.add(rail)
  }

  /** 机巢内停机坪参考点（无人机待命高度） */
  getDockPosition(): THREE.Vector3 {
    const p = new THREE.Vector3(0, 1.35, 2.2)
    this.root.localToWorld(p)
    return p
  }

  /** 目标开度 0=关闭 1=全开 */
  setDoorTarget(open: number): void {
    this.doorTarget = THREE.MathUtils.clamp(open, 0, 1)
  }

  tick(dt: number): void {
    const speed = 1.8
    this.doorOpen = THREE.MathUtils.damp(this.doorOpen, this.doorTarget, speed, dt)
    const a = this.doorOpen * 1.15
    this.doorL.rotation.y = a
    this.doorR.rotation.y = -a
  }

  dispose(): void {
    disposeTexture(this.metalTex)
    disposeTexture(this.concTex)
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
