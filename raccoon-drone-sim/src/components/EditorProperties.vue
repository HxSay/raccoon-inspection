<script setup lang="ts">
import { reactive, watch, computed, unref } from 'vue'
import type { ShallowRef } from 'vue'
import type { SceneEditor3D } from '@/editor/SceneEditor3D'
import type { EditorUiState, SelectionProps } from '@/editor/types'

const props = defineProps<{
  editor: ShallowRef<SceneEditor3D | null>
  ui: ShallowRef<EditorUiState | null>
}>()

/** 父模板会自动解包 ref；unref 兼容 ref 与实例两种情况 */
const ed = () => unref(props.editor) ?? null
const uiState = computed(() => unref(props.ui) ?? null)

const form = reactive<Partial<SelectionProps>>({})

watch(
  () => uiState.value?.props,
  (p) => {
    if (p) Object.assign(form, p)
  },
  { immediate: true, deep: true }
)

function apply() {
  ed()?.applyProps(form)
}

function del() {
  ed()?.deleteSelected()
}
</script>

<template>
  <div class="editor-props flex min-h-0 w-full flex-1 flex-col overflow-y-auto bg-transparent p-3 text-[11px]">
    <div class="ia-props-head">
      <span class="ia-props-head__bar" />
      <span class="ia-props-head__title">属性</span>
    </div>
    <template v-if="uiState?.props">
      <div class="mb-2 text-[9px] text-[var(--ia-muted)]">{{ form.label }} · {{ (form.ids?.length ?? 0) }} 项</div>
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
    <div v-else class="ia-props-empty">
      <span class="ia-props-empty__icon">＋</span>
      选中物体后编辑属性<br />Ctrl 多选可批量改材质与数值
    </div>
  </div>
</template>

<style scoped>
.ia-props-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.ia-props-head__bar {
  width: 3px;
  height: 13px;
  border-radius: 3px;
  background: linear-gradient(180deg, var(--ia-accent), rgba(56, 167, 214, 0.25));
}
.ia-props-head__title {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #e7f1f8;
}
.ia-props-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 22px 12px;
  text-align: center;
  font-family: ui-monospace, monospace;
  font-size: 10px;
  line-height: 1.6;
  color: var(--ia-muted);
  border: 1px dashed var(--ia-border-soft);
  border-radius: var(--ia-radius-sm);
  background: rgba(255, 255, 255, 0.012);
}
.ia-props-empty__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  font-size: 16px;
  color: var(--ia-accent);
  border: 1px solid var(--ia-border);
  border-radius: 50%;
  opacity: 0.7;
}
</style>
