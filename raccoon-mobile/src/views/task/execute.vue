<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { taskGet, pointList, taskComplete } from '@/api/cmms'
import { pickCameraImage, filesToJsonUrlArray } from '@/utils/camera'

/** 巡检点（与表 inspection_point 字段一致） */
interface PointRow {
  id: number
  deviceId: number
  pointName: string
  pointType: number
  unit?: string
  minThreshold?: string | number
  maxThreshold?: string | number
  standardValue?: string | number
  sort?: number
}

const route = useRoute()
const router = useRouter()
const taskId = computed(() => Number(route.params.id))
const task = ref<Record<string, unknown> | null>(null)
const points = ref<PointRow[]>([])
const loading = ref(true)
const submitting = ref(false)
/** 当前步骤索引，对应 van-steps */
const step = ref(0)

/** 每巡检点：录入值、是否正常、现场照片 */
const lineState = ref<Record<number, { checkValue: string; isNormal: number; files: File[] }>>({})

function ensureLine(pid: number) {
  if (!lineState.value[pid]) {
    lineState.value[pid] = { checkValue: '', isNormal: 1, files: [] }
  }
  return lineState.value[pid]
}

const currentPoint = computed(() => points.value[step.value])

function thresholdHint(p: PointRow, val: string): string | null {
  if (!val.trim()) return null
  const n = Number(val)
  if (Number.isNaN(n)) return null
  const min = p.minThreshold != null && p.minThreshold !== '' ? Number(p.minThreshold) : null
  const max = p.maxThreshold != null && p.maxThreshold !== '' ? Number(p.maxThreshold) : null
  if (min != null && n < min) return `低于下限 ${min}`
  if (max != null && n > max) return `超过上限 ${max}`
  return null
}

watch(
  currentPoint,
  (p) => {
    if (p) ensureLine(p.id)
  },
  { immediate: true }
)

async function load() {
  loading.value = true
  try {
    const tr: any = await taskGet(taskId.value)
    task.value = tr.data as Record<string, unknown>
    const deviceId = Number(task.value?.deviceId)
    const pr: any = await pointList(deviceId)
    const list = (pr.data || []) as PointRow[]
    points.value = list.sort((a, b) => (Number(a.sort) || 0) - (Number(b.sort) || 0))
    for (const p of points.value) ensureLine(p.id)
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function addPhoto() {
  const p = currentPoint.value
  if (!p) return
  const f = await pickCameraImage()
  if (f) ensureLine(p.id).files.push(f)
}

function removePhoto(idx: number) {
  const p = currentPoint.value
  if (!p) return
  ensureLine(p.id).files.splice(idx, 1)
}

async function submitAll() {
  for (const p of points.value) {
    const s = ensureLine(p.id)
    if (!String(s.checkValue).trim()) {
      showToast(`请填写「${p.pointName}」读数`)
      return
    }
  }
  const warn = points.value.some((p) => thresholdHint(p, ensureLine(p.id).checkValue))
  if (warn) {
    try {
      await showConfirmDialog({ title: '阈值提示', message: '部分读数超出阈值范围，是否仍按当前「正常/异常」提交？' })
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    const records = []
    for (const p of points.value) {
      const s = ensureLine(p.id)
      let imageUrls: string | undefined
      if (s.files.length) {
        imageUrls = await filesToJsonUrlArray(s.files)
      }
      records.push({
        pointId: p.id,
        checkValue: s.checkValue,
        isNormal: s.isNormal,
        imageUrls
      })
    }
    await taskComplete({ taskId: taskId.value, records })
    showToast('提交成功')
    router.replace(`/task/${taskId.value}`)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <van-nav-bar title="巡检执行" left-arrow fixed placeholder @click-left="router.back()" />
    <van-skeleton title :row="4" :loading="loading">
      <template v-if="task && points.length">
        <van-notice-bar v-if="task.status !== 1" color="#1989fa" background="#ecf9ff" left-icon="info-o">
          当前任务非「执行中」，仍可录入提交；建议先在任务详情中点击「开始执行」。
        </van-notice-bar>
        <div class="head">
          <div class="tname">{{ task.taskName }}</div>
          <div class="sub">设备 ID {{ task.deviceId }} · 共 {{ points.length }} 个巡检点</div>
        </div>
        <van-steps :active="step" direction="vertical" active-color="#1989fa">
          <van-step v-for="p in points" :key="p.id" :title="p.pointName" />
        </van-steps>
        <div v-if="currentPoint" class="card">
          <van-cell-group inset :title="currentPoint.pointName">
            <van-cell
              title="标准值"
              :value="currentPoint.standardValue != null ? String(currentPoint.standardValue) : '-'"
            />
            <van-cell title="单位" :value="currentPoint.unit || '-'" />
            <van-cell
              title="阈值"
              :value="
                [currentPoint.minThreshold, currentPoint.maxThreshold].every((x) => x == null || x === '')
                  ? '-'
                  : `${currentPoint.minThreshold ?? '-'} ~ ${currentPoint.maxThreshold ?? '-'}`
              "
            />
            <van-field
              v-model="ensureLine(currentPoint.id).checkValue"
              label="本次读数"
              placeholder="请输入数值或描述"
              type="text"
            />
            <van-cell v-if="thresholdHint(currentPoint, ensureLine(currentPoint.id).checkValue)" title="阈值判断">
              <template #value>
                <span class="warn">{{ thresholdHint(currentPoint, ensureLine(currentPoint.id).checkValue) }}</span>
              </template>
            </van-cell>
            <van-cell title="是否正常">
              <template #value>
                <van-radio-group v-model="ensureLine(currentPoint.id).isNormal" direction="horizontal">
                  <van-radio :name="1">正常</van-radio>
                  <van-radio :name="0">异常</van-radio>
                </van-radio-group>
              </template>
            </van-cell>
            <van-cell title="现场照片">
              <template #value>
                <van-button size="small" type="primary" plain @click="addPhoto">拍照</van-button>
              </template>
            </van-cell>
            <div v-if="ensureLine(currentPoint.id).files.length" class="thumbs">
              <div v-for="(f, i) in ensureLine(currentPoint.id).files" :key="i" class="thumb">
                <span class="rm" @click="removePhoto(i)">×</span>
                <span class="nm">{{ f.name }}</span>
              </div>
            </div>
          </van-cell-group>
        </div>
        <div class="navbtn">
          <van-button :disabled="step <= 0" @click="step--">上一步</van-button>
          <van-button v-if="step < points.length - 1" type="primary" @click="step++">下一步</van-button>
          <van-button v-else type="success" :loading="submitting" @click="submitAll">提交全部</van-button>
        </div>
      </template>
      <van-empty v-else-if="!loading" description="无巡检点，请先在 PC 端维护 inspection_point" />
    </van-skeleton>
  </div>
</template>

<style scoped>
.page {
  padding-bottom: 24px;
}
.head {
  padding: 12px 16px 0;
}
.tname {
  font-size: 16px;
  font-weight: 600;
}
.sub {
  font-size: 12px;
  color: #888;
  margin-top: 4px;
}
.card {
  margin-top: 8px;
}
.warn {
  color: #ee0a24;
}
.navbtn {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding: 16px;
  flex-wrap: wrap;
}
.thumbs {
  padding: 0 16px 12px;
  font-size: 12px;
  color: #666;
}
.thumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.rm {
  color: #ee0a24;
  cursor: pointer;
  font-size: 16px;
}
</style>
