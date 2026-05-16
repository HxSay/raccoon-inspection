import * as THREE from 'three'
import type { EditorSerializableMesh } from './types'

export type HistoryCmd =
  | { type: 'add'; id: string; json: EditorSerializableMesh }
  | { type: 'remove'; id: string; json: EditorSerializableMesh }
  | { type: 'transform'; ids: string[]; before: number[][]; after: number[][] }

const MAX = 20

export class EditorHistory {
  private undo: HistoryCmd[] = []
  private redo: HistoryCmd[] = []

  push(cmd: HistoryCmd): void {
    this.undo.push(cmd)
    if (this.undo.length > MAX) this.undo.shift()
    this.redo.length = 0
  }

  popForUndo(): HistoryCmd | undefined {
    const c = this.undo.pop()
    if (c) this.redo.push(c)
    return c
  }

  popForRedo(): HistoryCmd | undefined {
    const c = this.redo.pop()
    if (c) this.undo.push(c)
    return c
  }

  canUndo(): boolean {
    return this.undo.length > 0
  }

  canRedo(): boolean {
    return this.redo.length > 0
  }

  clear(): void {
    this.undo.length = 0
    this.redo.length = 0
  }
}

export function serializeObject3DMatrix(obj: THREE.Object3D): number[] {
  obj.updateMatrix()
  return obj.matrix.toArray()
}

export function deserializeMatrixToObject(obj: THREE.Object3D, arr: number[]): void {
  obj.matrix.fromArray(arr)
  obj.matrix.decompose(obj.position, obj.quaternion, obj.scale)
  obj.updateMatrixWorld(true)
}
