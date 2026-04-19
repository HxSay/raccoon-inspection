<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { getDictDataByTypeCode, DictDataResponse } from '@/api/dictData'

interface Props {
  modelValue: string | number | undefined
  dictTypeCode: string
  placeholder?: string
  disabled?: boolean
  clearable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择',
  disabled: false,
  clearable: true
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number | undefined]
}>()

const options = ref<DictDataResponse[]>([])
const loading = ref(false)

const fetchDictData = async () => {
  if (!props.dictTypeCode) return

  loading.value = true
  try {
    const res = await getDictDataByTypeCode(props.dictTypeCode)
    options.value = res.data
  } catch (error) {
    console.error('获取字典数据失败', error)
  } finally {
    loading.value = false
  }
}

const handleChange = (val: string | number | undefined) => {
  emit('update:modelValue', val)
}

watch(() => props.dictTypeCode, () => {
  fetchDictData()
}, { immediate: true })

onMounted(() => {
  fetchDictData()
})
</script>

<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :loading="loading"
    filterable
    style="width: 100%"
    @update:model-value="handleChange"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="item.dictLabel"
      :value="item.dictValue"
    />
  </el-select>
</template>
