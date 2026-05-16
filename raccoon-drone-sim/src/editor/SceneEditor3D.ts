import * as THREE from 'three'
import { TransformControls } from 'three/examples/jsm/controls/TransformControls.js'
import type { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { applyMaterialProps, cloneMaterialProps, createEditorPrimitive, disposeEditorMesh } from './primitives'
import { placeOnTerrainAt, snapObjectBottomToTerrain } from './snapGround'
import { EditorHistory, serializeObject3DMatrix, deserializeMatrixToObject } from './history'
import type {
  EditorEntityUserData,
  EditorPrimitiveKind,
  EditorSceneFile,
  EditorSerializableMesh,
  EditorUiState,
  SelectionProps,
  TransformToolMode,
  OutlinerNode
} from './types'
import { isEditorObject } from './types'

export interface SceneEditor3DOptions {
  canvas: HTMLCanvasElement
  camera: THREE.PerspectiveCamera
  orbit: OrbitControls
  getWorld: () => THREE.Group | null
  getScene: () => THREE.Scene | null
  getActiveTab: () => 'patrol' | 'substation' | 'thermal'
  onUiChange: (s: EditorUiState) => void
}

function disposeObjectDeep(root: THREE.Object3D): void {
  root.traverse((o) => {
    if (o instanceof THREE.Mesh) {
      o.geometry?.dispose()
      const m = o.material as THREE.Material | THREE.Material[]
      if (Array.isArray(m)) m.forEach((x) => x.dispose())
      else m?.dispose()
    }
  })
}

export class SceneEditor3D {
  private readonly opts: SceneEditor3DOptions
  private active = false
  private readonly raycaster = new THREE.Raycaster()
  private userRoot: THREE.Group | null = null
  private importRoot: THREE.Group | null = null
  private grid: THREE.GridHelper | null = null
  private readonly transformControls: TransformControls
  private readonly selectionHelper = new THREE.Group()
  private readonly axesGroup = new THREE.Group()
  private boxHelper: THREE.BoxHelper | null = null
  private readonly history = new EditorHistory()
  private transformMode: TransformToolMode = 'translate'
  private gridVisible = true
  private space: 'world' | 'local' = 'world'
  private placementKind: EditorPrimitiveKind | null = null
  private readonly selectedIds = new Set<string>()
  private transformDragBefore = new Map<string, THREE.Matrix4>()
  private removeListeners: Array<() => void> = []
  private readonly _ndc = new THREE.Vector2()
  private readonly _tmpBox = new THREE.Box3()
  private readonly _tmpVec3 = new THREE.Vector3()
  private readonly _plane = new THREE.Plane()
  private readonly _planeN = new THREE.Vector3(0, 1, 0)
  private readonly _pickXZ = new THREE.Vector2()

  constructor(opts: SceneEditor3DOptions) {
    this.opts = opts
    this.transformControls = new TransformControls(opts.camera, opts.canvas)
    this.transformControls.setMode('translate')
    this.transformControls.setSpace('world')
    this.transformControls.setSize(0.95)
    this.transformControls.addEventListener('dragging-changed', (ev) => {
      const dragging = (ev as unknown as { value?: boolean }).value === true
      opts.orbit.enabled = !dragging && this.active
      if (dragging) this.beginTransformDrag()
      else this.endTransformDrag()
    })
    this.transformControls.addEventListener('change', () => {
      this.boxHelper?.update()
      this.syncAxesToPrimary()
      this.emitUi()
    })
  }

  bindWorld(world: THREE.Group, scene: THREE.Scene): void {
    this.unbindWorld()
    this.userRoot = new THREE.Group()
    this.userRoot.name = 'EditorUserRoot'
    world.add(this.userRoot)
    this.importRoot = new THREE.Group()
    this.importRoot.name = 'EditorImportRoot'
    world.add(this.importRoot)
    this.grid = new THREE.GridHelper(800, 80, 0x445566, 0x334455)
    this.grid.position.y = 0.02
    this.grid.visible = this.gridVisible
    scene.add(this.grid)
    scene.add(this.transformControls)
    scene.add(this.selectionHelper)
    scene.add(this.axesGroup)
    this.emitUi()
  }

  unbindWorld(): void {
    this.clearSelection()
    this.transformControls.detach()
    if (this.transformControls.parent) this.transformControls.parent.remove(this.transformControls)
    this.selectionHelper.clear()
    this.axesGroup.clear()
    if (this.boxHelper?.parent) this.boxHelper.parent.remove(this.boxHelper)
    this.boxHelper = null
    if (this.selectionHelper.parent) this.selectionHelper.parent.remove(this.selectionHelper)
    if (this.axesGroup.parent) this.axesGroup.parent.remove(this.axesGroup)
    if (this.grid?.parent) this.grid.parent.remove(this.grid)
    this.grid = null
    if (this.userRoot?.parent) this.userRoot.parent.remove(this.userRoot)
    if (this.importRoot?.parent) this.importRoot.parent.remove(this.importRoot)
    this.userRoot = null
    this.importRoot = null
  }

  setActive(on: boolean): void {
    this.active = on
    if (!on) {
      this.placementKind = null
      this.opts.orbit.enabled = true
      this.opts.canvas.style.cursor = ''
      this.clearSelection()
    }
    this.emitUi()
  }

  isActive(): boolean {
    return this.active
  }

  dispose(): void {
    this.setActive(false)
    this.removeListeners.forEach((f) => f())
    this.removeListeners.length = 0
    this.transformControls.dispose()
    this.unbindWorld()
  }

  mountInputHandlers(): void {
    const kd = (e: KeyboardEvent) => this.onKeyDown(e)
    window.addEventListener('keydown', kd)
    this.removeListeners.push(() => window.removeEventListener('keydown', kd))
    const pd = (e: PointerEvent) => this.onPointerDown(e)
    this.opts.canvas.addEventListener('pointerdown', pd, { capture: true })
    this.removeListeners.push(() => this.opts.canvas.removeEventListener('pointerdown', pd, { capture: true }))
  }

  /**
   * 从屏幕 NDC 得到地表附近的 XZ：优先射线与 world 网格求交；若无命中（极端视角/数值）则用水平面回退。
   * placeOnTerrainAt 会再沿 -Y 贴地，故 XZ 近似即可。
   */
  private computePlacementXZ(world: THREE.Group, ndcX: number, ndcY: number, out: THREE.Vector2): boolean {
    const cam = this.opts.camera
    this._ndc.set(ndcX, ndcY)
    this.raycaster.setFromCamera(this._ndc, cam)
    this.raycaster.layers.mask = cam.layers.mask
    const hits = this.raycaster.intersectObject(world, true)
    if (hits.length) {
      const p = hits[0]!.point
      out.set(p.x, p.z)
      return true
    }
    this._tmpBox.setFromObject(world)
    if (this._tmpBox.isEmpty()) return false
    const yMin = this._tmpBox.min.y
    const yMax = this._tmpBox.max.y
    const yMid = this._tmpBox.getCenter(this._tmpVec3).y
    for (const y of [yMid, yMin + 1, yMax - 1, 0]) {
      if (y < yMin - 500 || y > yMax + 500) continue
      this._plane.set(this._planeN, -y)
      if (this.raycaster.ray.intersectPlane(this._plane, this._tmpVec3)) {
        out.set(this._tmpVec3.x, this._tmpVec3.z)
        return true
      }
    }
    return false
  }

  setTransformMode(m: TransformToolMode): void {
    this.transformMode = m
    this.transformControls.setMode(m)
    this.emitUi()
  }

  setSpace(s: 'world' | 'local'): void {
    this.space = s
    this.transformControls.setSpace(s)
    this.emitUi()
  }

  toggleGrid(): void {
    this.gridVisible = !this.gridVisible
    if (this.grid) this.grid.visible = this.gridVisible
    this.emitUi()
  }

  beginPlacement(kind: EditorPrimitiveKind): void {
    if (!this.active || kind === 'imported') return
    this.placementKind = kind
    this.opts.canvas.style.cursor = 'crosshair'
    this.emitUi()
  }

  cancelPlacement(): void {
    this.placementKind = null
    this.opts.canvas.style.cursor = ''
    this.emitUi()
  }

  exportSceneJson(): string {
    const tab = this.opts.getActiveTab()
    const objs = this.collectSerializable()
    const file: EditorSceneFile = { version: 1, tab, objects: objs }
    return JSON.stringify(file, null, 2)
  }

  importSceneJson(json: string): void {
    let data: EditorSceneFile
    try {
      data = JSON.parse(json) as EditorSceneFile
    } catch {
      throw new Error('JSON 无效')
    }
    if (data.version !== 1 || !Array.isArray(data.objects)) throw new Error('不支持的文件格式')
    this.clearUserObjects()
    const world = this.opts.getWorld()
    if (!world || !this.userRoot) return
    const ignore = this.collectIgnoreSet()
    for (const o of data.objects) {
      if (o.kind === 'imported') continue
      const mesh = createEditorPrimitive(o.kind, o.spec)
      ;(mesh.userData as EditorEntityUserData).editorId = o.id
      ;(mesh.userData as EditorEntityUserData).editorLabel = o.label
      mesh.visible = o.visible
      deserializeMatrixToObject(mesh, o.matrix)
      applyMaterialProps(mesh.material as THREE.MeshStandardMaterial, o.material)
      this.userRoot.add(mesh)
      snapObjectBottomToTerrain(mesh, world, this.raycaster, ignore)
    }
    this.refreshHighlights()
    this.emitUi()
  }

  clearUserObjects(): void {
    if (!this.userRoot || !this.importRoot) return
    while (this.userRoot.children.length) {
      const ch = this.userRoot.children[0]!
      this.userRoot.remove(ch)
      disposeObjectDeep(ch)
    }
    while (this.importRoot.children.length) {
      const ch = this.importRoot.children[0]!
      this.importRoot.remove(ch)
      disposeObjectDeep(ch)
    }
    this.clearSelection()
    this.history.clear()
    this.emitUi()
  }

  groupSelection(): void {
    /* 预留：与撤销/序列化打通后再实现父子层级分组 */
  }

  applyProps(p: Partial<SelectionProps>): void {
    const objs = [...this.selectedIds].map((id) => this.findByEditorId(id)).filter(Boolean) as THREE.Object3D[]
    if (!objs.length) return
    for (const obj of objs) {
      if (p.x !== undefined) obj.position.x = p.x
      if (p.y !== undefined) obj.position.y = p.y
      if (p.z !== undefined) obj.position.z = p.z
      if (p.rx !== undefined || p.ry !== undefined || p.rz !== undefined) {
        const e = new THREE.Euler().setFromQuaternion(obj.quaternion, 'XYZ')
        if (p.rx !== undefined) e.x = THREE.MathUtils.degToRad(p.rx)
        if (p.ry !== undefined) e.y = THREE.MathUtils.degToRad(p.ry)
        if (p.rz !== undefined) e.z = THREE.MathUtils.degToRad(p.rz)
        obj.quaternion.setFromEuler(e)
      }
      if (p.sx !== undefined) obj.scale.x = p.sx
      if (p.sy !== undefined) obj.scale.y = p.sy
      if (p.sz !== undefined) obj.scale.z = p.sz
      obj.traverse((o) => {
        if (o instanceof THREE.Mesh && o.material) {
          const mat = o.material as THREE.MeshStandardMaterial
          if (mat.color) {
            applyMaterialProps(mat, {
              color: p.color ?? '#' + mat.color.getHexString(),
              metalness: p.metalness ?? mat.metalness,
              roughness: p.roughness ?? mat.roughness,
              opacity: p.opacity ?? mat.opacity,
              transparent: p.transparent ?? mat.transparent
            })
          }
        }
      })
      obj.updateMatrixWorld(true)
    }
    this.refreshHighlights()
    this.emitUi()
  }

  deleteSelected(): void {
    if (!this.userRoot) return
    for (const id of [...this.selectedIds]) {
      const o = this.findByEditorId(id)
      if (!o) continue
      const snap = this.serializeObject(o)
      o.parent?.remove(o)
      disposeObjectDeep(o)
      this.history.push({ type: 'remove', id: snap.id, json: snap })
    }
    this.clearSelection()
    this.emitUi()
  }

  undo(): void {
    const cmd = this.history.popForUndo()
    if (!cmd) return
    if (cmd.type === 'add') {
      const o = this.findByEditorId(cmd.id)
      if (o) {
        o.parent?.remove(o)
        disposeObjectDeep(o)
      }
    } else if (cmd.type === 'remove') {
      this.restoreFromSnapshot(cmd.json)
    } else if (cmd.type === 'transform') {
      cmd.ids.forEach((id, i) => {
        const o = this.findByEditorId(id)
        if (o) deserializeMatrixToObject(o, cmd.before[i]!)
      })
    }
    this.refreshHighlights()
    this.emitUi()
  }

  redo(): void {
    const cmd = this.history.popForRedo()
    if (!cmd) return
    if (cmd.type === 'add') {
      this.restoreFromSnapshot(cmd.json)
    } else if (cmd.type === 'remove') {
      const o = this.findByEditorId(cmd.id)
      if (o) {
        o.parent?.remove(o)
        disposeObjectDeep(o)
      }
    } else if (cmd.type === 'transform') {
      cmd.ids.forEach((id, i) => {
        const o = this.findByEditorId(id)
        if (o) deserializeMatrixToObject(o, cmd.after[i]!)
      })
    }
    this.refreshHighlights()
    this.emitUi()
  }

  canUndo(): boolean {
    return this.history.canUndo()
  }

  canRedo(): boolean {
    return this.history.canRedo()
  }

  importGlbFile(file: File): Promise<void> {
    const url = URL.createObjectURL(file)
    return new Promise((resolve, reject) => {
      const loader = new GLTFLoader()
      loader.load(
        url,
        (gltf) => {
          URL.revokeObjectURL(url)
          const root = gltf.scene
          root.traverse((o) => {
            if (o instanceof THREE.Mesh) {
              o.castShadow = true
              o.receiveShadow = true
            }
          })
          const ud: EditorEntityUserData = {
            editorEntity: true,
            editorId: `ed-${Date.now()}-imp`,
            editorKind: 'imported',
            editorLabel: file.name,
            editorSpec: {}
          }
          root.userData = { ...root.userData, ...ud }
          if (this.importRoot) {
            this.importRoot.add(root)
            const world = this.opts.getWorld()
            if (world) snapObjectBottomToTerrain(root, world, this.raycaster, this.collectIgnoreSet())
          }
          this.emitUi()
          resolve()
        },
        undefined,
        (err) => {
          URL.revokeObjectURL(url)
          reject(err)
        }
      )
    })
  }

  selectById(id: string, additive: boolean): void {
    this.select(id, additive)
  }

  renameObject(id: string, label: string): void {
    const o = this.findByEditorId(id)
    if (!o) return
    ;(o.userData as EditorEntityUserData).editorLabel = label
    o.name = label
    this.emitUi()
  }

  toggleVisible(id: string): void {
    const o = this.findByEditorId(id)
    if (!o) return
    o.visible = !o.visible
    this.emitUi()
  }

  setObjectVisible(id: string, visible: boolean): void {
    const o = this.findByEditorId(id)
    if (!o) return
    o.visible = visible
    this.emitUi()
  }

  createPrimitiveAtViewCenter(
    kind: EditorPrimitiveKind,
    dims: { wx: number; wy: number; wz: number; radius: number; height: number; planeW: number; planeD: number }
  ): void {
    if (kind === 'imported') return
    const world = this.opts.getWorld()
    const cam = this.opts.camera
    if (!world || !this.userRoot) return
    const opts =
      kind === 'box'
        ? { wx: dims.wx, wy: dims.wy, wz: dims.wz }
        : kind === 'sphere'
          ? { radius: dims.radius }
          : kind === 'cylinder' || kind === 'cone'
            ? { radius: dims.radius, height: dims.height }
            : kind === 'plane'
              ? { planeW: dims.planeW, planeD: dims.planeD }
              : { wx: dims.wx, wy: dims.height, wz: dims.wz, radius: dims.radius, height: dims.height }
    const mesh = createEditorPrimitive(kind, opts)
    const ignore = this.collectIgnoreSet()
    this.userRoot.add(mesh)
    if (this.computePlacementXZ(world, 0, 0, this._pickXZ)) {
      this.raycaster.setFromCamera(new THREE.Vector2(0, 0), cam)
      this.raycaster.layers.mask = cam.layers.mask
      placeOnTerrainAt(mesh, world, this.raycaster, this._pickXZ, ignore)
    } else {
      mesh.position.set(0, 80, 0)
      snapObjectBottomToTerrain(mesh, world, this.raycaster, ignore)
    }
    const snap = this.serializeObject(mesh)
    this.history.push({ type: 'add', id: snap.id, json: snap })
    this.select(snap.id, false)
    this.emitUi()
  }

  private collectIgnoreSet(): Set<THREE.Object3D> {
    const s = new Set<THREE.Object3D>()
    this.userRoot?.traverse((o) => {
      if (isEditorObject(o)) s.add(o)
    })
    this.importRoot?.traverse((o) => {
      if (isEditorObject(o)) s.add(o)
    })
    return s
  }

  private findByEditorId(id: string): THREE.Object3D | null {
    const scan = (root: THREE.Object3D | null): THREE.Object3D | null => {
      if (!root) return null
      if (isEditorObject(root) && root.userData.editorId === id) return root
      for (const c of root.children) {
        const r = scan(c)
        if (r) return r
      }
      return null
    }
    return scan(this.userRoot) || scan(this.importRoot)
  }

  private clearSelection(): void {
    this.selectedIds.clear()
    this.transformControls.detach()
    this.selectionHelper.clear()
    this.axesGroup.clear()
    if (this.boxHelper?.parent) this.boxHelper.parent.remove(this.boxHelper)
    this.boxHelper = null
    this.emitUi()
  }

  private select(id: string, additive: boolean): void {
    if (!additive) this.selectedIds.clear()
    this.selectedIds.add(id)
    const primary = this.getPrimaryObject()
    if (primary) {
      if (this.selectedIds.size === 1) this.transformControls.attach(primary)
      else this.transformControls.detach()
      this.refreshHighlights()
      this.syncAxesToPrimary()
    }
    this.emitUi()
  }

  private getPrimaryObject(): THREE.Object3D | null {
    const first = [...this.selectedIds][0]
    return first ? this.findByEditorId(first) : null
  }

  private refreshHighlights(): void {
    this.selectionHelper.clear()
    if (this.boxHelper?.parent) this.boxHelper.parent.remove(this.boxHelper)
    this.boxHelper = null
    const primary = this.getPrimaryObject()
    if (!primary) return
    this.boxHelper = new THREE.BoxHelper(primary, 0xffffff)
    this.selectionHelper.add(this.boxHelper)
  }

  private syncAxesToPrimary(): void {
    this.axesGroup.clear()
    const m = this.getPrimaryObject()
    if (!m) return
    const ax = new THREE.AxesHelper(6)
    const wp = new THREE.Vector3()
    m.getWorldPosition(wp)
    ax.position.copy(wp)
    ax.setRotationFromQuaternion(m.getWorldQuaternion(new THREE.Quaternion()))
    this.axesGroup.add(ax)
  }

  private beginTransformDrag(): void {
    this.transformDragBefore.clear()
    for (const id of this.selectedIds) {
      const o = this.findByEditorId(id)
      if (o) this.transformDragBefore.set(id, o.matrix.clone())
    }
  }

  private endTransformDrag(): void {
    if (this.transformDragBefore.size === 0) return
    const ids: string[] = []
    const before: number[][] = []
    const after: number[][] = []
    let changed = false
    for (const id of this.selectedIds) {
      const o = this.findByEditorId(id)
      const prev = this.transformDragBefore.get(id)
      if (!o || !prev) continue
      o.updateMatrix()
      if (!o.matrix.equals(prev)) changed = true
      ids.push(id)
      before.push(prev.toArray())
      after.push(o.matrix.toArray())
    }
    this.transformDragBefore.clear()
    if (changed && ids.length) this.history.push({ type: 'transform', ids, before, after })
    this.refreshHighlights()
    this.emitUi()
  }

  private onPointerDown(e: PointerEvent): void {
    if (!this.active || e.button !== 0) return
    const world = this.opts.getWorld()
    if (!world || !this.userRoot) return
    const rect = this.opts.canvas.getBoundingClientRect()
    const rw = Math.max(rect.width, 1e-6)
    const rh = Math.max(rect.height, 1e-6)
    const mx = ((e.clientX - rect.left) / rw) * 2 - 1
    const my = -((e.clientY - rect.top) / rh) * 2 + 1

    if (this.placementKind) {
      if (!this.computePlacementXZ(world, mx, my, this._pickXZ)) return
      e.preventDefault()
      e.stopPropagation()
      e.stopImmediatePropagation()
      const mesh = createEditorPrimitive(this.placementKind)
      const ignore = this.collectIgnoreSet()
      this.userRoot.add(mesh)
      this.raycaster.setFromCamera(this._ndc.set(mx, my), this.opts.camera)
      this.raycaster.layers.mask = this.opts.camera.layers.mask
      placeOnTerrainAt(mesh, world, this.raycaster, this._pickXZ, ignore)
      const snap = this.serializeObject(mesh)
      this.history.push({ type: 'add', id: snap.id, json: snap })
      this.cancelPlacement()
      this.select(snap.id, false)
      return
    }

    this.raycaster.setFromCamera(new THREE.Vector2(mx, my), this.opts.camera)
    this.raycaster.layers.mask = this.opts.camera.layers.mask
    const hits = this.raycaster.intersectObject(world, true)
    let picked: THREE.Object3D | null = null
    for (const h of hits) {
      let o: THREE.Object3D | null = h.object
      while (o) {
        if (isEditorObject(o)) {
          picked = o
          break
        }
        o = o.parent
      }
      if (picked) break
    }
    if (picked) {
      e.preventDefault()
      e.stopPropagation()
      const id = (picked.userData as EditorEntityUserData).editorId
      const additive = e.ctrlKey || e.metaKey
      this.select(id, additive)
      return
    }
    if (!e.shiftKey) this.clearSelection()
  }

  private onKeyDown(e: KeyboardEvent): void {
    if (!this.active) return
    const tag = (e.target as HTMLElement)?.tagName
    if (tag === 'INPUT' || tag === 'TEXTAREA') return
    if (e.key === 'Delete') {
      if (this.selectedIds.size) {
        e.preventDefault()
        this.deleteSelected()
      }
    }
    if (e.ctrlKey && e.key.toLowerCase() === 'z') {
      e.preventDefault()
      if (e.shiftKey) this.redo()
      else this.undo()
    }
    if (e.ctrlKey && e.key.toLowerCase() === 'y') {
      e.preventDefault()
      this.redo()
    }
    if (e.code === 'KeyQ') {
      this.setSpace(this.space === 'world' ? 'local' : 'world')
    }
    const ctl = this.opts.orbit
    const cam = this.opts.camera
    const move = (dx: number, dz: number) => {
      const dir = new THREE.Vector3()
      cam.getWorldDirection(dir)
      dir.y = 0
      if (dir.lengthSq() < 1e-6) return
      dir.normalize()
      const right = new THREE.Vector3().crossVectors(dir, new THREE.Vector3(0, 1, 0)).normalize()
      const delta = right.clone().multiplyScalar(dx).add(dir.multiplyScalar(dz))
      cam.position.add(delta)
      ctl.target.add(delta)
      ctl.update()
    }
    const step = 2.4
    if (e.code === 'KeyW') {
      e.preventDefault()
      move(0, step)
    }
    if (e.code === 'KeyS') {
      e.preventDefault()
      move(0, -step)
    }
    if (e.code === 'KeyA') {
      e.preventDefault()
      move(-step, 0)
    }
    if (e.code === 'KeyD') {
      e.preventDefault()
      move(step, 0)
    }
  }

  private buildTree(): OutlinerNode[] {
    const list: OutlinerNode[] = []
    const scanRoot = (root: THREE.Group | null) => {
      if (!root) return
      for (const ch of root.children) {
        if (isEditorObject(ch)) {
          const ud = ch.userData as EditorEntityUserData
          list.push({
            id: ud.editorId,
            label: ud.editorLabel,
            kind: ud.editorKind === 'imported' ? 'imported' : ud.editorKind,
            type: ch.type,
            visible: ch.visible
          })
        }
      }
    }
    scanRoot(this.userRoot)
    scanRoot(this.importRoot)
    return list
  }

  private emitUi(): void {
    this.opts.onUiChange({
      tree: this.buildTree(),
      selectionIds: [...this.selectedIds],
      transformMode: this.transformMode,
      gridVisible: this.gridVisible,
      placementKind: this.placementKind,
      space: this.space,
      props: this.buildSelectionProps()
    })
  }

  private buildSelectionProps(): SelectionProps | null {
    const o = this.getPrimaryObject()
    if (!o) return null
    const e = new THREE.Euler().setFromQuaternion(o.quaternion, 'XYZ')
    let mat: THREE.MeshStandardMaterial | null = null
    o.traverse((c) => {
      if (!mat && c instanceof THREE.Mesh && c.material) mat = c.material as THREE.MeshStandardMaterial
    })
    const mp = mat ? cloneMaterialProps(mat) : { color: '#888888', metalness: 0.2, roughness: 0.5, opacity: 1, transparent: false }
    const ud = o.userData as EditorEntityUserData
    return {
      ids: [...this.selectedIds],
      single: this.selectedIds.size === 1,
      label: ud.editorLabel,
      x: o.position.x,
      y: o.position.y,
      z: o.position.z,
      rx: (e.x * 180) / Math.PI,
      ry: (e.y * 180) / Math.PI,
      rz: (e.z * 180) / Math.PI,
      sx: o.scale.x,
      sy: o.scale.y,
      sz: o.scale.z,
      ...mp,
      space: this.space
    }
  }

  private serializeObject(obj: THREE.Object3D): EditorSerializableMesh {
    const ud = obj.userData as EditorEntityUserData
    let matProps = { color: '#888888', metalness: 0.2, roughness: 0.5, opacity: 1, transparent: false }
    obj.traverse((c) => {
      if (c instanceof THREE.Mesh && c.material) {
        matProps = cloneMaterialProps(c.material as THREE.MeshStandardMaterial)
      }
    })
    return {
      id: ud.editorId,
      label: ud.editorLabel,
      kind: ud.editorKind,
      parentId: null,
      matrix: serializeObject3DMatrix(obj),
      visible: obj.visible,
      material: matProps,
      spec: { ...ud.editorSpec }
    }
  }

  private collectSerializable(): EditorSerializableMesh[] {
    const out: EditorSerializableMesh[] = []
    const seen = new Set<string>()
    const visit = (root: THREE.Group | null) => {
      if (!root) return
      root.traverse((o) => {
        if (!isEditorObject(o)) return
        const id = (o.userData as EditorEntityUserData).editorId
        if (seen.has(id)) return
        seen.add(id)
        out.push(this.serializeObject(o))
      })
    }
    visit(this.userRoot)
    visit(this.importRoot)
    return out
  }

  private restoreFromSnapshot(s: EditorSerializableMesh): void {
    const world = this.opts.getWorld()
    if (!world || !this.userRoot) return
    if (s.kind === 'imported') return
    const mesh = createEditorPrimitive(s.kind, s.spec)
    ;(mesh.userData as EditorEntityUserData).editorId = s.id
    ;(mesh.userData as EditorEntityUserData).editorLabel = s.label
    mesh.visible = s.visible
    deserializeMatrixToObject(mesh, s.matrix)
    applyMaterialProps(mesh.material as THREE.MeshStandardMaterial, s.material)
    this.userRoot.add(mesh)
  }
}
