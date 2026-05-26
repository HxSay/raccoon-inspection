<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ragPreviewUrl } from '@/api/rag'

const props = defineProps<{
  modelValue: boolean
  docId?: string
  fileName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const url = ref('')
const loading = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

watch(
  () => [props.modelValue, props.docId],
  async ([open, docId]) => {
    url.value = ''
    if (!open || !docId) return
    loading.value = true
    try {
      const res: any = await ragPreviewUrl(docId as string)
      url.value = res.data?.url ?? ''
    } catch (e: any) {
      ElMessage.error('获取预览链接失败：' + (e?.message ?? '未知错误'))
    } finally {
      loading.value = false
    }
  },
  { immediate: false }
)

const openInNewTab = () => {
  if (url.value) window.open(url.value, '_blank', 'noopener')
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="`PDF 预览${fileName ? ' - ' + fileName : ''}`"
    width="80%"
    top="5vh"
    destroy-on-close
  >
    <div v-loading="loading" class="pdf-preview-wrap">
      <iframe
        v-if="url"
        :src="url"
        class="pdf-frame"
        title="pdf-preview"
      />
      <el-empty v-else-if="!loading" description="未能获取到预览链接" />
    </div>
    <template #footer>
      <el-button :disabled="!url" @click="openInNewTab">在新标签页打开</el-button>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.pdf-preview-wrap {
  height: 75vh;
  background: #2b2b2b;
  border-radius: 4px;
}
.pdf-frame {
  width: 100%;
  height: 100%;
  border: none;
  background: #2b2b2b;
}
</style>
