<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'

const props = defineProps<{
  maxSizeMB?: number
}>()

const emit = defineEmits<{
  (e: 'select', file: File): void
  (e: 'clear'): void
}>()

const innerFile = ref<File | null>(null)
const fileName = ref('')
const uploadRef = ref<UploadInstance | null>(null)

/**
 * el-upload 在 auto-upload=false 时仅会触发 on-change，
 * before-upload 永远不会被调用，因此校验必须放在 on-change。
 */
const handleChange = (uploadFile: UploadFile) => {
  const raw = uploadFile.raw as File | undefined
  if (!raw) return
  const limitMB = props.maxSizeMB ?? 50
  if (raw.size > limitMB * 1024 * 1024) {
    ElMessage.error(`文件大小不能超过 ${limitMB}MB`)
    uploadRef.value?.clearFiles()
    return
  }
  const lowName = raw.name.toLowerCase()
  const isPdf =
    raw.type === 'application/pdf' ||
    lowName.endsWith('.pdf')
  if (!isPdf) {
    ElMessage.error('仅支持 PDF 文件')
    uploadRef.value?.clearFiles()
    return
  }
  innerFile.value = raw
  fileName.value = raw.name
  emit('select', raw)
  // 立即清空 el-upload 内部 fileList，避免 limit=1 阻止下一次替换
  uploadRef.value?.clearFiles()
}

const handleExceed = (files: File[]) => {
  const raw = files?.[0]
  if (raw) {
    handleChange({ raw, name: raw.name, size: raw.size, status: 'ready' } as unknown as UploadFile)
  }
}

const clear = () => {
  innerFile.value = null
  fileName.value = ''
  uploadRef.value?.clearFiles()
  emit('clear')
}

defineExpose({ clear })
</script>

<template>
  <div class="pdf-uploader">
    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      :show-file-list="false"
      :multiple="false"
      :limit="1"
      :on-change="handleChange"
      :on-exceed="handleExceed"
      accept=".pdf,application/pdf"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        点击或拖拽 PDF 到此处 <em>（最大 {{ props.maxSizeMB ?? 50 }} MB）</em>
      </div>
    </el-upload>
    <div v-if="fileName" class="picked">
      <el-tag type="success" closable @close="clear">已选：{{ fileName }}</el-tag>
    </div>
  </div>
</template>

<style scoped>
.pdf-uploader {
  width: 100%;
}
.picked {
  margin-top: 8px;
}
</style>
