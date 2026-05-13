import * as THREE from 'three'
import type { CloudPathPoint } from './types'

/**
 * 航点标记、拍照点高亮、已完成/未完成轨迹颜色区分。
 */

const COLOR_DONE = 0x22c55e
const COLOR_TODO = 0xf97316
const COLOR_PREVIEW = 0x94a3b8

export class FlightPathVisualization {
  readonly group: THREE.Group
  private readonly markers: THREE.Object3D[] = []
  private lineDone: THREE.Line
  private lineTodo: THREE.Line
  private previewLine: THREE.Line
  private doneGeom: THREE.BufferGeometry
  private todoGeom: THREE.BufferGeometry
  private maxPoints: number

  constructor(points: CloudPathPoint[], maxSegments = 4096) {
    this.group = new THREE.Group()
    this.maxPoints = maxSegments

    for (const p of points) {
      const g = new THREE.SphereGeometry(p.isPhoto ? 2.2 : 1.4, 12, 12)
      const mat = new THREE.MeshStandardMaterial({
        color: p.isPhoto ? 0xfacc15 : 0x38bdf8,
        emissive: p.isPhoto ? 0x854d0e : 0x0c4a6e,
        emissiveIntensity: 0.25
      })
      const m = new THREE.Mesh(g, mat)
      m.position.set(p.x, p.y, p.z)
      m.castShadow = true
      this.group.add(m)
      this.markers.push(m)
      if (p.isPhoto) {
        const ring = new THREE.Mesh(
          new THREE.TorusGeometry(3.2, 0.15, 8, 32),
          new THREE.MeshBasicMaterial({ color: 0xfde047 })
        )
        ring.position.copy(m.position)
        ring.rotation.x = Math.PI / 2
        this.group.add(ring)
        this.markers.push(ring)
      }
    }

    const positions = points.map((p) => new THREE.Vector3(p.x, p.y, p.z))
    const curve = new THREE.CatmullRomCurve3(positions, false, 'catmullrom', 0.35)
    const pts = curve.getPoints(Math.min(400, maxSegments))
    const arr = new Float32Array(pts.length * 3)
    for (let i = 0; i < pts.length; i++) {
      arr[i * 3] = pts[i].x
      arr[i * 3 + 1] = pts[i].y
      arr[i * 3 + 2] = pts[i].z
    }
    const previewGeom = new THREE.BufferGeometry()
    previewGeom.setAttribute('position', new THREE.BufferAttribute(arr, 3))
    this.previewLine = new THREE.Line(
      previewGeom,
      new THREE.LineBasicMaterial({ color: COLOR_PREVIEW, transparent: true, opacity: 0.45 })
    )
    this.group.add(this.previewLine)

    this.doneGeom = new THREE.BufferGeometry()
    const donePos = new Float32Array(maxSegments * 3)
    this.doneGeom.setAttribute('position', new THREE.BufferAttribute(donePos, 3))
    this.doneGeom.setDrawRange(0, 0)
    this.lineDone = new THREE.Line(this.doneGeom, new THREE.LineBasicMaterial({ color: COLOR_DONE, linewidth: 1 }))
    this.group.add(this.lineDone)

    this.todoGeom = new THREE.BufferGeometry()
    const todoPos = new Float32Array(maxSegments * 3)
    this.todoGeom.setAttribute('position', new THREE.BufferAttribute(todoPos, 3))
    this.todoGeom.setDrawRange(0, 0)
    this.lineTodo = new THREE.Line(this.todoGeom, new THREE.LineBasicMaterial({ color: COLOR_TODO, linewidth: 1 }))
    this.group.add(this.lineTodo)
  }

  /**
   * 更新已飞过的折线（从曲线采样点序列）与剩余部分。
   * @param flown 已飞过的世界坐标点（含当前位置）
   * @param curveFull 完整 CatmullRom 曲线
   * @param uCurrent 当前曲线参数 0..1
   */
  updateProgress(flown: THREE.Vector3[], curveFull: THREE.CatmullRomCurve3, uCurrent: number): void {
    const doneAttr = this.doneGeom.getAttribute('position') as THREE.BufferAttribute
    const nDone = Math.min(flown.length, this.maxPoints)
    for (let i = 0; i < nDone; i++) {
      doneAttr.setXYZ(i, flown[i].x, flown[i].y, flown[i].z)
    }
    this.doneGeom.setDrawRange(0, nDone)

    const restPts = 200
    const todoAttr = this.todoGeom.getAttribute('position') as THREE.BufferAttribute
    let k = 0
    for (let i = 0; i <= restPts && k < this.maxPoints; i++) {
      const u = uCurrent + (1 - uCurrent) * (i / restPts)
      const p = curveFull.getPointAt(Math.min(1, Math.max(0, u)))
      todoAttr.setXYZ(k++, p.x, p.y, p.z)
    }
    this.todoGeom.setDrawRange(0, k)
    doneAttr.needsUpdate = true
    todoAttr.needsUpdate = true
  }

  dispose(): void {
    this.markers.forEach((m) => {
      m.traverse((o) => {
        if (o instanceof THREE.Mesh) {
          o.geometry.dispose()
          if (o.material instanceof THREE.Material) o.material.dispose()
        }
      })
    })
    this.previewLine.geometry.dispose()
    ;(this.previewLine.material as THREE.Material).dispose()
    this.doneGeom.dispose()
    ;(this.lineDone.material as THREE.Material).dispose()
    this.todoGeom.dispose()
    ;(this.lineTodo.material as THREE.Material).dispose()
  }
}
