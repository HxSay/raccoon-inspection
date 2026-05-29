<script setup lang="ts">
import { computed, nextTick, unref } from 'vue'
import type { ShallowRef } from 'vue'
import type { SceneEditor3D } from '@/editor/SceneEditor3D'
import type { EditorUiState, EditorPrimitiveKind, TransformToolMode } from '@/editor/types'

const props = defineProps<{
  editor: ShallowRef<SceneEditor3D | null>
  ui: ShallowRef<EditorUiState | null>
}>()

/** 父模板会自动解包 ref，故收到的可能是实例本身；unref 同时兼容两种情况 */
const uiState = computed(() => unref(props.ui) ?? null)

const ed = () => unref(props.editor) ?? null

const dims = {
  wx: 4,
  wy: 3,
  wz: 4,
  radius: 1.5,
  height: 5,
  planeW: 40,
  planeD: 40
}

const transformLabel = computed(() => {
  const m = uiState.value?.transformMode
  if (m === 'rotate') return '旋转'
  if (m === 'scale') return '缩放'
  return '移动'
})

function tool(m: TransformToolMode) {
  ed()?.setTransformMode(m)
}

function space() {
  const e = ed()
  if (!e) return
  e.setSpace(uiState.value?.space === 'world' ? 'local' : 'world')
}

function grid() {
  ed()?.toggleGrid()
}

function quickAdd(kind: EditorPrimitiveKind) {
  if (kind === 'imported') return
  void nextTick(() => {
    ed()?.createPrimitiveAtViewCenter(kind, { ...dims })
  })
}

function place(kind: EditorPrimitiveKind) {
  if (kind === 'imported') return
  void nextTick(() => {
    ed()?.beginPlacement(kind)
  })
}

function cancelPlace() {
  void nextTick(() => {
    ed()?.cancelPlacement()
  })
}

function undo() {
  ed()?.undo()
}

function redo() {
  ed()?.redo()
}

function onTransformCmd(cmd: string) {
  if (cmd === 'translate' || cmd === 'rotate' || cmd === 'scale') tool(cmd)
}

function onGeometryCmd(cmd: string | number) {
  const c = String(cmd)
  const map: Record<string, EditorPrimitiveKind> = {
    box: 'box',
    sphere: 'sphere',
    cylinder: 'cylinder',
    cone: 'cone',
    plane: 'plane',
    pipe: 'pipe'
  }
  const k = map[c]
  if (k) quickAdd(k)
}

function onPlaceCmd(cmd: string | number) {
  const c = String(cmd)
  if (c === 'box') place('box')
  else if (c === 'sphere') place('sphere')
  else if (c === 'cancel') cancelPlace()
}

function onMoreCmd(cmd: string) {
  if (cmd === 'space') space()
  else if (cmd === 'grid') grid()
  else if (cmd === 'undo') undo()
  else if (cmd === 'redo') redo()
}
</script>

<template>
  <div
    class="edit-toolbar-tools ia-edit-tools flex flex-wrap items-center justify-center gap-1 font-mono text-[10px] text-[var(--ia-muted)]"
  >
    <span class="ia-edit-tag">编辑</span>
    <el-dropdown trigger="click" @command="onTransformCmd">
      <el-button size="small" text class="ia-edit-btn" :class="{ 'is-on': true }">
        变换 · {{ transformLabel }} <span class="ml-0.5 opacity-50">▾</span>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="translate">移动</el-dropdown-item>
          <el-dropdown-item command="rotate">旋转</el-dropdown-item>
          <el-dropdown-item command="scale">缩放</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-dropdown trigger="click" @command="onGeometryCmd">
      <el-button size="small" text class="ia-edit-btn">
        几何 <span class="ml-0.5 opacity-50">▾</span>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="box">立方体</el-dropdown-item>
          <el-dropdown-item command="sphere">球体</el-dropdown-item>
          <el-dropdown-item command="cylinder">圆柱</el-dropdown-item>
          <el-dropdown-item command="cone">圆锥</el-dropdown-item>
          <el-dropdown-item command="plane">平面</el-dropdown-item>
          <el-dropdown-item command="pipe">管道</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-dropdown trigger="click" @command="onPlaceCmd">
      <el-button size="small" text class="ia-edit-btn" :class="{ 'is-on': !!uiState?.placementKind }">
        点击放置 <span class="ml-0.5 opacity-50">▾</span>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="box">放立方体（再左键点地）</el-dropdown-item>
          <el-dropdown-item command="sphere">放球体（再左键点地）</el-dropdown-item>
          <el-dropdown-item v-if="uiState?.placementKind" command="cancel" divided>取消放置模式</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-dropdown trigger="click" @command="onMoreCmd">
      <el-button size="small" text class="ia-edit-btn">
        更多 <span class="ml-0.5 opacity-50">▾</span>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="space">{{ uiState?.space === 'world' ? '切换为局部轴' : '切换为世界轴' }}</el-dropdown-item>
          <el-dropdown-item command="grid">{{ uiState?.gridVisible ? '隐藏网格' : '显示网格' }}</el-dropdown-item>
          <el-dropdown-item command="undo" divided :disabled="!ed()?.canUndo()">撤销</el-dropdown-item>
          <el-dropdown-item command="redo" :disabled="!ed()?.canRedo()">重做</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped>
.ia-edit-tools {
  padding: 2px 4px;
  border: 1px solid var(--ia-border-soft);
  border-radius: var(--ia-radius-sm);
  background: rgba(255, 255, 255, 0.015);
}
.ia-edit-tag {
  padding: 0 6px 0 4px;
  font-family: ui-monospace, monospace;
  font-size: 9px;
  letter-spacing: 0.12em;
  color: var(--ia-accent);
  opacity: 0.85;
}
:deep(.ia-edit-btn) {
  height: 26px;
  padding: 0 9px;
  font-family: ui-monospace, monospace;
  font-size: 11px;
  color: var(--ia-text);
  border-radius: 4px;
}
:deep(.ia-edit-btn:hover) {
  background: var(--ia-accent-soft);
  color: #eaf4fb;
}
:deep(.ia-edit-btn.is-on) {
  background: rgba(56, 167, 214, 0.12);
  color: #bfe3f4;
}
</style>
