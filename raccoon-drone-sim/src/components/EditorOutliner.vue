<script setup lang="ts">
import { computed, ref, unref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ShallowRef } from 'vue'
import type { SceneEditor3D } from '@/editor/SceneEditor3D'
import type { EditorUiState, EditorPrimitiveKind } from '@/editor/types'

const props = withDefaults(
  defineProps<{
    editor: ShallowRef<SceneEditor3D | null>
    ui: ShallowRef<EditorUiState | null>
    /** panel=侧栏；drawer=顶部抽屉内横向布局 */
    layout?: 'panel' | 'drawer'
  }>(),
  { layout: 'panel' }
)

const renameId = ref<string | null>(null)
const renameText = ref('')

/** 父模板会自动解包 ref；unref 兼容 ref 与实例两种情况 */
const ed = () => unref(props.editor) ?? null
const st = computed(() => unref(props.ui) ?? null)

function selectNode(id: string, ev: MouseEvent) {
  ed()?.selectById(id, ev.ctrlKey || ev.metaKey)
}

function startRename(n: { id: string; label: string }) {
  renameId.value = n.id
  renameText.value = n.label
}

function commitRename() {
  if (!renameId.value) return
  ed()?.renameObject(renameId.value, renameText.value)
  renameId.value = null
}

function onVisChange(id: string, v: boolean | string | number) {
  ed()?.setObjectVisible(id, !!v)
}

function group() {
  ed()?.groupSelection()
}

async function clearScene() {
  try {
    await ElMessageBox.confirm('清空编辑器创建的所有物体与导入地图？', '清空场景', { type: 'warning' })
    ed()?.clearUserObjects()
    ElMessage.success('已清空')
  } catch {
    /* cancel */
  }
}

function exportJson() {
  const json = ed()?.exportSceneJson()
  if (!json) return
  const blob = new Blob([json], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `scene-editor-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 JSON')
}

const sceneFileRef = ref<HTMLInputElement | null>(null)
function pickImportScene() {
  sceneFileRef.value?.click()
}
function onSceneFile(ev: Event) {
  const f = (ev.target as HTMLInputElement).files?.[0]
  if (!f) return
  const reader = new FileReader()
  reader.onload = () => {
    try {
      ed()?.importSceneJson(reader.result as string)
      ElMessage.success('场景已导入')
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }
  reader.readAsText(f)
  ;(ev.target as HTMLInputElement).value = ''
}

const mapFileRef = ref<HTMLInputElement | null>(null)
function pickImportMap() {
  mapFileRef.value?.click()
}
function onMapFile(ev: Event) {
  const f = (ev.target as HTMLInputElement).files?.[0]
  if (!f) return
  ed()
    ?.importGlbFile(f)
    .then(() => ElMessage.success('地图模型已导入'))
    .catch(() => ElMessage.error('导入失败'))
  ;(ev.target as HTMLInputElement).value = ''
}
</script>

<template>
  <div
    class="editor-outliner text-[11px]"
    :class="
      layout === 'drawer'
        ? 'flex flex-col gap-3'
        : 'flex min-h-0 w-full flex-1 flex-col overflow-hidden bg-[var(--ia-panel)]'
    "
  >
    <div
      v-if="layout === 'panel'"
      class="border-b border-[var(--ia-border)] px-2 py-1.5 font-mono text-[10px] uppercase tracking-wide text-[var(--ia-accent)]"
    >
      场景对象
    </div>
    <div
      class="overflow-y-auto px-1 py-1"
      :class="layout === 'drawer' ? 'max-h-40 min-h-[5rem] rounded border border-[var(--ia-border)] bg-[#0a1018]' : 'flex-1'"
    >
      <div
        v-for="n in st?.tree ?? []"
        :key="n.id"
        class="mb-0.5 flex items-center gap-1 rounded px-1 py-0.5 hover:bg-[#0c141c]"
      >
        <el-checkbox
          :model-value="n.visible"
          size="small"
          class="!mr-0 scale-90"
          @update:model-value="(v) => onVisChange(n.id, v)"
        />
        <button
          type="button"
          class="min-w-0 flex-1 truncate text-left font-mono text-[10px] text-[#c8d4e0]"
          :class="{ 'ring-1 ring-[var(--ia-accent)]': st?.selectionIds?.includes(n.id) }"
          @click="selectNode(n.id, $event)"
          @dblclick.prevent="startRename(n)"
        >
          {{ n.label }}
        </button>
      </div>
      <div v-if="!st?.tree?.length" class="px-2 py-4 text-center text-[10px] text-[var(--ia-muted)]">暂无物体</div>
    </div>
    <div v-if="renameId">
      <div class="mb-1 text-[9px] text-[var(--ia-muted)]">重命名</div>
      <el-input v-model="renameText" size="small" class="font-mono" @keyup.enter="commitRename" @blur="commitRename" />
    </div>
    <div
      class="outliner-actions"
      :class="
        layout === 'drawer'
          ? 'grid grid-cols-2 gap-2 sm:grid-cols-5'
          : 'flex flex-col gap-1 border-t border-[var(--ia-border)] p-2'
      "
    >
      <el-button
        size="small"
        class="!m-0 !font-mono"
        :class="layout === 'drawer' ? '!w-full' : ''"
        :disabled="(st?.selectionIds?.length ?? 0) < 2"
        @click="group"
      >
        分组（预留）
      </el-button>
      <el-button size="small" class="!m-0 !font-mono" :class="layout === 'drawer' ? '!w-full' : ''" @click="exportJson">
        导出 JSON
      </el-button>
      <el-button size="small" class="!m-0 !font-mono" :class="layout === 'drawer' ? '!w-full' : ''" @click="pickImportScene">
        导入 JSON
      </el-button>
      <input ref="sceneFileRef" type="file" accept=".json,application/json" class="hidden" @change="onSceneFile" />
      <el-button size="small" class="!m-0 !font-mono" :class="layout === 'drawer' ? '!w-full' : ''" @click="pickImportMap">
        导入地图 GLB
      </el-button>
      <input ref="mapFileRef" type="file" accept=".glb,.gltf" class="hidden" @change="onMapFile" />
      <el-button
        size="small"
        type="danger"
        plain
        class="!m-0 !font-mono"
        :class="layout === 'drawer' ? '!w-full' : ''"
        @click="clearScene"
      >
        清空场景
      </el-button>
    </div>
  </div>
</template>
