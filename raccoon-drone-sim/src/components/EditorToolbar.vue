<script setup lang="ts">
import { computed } from 'vue'
import type { ShallowRef } from 'vue'
import type { SceneEditor3D } from '@/editor/SceneEditor3D'
import type { EditorUiState, EditorPrimitiveKind, TransformToolMode } from '@/editor/types'

const props = defineProps<{
  editor: ShallowRef<SceneEditor3D | null>
  ui: ShallowRef<EditorUiState | null>
}>()

const uiState = computed(() => props.ui.value)

const ed = () => props.editor.value

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

function place(kind: EditorPrimitiveKind) {
  if (kind === 'imported') return
  ed()?.beginPlacement(kind)
}

function cancelPlace() {
  ed()?.cancelPlacement()
}

function quickAdd(kind: EditorPrimitiveKind) {
  if (kind === 'imported') return
  ed()?.createPrimitiveAtViewCenter(kind, { ...dims })
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

function onGeometryCmd(cmd: string) {
  const map: Record<string, EditorPrimitiveKind> = {
    box: 'box',
    sphere: 'sphere',
    cylinder: 'cylinder',
    cone: 'cone',
    plane: 'plane',
    pipe: 'pipe'
  }
  const k = map[cmd]
  if (k) quickAdd(k)
}

function onPlaceCmd(cmd: string) {
  if (cmd === 'box') place('box')
  else if (cmd === 'sphere') place('sphere')
  else if (cmd === 'cancel') cancelPlace()
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
    class="flex flex-wrap items-center gap-x-1 gap-y-1 border-b border-[var(--ia-border)] bg-[#0a1018]/95 px-2 py-1 font-mono text-[10px] text-[var(--ia-muted)]"
  >
    <el-dropdown trigger="click" @command="onTransformCmd">
      <el-button size="small" type="default" class="!font-mono">
        变换 · {{ transformLabel }} <span class="ml-0.5 opacity-60">▾</span>
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
      <el-button size="small" type="default" class="!font-mono">
        几何（视野中心） <span class="ml-0.5 opacity-60">▾</span>
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
      <el-button size="small" type="default" class="!font-mono">
        点击放置 <span class="ml-0.5 opacity-60">▾</span>
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
      <el-button size="small" type="default" class="!font-mono">
        更多 <span class="ml-0.5 opacity-60">▾</span>
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

    <span
      class="ml-auto max-w-[min(100%,28rem)] truncate text-[9px] opacity-75"
      title="Ctrl+Z / Ctrl+Y · Q 切换世界/局部 · WASD 平移视角 · Del 删除 · Ctrl 多选"
    >
      Ctrl+Z/Y · Q · WASD · Del · Ctrl 多选
    </span>
  </div>
</template>
