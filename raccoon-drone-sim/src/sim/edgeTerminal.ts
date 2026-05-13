import * as THREE from 'three'
import { createGalvanizedMetalTexture, createCarbonTexture, disposeTexture } from './textures'

/**
 * 边缘侧控制终端：机柜式工业设备 + 地面部署标识环。
 * 位置与电网运维场景中「就地控制机」一致，仅作可视化。
 */
export class EdgeTerminal3D {
  readonly root: THREE.Group
  private metal: THREE.Texture | null = null
  private carbon: THREE.Texture | null = null

  constructor() {
    this.root = new THREE.Group()
    this.metal = createGalvanizedMetalTexture()
    this.carbon = createCarbonTexture()

    const ring = new THREE.Mesh(
      new THREE.RingGeometry(2.2, 2.8, 48),
      new THREE.MeshStandardMaterial({
        color: 0xffaa33,
        emissive: 0x331100,
        emissiveIntensity: 0.6,
        metalness: 0.2,
        roughness: 0.5,
        side: THREE.DoubleSide
      })
    )
    ring.rotation.x = -Math.PI / 2
    ring.position.y = 0.04
    ring.receiveShadow = true
    this.root.add(ring)

    const cabinet = new THREE.Mesh(
      new THREE.BoxGeometry(2.2, 3.2, 1.1),
      new THREE.MeshStandardMaterial({
        map: this.carbon,
        metalness: 0.35,
        roughness: 0.55,
        envMapIntensity: 0.7
      })
    )
    cabinet.position.set(0, 1.65, 0)
    cabinet.castShadow = true
    cabinet.receiveShadow = true
    this.root.add(cabinet)

    const bezel = new THREE.Mesh(
      new THREE.BoxGeometry(1.65, 1.05, 0.08),
      new THREE.MeshStandardMaterial({ color: 0x0a1628, metalness: 0.6, roughness: 0.25, emissive: 0x001830, emissiveIntensity: 0.4 })
    )
    bezel.position.set(0, 2.05, 0.58)
    this.root.add(bezel)

    const rack = new THREE.Mesh(
      new THREE.BoxGeometry(2.35, 0.2, 1.15),
      new THREE.MeshStandardMaterial({ map: this.metal, metalness: 0.5, roughness: 0.4 })
    )
    rack.position.set(0, 3.35, 0)
    rack.castShadow = true
    this.root.add(rack)
  }

  dispose(): void {
    disposeTexture(this.metal)
    disposeTexture(this.carbon)
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
