import * as THREE from 'three'

/**
 * 折线航迹：按 waypoint 顺序直线连接，无人机严格经过每个航点。
 */
export class PolylinePath {
  readonly points: THREE.Vector3[]
  readonly totalLength: number
  private readonly cumLen: number[]

  constructor(points: THREE.Vector3[]) {
    if (points.length === 0) {
      this.points = [new THREE.Vector3()]
      this.cumLen = [0]
      this.totalLength = 1
      return
    }
    this.points = points.map((p) => p.clone())
    this.cumLen = [0]
    for (let i = 1; i < this.points.length; i++) {
      this.cumLen.push(this.cumLen[i - 1]! + this.points[i]!.distanceTo(this.points[i - 1]!))
    }
    this.totalLength = Math.max(1, this.cumLen[this.cumLen.length - 1]!)
  }

  /** 归一化参数 0..1 */
  getPointAt(u: number): THREE.Vector3 {
    const d = THREE.MathUtils.clamp(u, 0, 1) * this.totalLength
    if (this.points.length === 1) return this.points[0]!.clone()
    for (let i = 1; i < this.cumLen.length; i++) {
      if (d <= this.cumLen[i]!) {
        const a = this.cumLen[i - 1]!
        const b = this.cumLen[i]!
        const seg = b - a
        const t = seg > 1e-6 ? (d - a) / seg : 0
        return new THREE.Vector3().lerpVectors(this.points[i - 1]!, this.points[i]!, t)
      }
    }
    return this.points[this.points.length - 1]!.clone()
  }

  getTangentAt(u: number): THREE.Vector3 {
    const d = THREE.MathUtils.clamp(u, 0, 1) * this.totalLength
    for (let i = 1; i < this.cumLen.length; i++) {
      if (d <= this.cumLen[i]! + 1e-6) {
        const dir = new THREE.Vector3().subVectors(this.points[i]!, this.points[i - 1]!)
        if (dir.lengthSq() < 1e-8 && i + 1 < this.points.length) {
          dir.subVectors(this.points[i + 1]!, this.points[i]!)
        }
        return dir.lengthSq() > 1e-8 ? dir.normalize() : new THREE.Vector3(0, 0, 1)
      }
    }
    const n = this.points.length
    if (n >= 2) {
      return new THREE.Vector3()
        .subVectors(this.points[n - 1]!, this.points[n - 2]!)
        .normalize()
    }
    return new THREE.Vector3(0, 0, 1)
  }

  /** 第 index 个航点在折线上的归一化参数 */
  getUAtIndex(index: number): number {
    if (index <= 0) return 0
    if (index >= this.cumLen.length) return 1
    return this.cumLen[index]! / this.totalLength
  }
}
