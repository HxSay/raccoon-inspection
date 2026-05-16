import * as THREE from 'three'
import type { EditorEntityUserData, EditorPrimitiveKind } from './types'

let checkerSeq = 0

export function createCheckerTexture(size = 128, cells = 8): THREE.CanvasTexture {
  const c = document.createElement('canvas')
  c.width = size
  c.height = size
  const g = c.getContext('2d')!
  const cell = size / cells
  for (let i = 0; i < cells; i++) {
    for (let j = 0; j < cells; j++) {
      g.fillStyle = (i + j) % 2 === 0 ? '#2a3340' : '#4a5a6a'
      g.fillRect(i * cell, j * cell, cell + 0.5, cell + 0.5)
    }
  }
  const tex = new THREE.CanvasTexture(c)
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping
  tex.colorSpace = THREE.SRGBColorSpace
  tex.repeat.set(2, 2)
  return tex
}

function baseMat(color: number, map?: THREE.Texture): THREE.MeshStandardMaterial {
  const m = new THREE.MeshStandardMaterial({
    color,
    metalness: 0.22,
    roughness: 0.55,
    map,
    transparent: false,
    opacity: 1
  })
  m.envMapIntensity = 0.45
  return m
}

function nextId(): string {
  return `ed-${Date.now()}-${++checkerSeq}`
}

export interface PrimitiveCreateOptions {
  wx?: number
  wy?: number
  wz?: number
  radius?: number
  height?: number
  planeW?: number
  planeD?: number
}

export function createEditorPrimitive(
  kind: EditorPrimitiveKind,
  opts: PrimitiveCreateOptions = {}
): THREE.Mesh {
  const wx = opts.wx ?? 4
  const wy = opts.wy ?? 3
  const wz = opts.wz ?? 4
  const r = opts.radius ?? 1.5
  const h = opts.height ?? 5
  const pw = opts.planeW ?? 40
  const pd = opts.planeD ?? 40

  let geo: THREE.BufferGeometry
  let label: string
  const spec: Record<string, number> = {}

  switch (kind) {
    case 'box':
      geo = new THREE.BoxGeometry(wx, wy, wz)
      label = `立方体 ${wx}×${wy}×${wz}`
      Object.assign(spec, { wx, wy, wz })
      break
    case 'sphere':
      geo = new THREE.SphereGeometry(r, 32, 24)
      label = `球体 r=${r}`
      Object.assign(spec, { radius: r })
      break
    case 'cylinder':
      geo = new THREE.CylinderGeometry(r, r, h, 28)
      label = `圆柱 r=${r} h=${h}`
      Object.assign(spec, { radius: r, height: h })
      break
    case 'cone':
      geo = new THREE.ConeGeometry(r, h, 28)
      label = `圆锥 r=${r} h=${h}`
      Object.assign(spec, { radius: r, height: h })
      break
    case 'plane': {
      geo = new THREE.PlaneGeometry(pw, pd)
      geo.rotateX(-Math.PI / 2)
      label = `地面 ${pw}×${pd}`
      Object.assign(spec, { planeW: pw, planeD: pd })
      break
    }
    case 'pipe': {
      const path = new THREE.CatmullRomCurve3([
        new THREE.Vector3(0, 0, 0),
        new THREE.Vector3(0, h * 0.35, wz * 0.4),
        new THREE.Vector3(wx * 0.35, h * 0.7, wz * 0.5),
        new THREE.Vector3(wx * 0.5, h, wz * 0.45)
      ])
      geo = new THREE.TubeGeometry(path, 48, Math.max(0.35, r * 0.35), 12, false)
      label = `管道`
      Object.assign(spec, { wx, wy, wz, radius: r, height: h })
      break
    }
    default:
      geo = new THREE.BoxGeometry(2, 2, 2)
      label = '立方体'
  }

  const checker = kind === 'pipe' ? createCheckerTexture(128, 10) : undefined
  if (checker) checker.repeat.set(6, 2)
  const mat = baseMat(kind === 'pipe' ? 0x8899aa : 0x6a7a8a, checker)
  const mesh = new THREE.Mesh(geo, mat)
  mesh.castShadow = true
  mesh.receiveShadow = true
  mesh.name = label

  const ud: EditorEntityUserData = {
    editorEntity: true,
    editorId: nextId(),
    editorKind: kind,
    editorLabel: label,
    editorSpec: spec
  }
  mesh.userData = { ...mesh.userData, ...ud }
  return mesh
}

export function disposeEditorMesh(mesh: THREE.Mesh): void {
  mesh.traverse((o) => {
    if (o instanceof THREE.Mesh) {
      o.geometry?.dispose()
      const m = o.material as THREE.MeshStandardMaterial | THREE.MeshStandardMaterial[]
      if (Array.isArray(m)) m.forEach((x) => x.dispose())
      else m?.dispose()
    }
  })
}

export function cloneMaterialProps(mat: THREE.MeshStandardMaterial): {
  color: string
  metalness: number
  roughness: number
  opacity: number
  transparent: boolean
} {
  return {
    color: '#' + mat.color.getHexString(),
    metalness: mat.metalness,
    roughness: mat.roughness,
    opacity: mat.opacity,
    transparent: mat.transparent
  }
}

export function applyMaterialProps(
  mat: THREE.MeshStandardMaterial,
  p: { color: string; metalness: number; roughness: number; opacity: number; transparent: boolean }
): void {
  mat.color.set(p.color)
  mat.metalness = p.metalness
  mat.roughness = p.roughness
  mat.opacity = p.opacity
  mat.transparent = p.transparent
  mat.depthWrite = !p.transparent || p.opacity > 0.98
  mat.needsUpdate = true
}
