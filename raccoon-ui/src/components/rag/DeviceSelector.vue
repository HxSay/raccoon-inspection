<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { cmmsDevicePage, type DeviceInfo } from '@/api/cmms'
import type { RagDevice } from '@/api/rag'

const props = defineProps<{
  modelValue?: string
  placeholder?: string
  clearable?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | undefined): void
  (e: 'change', value: string | undefined, device: RagDevice | undefined): void
}>()

const devices = ref<RagDevice[]>([])
const loading = ref(false)
const inner = ref<string | undefined>(props.modelValue)

/** CMMS 设备台账 → RAG/Neo4j 设备结构（deviceId 使用 deviceCode） */
const toRagDevice = (d: DeviceInfo): RagDevice => ({
  deviceId: d.deviceCode,
  name: d.deviceName,
  type: d.model,
  station: d.location
})

const loadDevices = async () => {
  loading.value = true
  try {
    const res: any = await cmmsDevicePage({ page: 1, size: 500 })
    const records: DeviceInfo[] = res.data?.records ?? []
    devices.value = records
      .filter((d) => d.deviceCode)
      .map(toRagDevice)
  } catch (e: any) {
    ElMessage.error('加载设备管理列表失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (v) => {
    inner.value = v
  }
)

const handleChange = (val: string | undefined) => {
  inner.value = val
  const dev = devices.value.find((d) => d.deviceId === val)
  emit('update:modelValue', val)
  emit('change', val, dev)
}

defineExpose({ reload: loadDevices })

onMounted(loadDevices)
</script>

<template>
  <el-select
    :model-value="inner"
    :placeholder="placeholder || '请选择设备（来自设备管理）'"
    :clearable="clearable !== false"
    :loading="loading"
    filterable
    style="width: 100%"
    @update:model-value="handleChange"
  >
    <el-option
      v-for="d in devices"
      :key="d.deviceId"
      :label="`${d.name || d.deviceId}${d.station ? ' / ' + d.station : ''}`"
      :value="d.deviceId"
    />
  </el-select>
</template>
