<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { taskGet, taskCheckIn, taskStart } from '@/api/cmms'
import { getCurrentPosition } from '@/utils/location'
import { scanDeviceCode } from '@/utils/scanner'

const route = useRoute()
const router = useRouter()
const task = ref<Record<string, unknown> | null>(null)
const loading = ref(true)

async function load() {
  const id = Number(route.params.id)
  const res: any = await taskGet(id)
  task.value = res.data as Record<string, unknown> | null
  loading.value = false
}

onMounted(load)

async function doCheckIn() {
  const id = Number(route.params.id)
  try {
    const geo = await getCurrentPosition()
    const code = await scanDeviceCode()
    await taskCheckIn({
      taskId: id,
      longitude: geo.longitude,
      latitude: geo.latitude,
      scannedDeviceCode: code || undefined
    })
    showToast('签到成功')
    load()
  } catch (e: unknown) {
    showToast((e as Error).message || '签到失败')
  }
}

async function doStart() {
  const id = Number(route.params.id)
  try {
    await taskStart(id)
    showToast('已开始执行')
    load()
  } catch {
    /* request 已 toast */
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="任务详情" left-arrow fixed placeholder @click-left="router.back()" />
    <van-skeleton title :row="5" :loading="loading">
      <template v-if="task">
        <van-cell-group inset>
          <van-cell title="任务名称" :value="String(task.taskName)" />
          <van-cell title="任务单号" :value="String(task.taskCode)" />
          <van-cell title="设备ID" :value="String(task.deviceId)" />
          <van-cell title="计划执行时间" :value="String(task.planExecuteTime)" />
          <van-cell title="状态" :value="['待执行', '执行中', '已完成', '已过期'][Number(task.status)] || '-'" />
        </van-cell-group>
        <div class="actions">
          <van-button v-if="task.status === 0" type="primary" block round @click="doCheckIn">GPS 签到 + 扫码确认</van-button>
          <van-button v-if="task.status === 0" type="success" block round style="margin-top: 10px" @click="doStart">开始执行</van-button>
          <van-button
            v-if="task.status === 1"
            type="primary"
            block
            round
            @click="router.push(`/task/${task.id}/exec`)"
          >
            进入巡检执行
          </van-button>
          <van-button type="default" block round style="margin-top: 10px" @click="router.push(`/task/${task.id}/report`)">
            异常上报
          </van-button>
        </div>
      </template>
      <van-empty v-else description="任务不存在" />
    </van-skeleton>
  </div>
</template>

<style scoped>
.actions {
  padding: 16px;
}
</style>
