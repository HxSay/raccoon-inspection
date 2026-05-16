import * as THREE from 'three'
import { getPatrolTowerCoordinates } from './towerInspectionCoords'

function makeLabelSprite(text: string, subText: string): THREE.Sprite {
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 160
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = 'rgba(8, 18, 28, 0.82)'
  ctx.strokeStyle = 'rgba(110, 207, 155, 0.9)'
  ctx.lineWidth = 3
  roundRect(ctx, 8, 8, 496, 144, 12)
  ctx.fill()
  ctx.stroke()
  ctx.fillStyle = '#6ecf9b'
  ctx.font = 'bold 40px ui-monospace, monospace'
  ctx.fillText(text, 24, 52)
  ctx.fillStyle = '#c8d6e5'
  ctx.font = '22px ui-monospace, monospace'
  const lines = subText.split('\n')
  lines.forEach((line, i) => ctx.fillText(line, 24, 88 + i * 28))

  const tex = new THREE.CanvasTexture(canvas)
  tex.colorSpace = THREE.SRGBColorSpace
  const mat = new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false, depthWrite: false })
  const sprite = new THREE.Sprite(mat)
  sprite.scale.set(42, 13, 1)
  sprite.renderOrder = 999
  return sprite
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

/** 在 3D 场景杆塔上方显示编号与 WGS84 坐标标签 */
export function createTowerCoordMarkers(world: THREE.Group): { group: THREE.Group; dispose: () => void } {
  const group = new THREE.Group()
  group.name = 'TowerCoordMarkers'

  const centers = getPatrolTowerCoordinates().filter((r) => r.role === 'tower_center')
  for (const row of centers) {
    const sprite = makeLabelSprite(
      row.label,
      `经 ${row.longitude.toFixed(6)}\n纬 ${row.latitude.toFixed(6)}\n高 ${row.height} m`
    )
    sprite.position.set(row.scene.x, row.scene.y + 72, row.scene.z)
    group.add(sprite)

    const pole = new THREE.Mesh(
      new THREE.CylinderGeometry(0.35, 0.35, 68, 8),
      new THREE.MeshBasicMaterial({ color: 0x6ecf9b, transparent: true, opacity: 0.35, depthWrite: false })
    )
    pole.position.set(row.scene.x, row.scene.y + 34, row.scene.z)
    group.add(pole)
  }

  world.add(group)

  return {
    group,
    dispose: () => {
      group.traverse((obj) => {
        if (obj instanceof THREE.Sprite) {
          const m = obj.material as THREE.SpriteMaterial
          m.map?.dispose()
          m.dispose()
        }
        if (obj instanceof THREE.Mesh) {
          obj.geometry.dispose()
          ;(obj.material as THREE.Material).dispose()
        }
      })
      world.remove(group)
    }
  }
}
