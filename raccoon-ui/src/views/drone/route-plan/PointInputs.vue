<script setup lang="ts">
export interface PointTriple {
  longitude?: number
  latitude?: number
  height?: number
}

const model = defineModel<PointTriple>({ required: true })
</script>

<template>
  <el-row :gutter="8">
    <el-col :span="8">
      <el-form-item label="经度" label-width="48px" class="compact-item">
        <el-input-number
          v-model="model.longitude"
          :precision="6"
          :step="0.0001"
          controls-position="right"
          class="w-full"
        />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="纬度" label-width="48px" class="compact-item">
        <el-input-number
          v-model="model.latitude"
          :precision="6"
          :step="0.0001"
          controls-position="right"
          class="w-full"
        />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="高度" label-width="48px" class="compact-item">
        <el-input-number v-model="model.height" :min="0" :step="1" controls-position="right" class="w-full" />
      </el-form-item>
    </el-col>
  </el-row>
</template>

<style scoped>
.compact-item {
  margin-bottom: 0;
}
.w-full {
  width: 100%;
}
</style>
