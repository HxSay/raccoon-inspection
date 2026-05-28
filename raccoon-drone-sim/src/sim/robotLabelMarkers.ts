import * as THREE from 'three'
import type { InspectionRobotVO } from '@/api/inspectionRobot'

export interface RobotLabelHandle {
  group: THREE.Group
  dispose: () => void
}

function hexToRgb(hex: string): { r: number; g: number; b: number } {
  const h = (hex || '#409EFF').replace('#', '')
  const n = parseInt(h.length === 3 ? h.split('').map((c) => c + c).join('') : h, 16)
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
}

function makeRobotLabelSprite(robot: InspectionRobotVO): THREE.Sprite {
  const color = robot.markerColor ?? '#409EFF'
  const { r, g, b } = hexToRgb(color)
  const canvas = document.createElement('canvas')
  canvas.width = 480
  canvas.height = 140
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = 'rgba(8, 18, 28, 0.88)'
  ctx.strokeStyle = `rgba(${r},${g},${b},0.95)`
  ctx.lineWidth = 4
  roundRect(ctx, 6, 6, 468, 128, 10)
  ctx.fill()
  ctx.stroke()
  ctx.fillStyle = `rgb(${r},${g},${b})`
  ctx.font = 'bold 44px ui-monospace, monospace'
  ctx.fillText(robot.markerLabel || '?', 20, 52)
  ctx.fillStyle = '#c8d6e5'
  ctx.font = '22px ui-monospace, monospace'
  const sub = robot.uavName?.length > 18 ? robot.uavName.slice(0, 16) + '…' : robot.uavName
  ctx.fillText(sub ?? '', 20, 88)
  ctx.font = '18px ui-monospace, monospace'
  ctx.fillStyle = '#8ab4f8'
  ctx.fillText(robot.robotTypeLabel ?? robot.robotType ?? '', 20, 118)

  const tex = new THREE.CanvasTexture(canvas)
  tex.colorSpace = THREE.SRGBColorSpace
  const mat = new THREE.SpriteMaterial({
    map: tex,
    transparent: true,
    depthTest: false,
    depthWrite: false
  })
  const sprite = new THREE.Sprite(mat)
  sprite.scale.set(38, 11, 1)
  sprite.renderOrder = 1000
  sprite.position.y = 16
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

/** 在机器人模型上方挂载彩色识别标记 */
export function attachRobotLabel(root: THREE.Object3D, robot: InspectionRobotVO): RobotLabelHandle {
  const group = new THREE.Group()
  group.add(makeRobotLabelSprite(robot))
  root.add(group)
  return {
    group,
    dispose: () => {
      group.traverse((o) => {
        if (o instanceof THREE.Sprite) {
          o.material.map?.dispose()
          o.material.dispose()
        }
      })
      root.remove(group)
    }
  }
}
