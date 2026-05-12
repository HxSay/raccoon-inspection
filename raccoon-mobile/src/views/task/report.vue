<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { taskGet, taskReportAbnormal } from '@/api/cmms'
import { pickCameraImage, filesToJsonUrlArray } from '@/utils/camera'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)
const task = ref<Record<string, unknown> | null>(null)
const desc = ref('')
const files = ref<File[]>([])
const loading = ref(true)
const submitting = ref(false)

onMounted(async () => {
  const tr: any = await taskGet(taskId)
  task.value = tr.data
  loading.value = false
})

async function addPhoto() {
  const f = await pickCameraImage()
  if (f) files.value.push(f)
}

function rm(i: number) {
  files.value.splice(i, 1)
}

async function submit() {
  if (!desc.value.trim()) {
    showToast('请填写异常描述')
    return
  }
  submitting.value = true
  try {
    let faultImageUrls: string | undefined
    if (files.value.length) {
      faultImageUrls = await filesToJsonUrlArray(files.value)
    }
    await taskReportAbnormal({
      taskId,
      faultDescription: desc.value.trim(),
      faultImageUrls
    })
    showToast('已上报并生成工单')
    router.replace('/wo')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="异常上报" left-arrow fixed placeholder @click-left="router.back()" />
    <van-skeleton title :row="3" :loading="loading">
      <van-cell-group v-if="task" inset title="关联任务">
        <van-cell title="任务" :value="String(task.taskName)" />
        <van-cell title="设备 ID" :value="String(task.deviceId)" />
      </van-cell-group>
      <van-cell-group inset title="异常信息" style="margin-top: 12px">
        <van-field
          v-model="desc"
          rows="4"
          autosize
          type="textarea"
          maxlength="500"
          show-word-limit
          label="描述"
          placeholder="故障现象、位置、紧急程度等（对应 work_order.fault_description）"
        />
        <van-cell title="照片">
          <template #value>
            <van-button size="small" type="danger" plain @click="addPhoto">添加照片</van-button>
          </template>
        </van-cell>
        <div v-if="files.length" class="files">
          <div v-for="(f, i) in files" :key="i" class="row">
            <span>{{ f.name }}</span>
            <van-button size="mini" type="danger" plain @click="rm(i)">删除</van-button>
          </div>
        </div>
      </van-cell-group>
      <div class="foot">
        <van-button type="danger" block round :loading="submitting" @click="submit">提交异常并生成工单</van-button>
      </div>
    </van-skeleton>
  </div>
</template>

<style scoped>
.files {
  padding: 8px 16px 12px;
  font-size: 13px;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.foot {
  padding: 20px 16px;
}
</style>
