<script setup lang="ts">
import { reactive, watch, computed } from 'vue'
import type { ShallowRef } from 'vue'
import type { SceneEditor3D } from '@/editor/SceneEditor3D'
import type { EditorUiState, SelectionProps } from '@/editor/types'

const props = defineProps<{
  editor: ShallowRef<SceneEditor3D | null>
  ui: ShallowRef<EditorUiState | null>
}>()

const uiState = computed(() => props.ui?.value ?? null)

const form = reactive<Partial<SelectionProps>>({})

watch(
  () => uiState.value?.props,
  (p) => {
    if (p) Object.assign(form, p)
  },
  { immediate: true, deep: true }
)

function apply() {
  props.editor?.value?.applyProps(form)
}

function del() {
  props.editor?.value?.deleteSelected()
}
</script>

<template>
  <div class="editor-props flex h-full min-h-0 w-72 shrink-0 flex-col border-l border-[var(--ia-border)] bg-[var(--ia-panel)] p-2 text-[11px]">
    <div class="mb-2 font-mono text-[10px] uppercase tracking-wide text-[var(--ia-accent)]">属性</div>
    <template v-if="uiState?.props">
      <div class="mb-1 text-[9px] text-[var(--ia-muted)]">{{ form.label }} · {{ (form.ids?.length ?? 0) }} 项</div>
      <div class="grid grid-cols-3 gap-1">
        <div><span class="text-[9px] text-[var(--ia-muted)]">X</span><el-input-number v-model="form.x!" size="small" class="!w-full" controls-position="right" @change="apply" /></div>
        <div><span class="text-[9px] text-[var(--ia-muted)]">Y</span><el-input-number v-model="form.y!" size="small" class="!w-full" controls-position="right" @change="apply" /></div>
        <div><span class="text-[9px] text-[var(--ia-muted)]">Z</span><el-input-number v-model="form.z!" size="small" class="!w-full" controls-position="right" @change="apply" /></div>
      </div>
      <div class="mt-1 grid grid-cols-3 gap-1">
        <div><span class="text-[9px] text-[var(--ia-muted)]">RX°</span><el-input-number v-model="form.rx!" size="small" class="!w-full" controls-position="right" @change="apply" /></div>
        <div><span class="text-[9px] text-[var(--ia-muted)]">RY°</span><el-input-number v-model="form.ry!" size="small" class="!w-full" controls-position="right" @change="apply" /></div>
        <div><span class="text-[9px] text-[var(--ia-muted)]">RZ°</span><el-input-number v-model="form.rz!" size="small" class="!w-full" controls-position="right" @change="apply" /></div>
      </div>
      <div class="mt-1 grid grid-cols-3 gap-1">
        <div><span class="text-[9px] text-[var(--ia-muted)]">Sx</span><el-input-number v-model="form.sx!" :min="0.01" :step="0.05" size="small" class="!w-full" controls-position="right" @change="apply" /></div>
        <div><span class="text-[9px] text-[var(--ia-muted)]">Sy</span><el-input-number v-model="form.sy!" :min="0.01" :step="0.05" size="small" class="!w-full" controls-position="right" @change="apply" /></div>
        <div><span class="text-[9px] text-[var(--ia-muted)]">Sz</span><el-input-number v-model="form.sz!" :min="0.01" :step="0.05" size="small" class="!w-full" controls-position="right" @change="apply" /></div>
      </div>
      <div class="mt-2 space-y-2">
        <div>
          <div class="mb-0.5 text-[9px] text-[var(--ia-muted)]">颜色</div>
          <el-color-picker v-model="form.color!" show-alpha @change="apply" />
        </div>
        <div>
          <div class="mb-0.5 text-[9px] text-[var(--ia-muted)]">金属度</div>
          <el-slider v-model="form.metalness!" :min="0" :max="1" :step="0.02" size="small" @change="apply" />
        </div>
        <div>
          <div class="mb-0.5 text-[9px] text-[var(--ia-muted)]">粗糙度</div>
          <el-slider v-model="form.roughness!" :min="0" :max="1" :step="0.02" size="small" @change="apply" />
        </div>
        <div>
          <div class="mb-0.5 text-[9px] text-[var(--ia-muted)]">透明度</div>
          <el-slider v-model="form.opacity!" :min="0" :max="1" :step="0.02" size="small" @change="apply" />
        </div>
        <el-switch v-model="form.transparent!" active-text="透明" inactive-text="不透明" @change="apply" />
      </div>
      <el-button class="mt-3 !w-full" type="danger" size="small" plain @click="del">删除选中</el-button>
    </template>
    <div v-else class="text-[10px] text-[var(--ia-muted)]">选中物体后编辑属性；Ctrl 多选可批量改材质与数值。</div>
  </div>
</template>
