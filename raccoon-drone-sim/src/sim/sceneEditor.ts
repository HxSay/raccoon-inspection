import * as THREE from 'three'
import { TransformControls } from 'three/examples/jsm/controls/TransformControls.js'
import type { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

/** 当前选中物体（用于侧栏数值编辑） */
export interface SceneObjectEditorSnapshot {
  uuid: string
  type: string
  label: string
  x: number
  y: number
  z: number
  rotYdeg: number
  sx: number
  sy: number
  sz: number
}

export type ScenePickable = THREE.Mesh | THREE.Line | THREE.LineSegments | THREE.Points

function isPickable(o: THREE.Object3D): o is ScenePickable {
  return (
    o instanceof THREE.Mesh ||
    o instanceof THREE.Line ||
    o instanceof THREE.LineSegments ||
    o instanceof THREE.Points
  )
}

function isLockedChain(o: THREE.Object3D): boolean {
  let p: THREE.Object3D | null = o
  while (p) {
    if (p.userData.noScenePick === true) return true
    p = p.parent
  }
  return false
}

function disposeSubtreeResources(root: THREE.Object3D): void {
  root.traverse((c) => {
    if (c instanceof THREE.Mesh || c instanceof THREE.Line || c instanceof THREE.LineSegments || c instanceof THREE.Points) {
      c.geometry?.dispose()
      const m = c.material as THREE.Material | THREE.Material[] | undefined
      if (!m) return
      const list = Array.isArray(m) ? m : [m]
      for (const mat of list) {
        const std = mat as THREE.MeshStandardMaterial & { userData?: { skipDispose?: boolean } }
        if (!std.userData?.skipDispose) mat.dispose()
      }
    }
  })
}

/**
 * 场景物体编辑：Shift+左键点选，`TransformControls` 平移；表单改数值 / 删除（可由宿主写入本地持久化）。
 * 业务对象（机巢、终端、无人机、机器狗、航线可视化）应设 `userData.noScenePick = true`。
 */
export class SceneObjectEditor {
  private readonly raycaster = new THREE.Raycaster()
  private readonly transformControls: TransformControls
  private selected: ScenePickable | null = null
  private readonly removePointerDown: () => void
  enabled = false

  constructor(
    private readonly canvas: HTMLCanvasElement,
    camera: THREE.PerspectiveCamera,
    private readonly orbit: OrbitControls,
    private readonly getContext: () => { scene: THREE.Scene; world: THREE.Group } | null,
    private readonly onSelection: (snap: SceneObjectEditorSnapshot | null) => void,
    private readonly onObjectDeleted?: (obj: ScenePickable) => void
  ) {
    this.transformControls = new TransformControls(camera, canvas)
    this.transformControls.setMode('translate')
    this.transformControls.setSize(0.88)
    this.transformControls.addEventListener('change', () => this.emitSnapshot())
    this.transformControls.addEventListener('dragging-changed', (ev) => {
      const dragging = (ev as unknown as { value?: boolean }).value === true
      this.orbit.enabled = !dragging
    })

    const onPointerDown = (e: PointerEvent) => {
      if (!this.enabled || e.button !== 0 || !e.shiftKey) return
      const ctx = this.getContext()
      if (!ctx) return
      e.preventDefault()
      e.stopPropagation()
      const rect = this.canvas.getBoundingClientRect()
      const mx = ((e.clientX - rect.left) / rect.width) * 2 - 1
      const my = -((e.clientY - rect.top) / rect.height) * 2 + 1
      this.raycaster.setFromCamera(new THREE.Vector2(mx, my), camera)
      const hits = this.raycaster.intersectObject(ctx.world, true)
      const pick = this.pickFirst(hits)
      if (pick) this.select(pick, ctx.scene)
      else this.clearSelection()
    }

    this.canvas.addEventListener('pointerdown', onPointerDown, { capture: true })
    this.removePointerDown = () => this.canvas.removeEventListener('pointerdown', onPointerDown, { capture: true })
  }

  setEnabled(on: boolean): void {
    this.enabled = on
    if (!on) this.clearSelection()
  }

  /** Tab 切换时把辅助器挂到新场景并清空选中 */
  rebindToActiveScene(): void {
    const ctx = this.getContext()
    this.clearSelection()
    if (this.transformControls.parent) {
      this.transformControls.parent.remove(this.transformControls)
    }
    if (ctx) ctx.scene.add(this.transformControls)
  }

  private pickFirst(hits: THREE.Intersection[]): ScenePickable | null {
    for (const h of hits) {
      const o = h.object
      if (!isPickable(o)) continue
      if (isLockedChain(o)) continue
      return o
    }
    return null
  }

  private select(obj: ScenePickable, scene: THREE.Scene): void {
    this.clearSelection()
    this.selected = obj
    this.transformControls.attach(obj)
    if (!scene.children.includes(this.transformControls)) {
      scene.add(this.transformControls)
    }
    this.emitSnapshot()
  }

  clearSelection(): void {
    this.transformControls.detach()
    this.selected = null
    this.onSelection(null)
  }

  private emitSnapshot(): void {
    const o = this.selected
    if (!o) {
      this.onSelection(null)
      return
    }
    const e = new THREE.Euler().setFromQuaternion(o.quaternion, 'YXZ')
    this.onSelection({
      uuid: o.uuid,
      type: o.type,
      label: (o.name && o.name.length ? o.name : o.type) + ' · ' + o.uuid.slice(0, 8),
      x: o.position.x,
      y: o.position.y,
      z: o.position.z,
      rotYdeg: (e.y * 180) / Math.PI,
      sx: o.scale.x,
      sy: o.scale.y,
      sz: o.scale.z
    })
  }

  applyFromForm(p: { x: number; y: number; z: number; rotYdeg: number; sx: number; sy: number; sz: number }): void {
    const o = this.selected
    if (!o) return
    o.position.set(p.x, p.y, p.z)
    o.rotation.set(0, THREE.MathUtils.degToRad(p.rotYdeg), 0, 'YXZ')
    o.scale.set(p.sx, p.sy, p.sz)
    o.updateMatrixWorld(true)
    this.emitSnapshot()
  }

  deleteSelected(): void {
    const o = this.selected
    if (!o) return
    this.onObjectDeleted?.(o)
    this.transformControls.detach()
    const parent = o.parent
    if (parent) parent.remove(o)
    disposeSubtreeResources(o)
    this.selected = null
    this.onSelection(null)
  }

  dispose(): void {
    this.setEnabled(false)
    this.clearSelection()
    this.removePointerDown()
    this.transformControls.dispose()
    if (this.transformControls.parent) {
      this.transformControls.parent.remove(this.transformControls)
    }
  }
}
