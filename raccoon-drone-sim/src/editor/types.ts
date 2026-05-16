import type { Object3D } from 'three'

export type EditorPrimitiveKind = 'box' | 'sphere' | 'cylinder' | 'cone' | 'plane' | 'pipe' | 'imported'

export interface EditorEntityUserData {
  editorEntity: true
  editorId: string
  editorKind: EditorPrimitiveKind
  editorLabel: string
  editorSpec: Record<string, number>
}

export interface OutlinerNode {
  id: string
  label: string
  kind: EditorPrimitiveKind | 'group'
  type: string
  visible: boolean
  children?: OutlinerNode[]
}

export type TransformToolMode = 'translate' | 'rotate' | 'scale'

export interface EditorSerializableMesh {
  id: string
  label: string
  kind: EditorPrimitiveKind
  parentId: string | null
  matrix: number[]
  visible: boolean
  material: {
    color: string
    metalness: number
    roughness: number
    opacity: number
    transparent: boolean
  }
  spec: Record<string, number>
  glbDataUrl?: string
}

export interface EditorSceneFile {
  version: 1
  tab: 'patrol' | 'substation' | 'thermal'
  objects: EditorSerializableMesh[]
}

export interface SelectionProps {
  ids: string[]
  single: boolean
  label: string
  x: number
  y: number
  z: number
  rx: number
  ry: number
  rz: number
  sx: number
  sy: number
  sz: number
  color: string
  metalness: number
  roughness: number
  opacity: number
  transparent: boolean
  space: 'world' | 'local'
}

export interface EditorUiState {
  tree: OutlinerNode[]
  selectionIds: string[]
  transformMode: TransformToolMode
  gridVisible: boolean
  placementKind: EditorPrimitiveKind | null
  space: 'world' | 'local'
  props: SelectionProps | null
}

export function isEditorObject(o: Object3D): o is Object3D & { userData: EditorEntityUserData } {
  return (o as Object3D).userData?.editorEntity === true
}
