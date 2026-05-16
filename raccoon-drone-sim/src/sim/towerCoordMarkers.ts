import * as THREE from 'three'
import {
  getPatrolFacilityCoordinates,
  getPatrolTowerCoordinates,
  type TowerCoordRow
} from './towerInspectionCoords'
import { PATROL_TOWER_HEIGHTS } from './scenePatrolLayout'

const PATROL_TOWER_LABEL_LIFT = Math.max(...PATROL_TOWER_HEIGHTS) + 14

type LabelStyle = { stroke: string; title: string; body: string; pole: number }

const STYLES = {
  tower: { stroke: 'rgba(110, 207, 155, 0.9)', title: '#6ecf9b', body: '#c8d6e5', pole: 0x6ecf9b },
  nest: { stroke: 'rgba(255, 170, 51, 0.95)', title: '#ffcc66', body: '#f5e6d0', pole: 0xffaa33 },
  ground: { stroke: 'rgba(255, 107, 107, 0.95)', title: '#ff8888', body: '#f5d6d6', pole: 0xff6b6b }
} as const

function styleForRow(row: TowerCoordRow): LabelStyle {
  if (row.role === 'drone_nest') return STYLES.nest
  if (row.role === 'ground_station') return STYLES.ground
  return STYLES.tower
}

function labelHeight(row: TowerCoordRow): number {
  if (row.role === 'drone_nest') return row.scene.y + 14
  if (row.role === 'ground_station') return row.scene.y + 9
  return row.scene.y + PATROL_TOWER_LABEL_LIFT
}

function poleHeight(row: TowerCoordRow): number {
  if (row.role === 'drone_nest') return 12
  if (row.role === 'ground_station') return 8
  if (row.role === 'tower_center' && row.structuralHeight != null) return row.structuralHeight
  return Math.max(...PATROL_TOWER_HEIGHTS)
}

function makeLabelSprite(text: string, subText: string, style: LabelStyle): THREE.Sprite {
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 160
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = 'rgba(8, 18, 28, 0.82)'
  ctx.strokeStyle = style.stroke
  ctx.lineWidth = 3
  roundRect(ctx, 8, 8, 496, 144, 12)
  ctx.fill()
  ctx.stroke()
  ctx.fillStyle = style.title
  ctx.font = 'bold 40px ui-monospace, monospace'
  ctx.fillText(text, 24, 52)
  ctx.fillStyle = style.body
  ctx.font = '22px ui-monospace, monospace'
  subText.split('\n').forEach((line, i) => ctx.fillText(line, 24, 88 + i * 28))

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

function heightLine(row: TowerCoordRow): string {
  if (row.role === 'tower_center' && row.structuralHeight != null) {
    return `塔全高 ${row.structuralHeight} m`
  }
  return `高 ${row.height} m`
}

function addCoordMarker(group: THREE.Group, row: TowerCoordRow) {
  const style = styleForRow(row)
  const sub = `经 ${row.longitude.toFixed(6)}\n纬 ${row.latitude.toFixed(6)}\n${heightLine(row)}`
  const sprite = makeLabelSprite(row.label, sub, style)
  sprite.position.set(row.scene.x, labelHeight(row), row.scene.z)
  group.add(sprite)

  const h = poleHeight(row)
  const pole = new THREE.Mesh(
    new THREE.CylinderGeometry(0.35, 0.35, h, 8),
    new THREE.MeshBasicMaterial({ color: style.pole, transparent: true, opacity: 0.35, depthWrite: false })
  )
  pole.position.set(row.scene.x, row.scene.y + h * 0.5, row.scene.z)
  group.add(pole)
}

/** 杆塔、机巢、地面站 WGS84 坐标标签 */
export function createTowerCoordMarkers(world: THREE.Group): { group: THREE.Group; dispose: () => void } {
  const group = new THREE.Group()
  group.name = 'PatrolCoordMarkers'

  const rows = [
    ...getPatrolFacilityCoordinates(),
    ...getPatrolTowerCoordinates().filter((r) => r.role === 'tower_center')
  ]
  for (const row of rows) addCoordMarker(group, row)

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
