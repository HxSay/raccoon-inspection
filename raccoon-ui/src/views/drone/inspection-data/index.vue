<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  iotInspectionSessionPage,
  iotInspectionSamples,
  type UavInspectionSession,
  type UavInspectionSample
} from '@/api/iot'
import { droneUavOptions, type UavInfoOption } from '@/api/drone'

const MODALITY_LABEL: Record<string, string> = {
  VISIBLE: '可见光',
  THERMAL: '热成像',
  AUDIO: '声音',
  VIBRATION: '振动',
  TEMPERATURE: '温度'
}

const MODALITY_TAG: Record<string, string> = {
  VISIBLE: '',
  THERMAL: 'danger',
  AUDIO: 'warning',
  VIBRATION: 'success',
  TEMPERATURE: 'info'
}

const query = reactive({
  current: 1,
  size: 10,
  uavId: undefined as number | undefined,
  taskId: undefined as number | undefined,
  planId: undefined as number | undefined
})

const loading = ref(false)
const sessions = ref<UavInspectionSession[]>([])
const total = ref(0)
const uavOptions = ref<UavInfoOption[]>([])

const drawerVisible = ref(false)
const drawerLoading = ref(false)
const activeSession = ref<UavInspectionSession | null>(null)
const samples = ref<UavInspectionSample[]>([])

const loadSessions = async () => {
  loading.value = true
  try {
    const res: any = await iotInspectionSessionPage({
      current: query.current,
      size: query.size,
      uavId: query.uavId,
      taskId: query.taskId,
      planId: query.planId
    })
    sessions.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

const loadUavOptions = async () => {
  const res: any = await droneUavOptions()
  uavOptions.value = res.data ?? []
}

const onSearch = () => {
  query.current = 1
  loadSessions()
}

const onReset = () => {
  query.uavId = undefined
  query.taskId = undefined
  query.planId = undefined
  query.current = 1
  loadSessions()
}

const openSamples = async (row: UavInspectionSession) => {
  activeSession.value = row
  drawerVisible.value = true
  drawerLoading.value = true
  samples.value = []
  try {
    const res: any = await iotInspectionSamples(row.id)
    samples.value = res.data ?? []
  } catch {
    ElMessage.error('加载采样明细失败')
  } finally {
    drawerLoading.value = false
  }
}

const formatTime = (t?: string) => {
  if (!t) return '—'
  return t.replace('T', ' ').slice(0, 19)
}

const modalityLabel = (type: string) => MODALITY_LABEL[type] ?? type

const isImageModality = (type: string) => type === 'VISIBLE' || type === 'THERMAL'

const sampleImageSrc = (row: UavInspectionSample): string | undefined => {
  const thumb = row.payload?.thumbnail
  if (typeof thumb === 'string' && thumb.startsWith('data:image')) {
    return thumb
  }
  return undefined
}

const payloadForDisplay = (row: UavInspectionSample): Record<string, unknown> => {
  const p = { ...(row.payload ?? {}) }
  if (typeof p.thumbnail === 'string') {
    const len = p.thumbnail.length
    p.thumbnail = `[base64 图像, ${len} 字符]`
  }
  return p
}

const imageSamplesOfWaypoint = (rows: UavInspectionSample[]) =>
  rows.filter((r) => isImageModality(r.modalityType))

const payloadSummary = (row: UavInspectionSample): string => {
  const p = row.payload ?? {}
  switch (row.modalityType) {
    case 'VISIBLE':
    case 'THERMAL':
      return [
        p.label,
        p.confidence != null ? `置信度 ${p.confidence}%` : null,
        p.minC != null && p.maxC != null ? `${p.minC}~${p.maxC}°C` : null,
        sampleImageSrc(row) ? null : p.thumbnailChars != null ? '无图像数据' : null,
        p.note
      ]
        .filter(Boolean)
        .join(' · ') || '—'
    case 'AUDIO':
      return p.rmsDb != null ? `RMS ${p.rmsDb} ${p.unit ?? 'dB'}` : JSON.stringify(p)
    case 'VIBRATION': {
      const z = (p.axis as Record<string, { rms?: number }> | undefined)?.z
      return z?.rms != null ? `Z 轴 RMS ${z.rms} ${p.unit ?? 'mm/s'}` : JSON.stringify(p)
    }
    case 'TEMPERATURE':
      return p.maxC != null
        ? `最高 ${p.maxC}°C / 环境 ${p.ambientC ?? '—'}°C`
        : JSON.stringify(p)
    default:
      return Object.keys(p).length ? JSON.stringify(p) : '—'
  }
}

const groupedByWaypoint = computed(() => {
  const map = new Map<number, UavInspectionSample[]>()
  for (const s of samples.value) {
    const list = map.get(s.waypointIndex) ?? []
    list.push(s)
    map.set(s.waypointIndex, list)
  }
  return [...map.entries()].sort((a, b) => a[0] - b[0])
})

onMounted(() => {
  loadUavOptions()
  loadSessions()
})
</script>

<template>
  <div class="inspection-data-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>边缘巡检数据</span>
          <span class="hint">无人机终端任务结束后上报至 hxsay_agent_iot</span>
        </div>
      </template>

      <el-form :inline="true" class="query-form" @submit.prevent="onSearch">
        <el-form-item label="无人机">
          <el-select v-model="query.uavId" clearable placeholder="全部" style="width: 160px">
            <el-option
              v-for="u in uavOptions"
              :key="u.id"
              :label="`${u.uavName} (#${u.id})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务 ID">
          <el-input-number v-model="query.taskId" :min="1" controls-position="right" clearable />
        </el-form-item>
        <el-form-item label="规划 ID">
          <el-input-number v-model="query.planId" :min="1" controls-position="right" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="sessions" stripe border style="width: 100%">
        <el-table-column prop="id" label="会话 ID" width="90" />
        <el-table-column prop="uavId" label="无人机" width="90" />
        <el-table-column prop="taskId" label="任务" width="80">
          <template #default="{ row }">{{ row.taskId ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="planId" label="规划" width="80">
          <template #default="{ row }">{{ row.planId ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="开始时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column prop="distanceM" label="距离(m)" width="100">
          <template #default="{ row }">
            {{ row.distanceM != null ? row.distanceM.toFixed(1) : '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="sampleCount" label="采样条数" width="100" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openSamples(row)">查看采样</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadSessions"
          @current-change="loadSessions"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      :title="activeSession ? `会话 #${activeSession.id} 多模态采样` : '多模态采样'"
      size="68%"
      destroy-on-close
    >
      <div v-if="activeSession" class="session-meta">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="无人机 ID">{{ activeSession.uavId }}</el-descriptions-item>
          <el-descriptions-item label="采样条数">{{ activeSession.sampleCount }}</el-descriptions-item>
          <el-descriptions-item label="任务 ID">{{ activeSession.taskId ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="规划 ID">{{ activeSession.planId ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="开始">{{ formatTime(activeSession.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束">{{ formatTime(activeSession.finishedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div v-loading="drawerLoading" class="samples-body">
        <template v-if="!drawerLoading && samples.length === 0">
          <el-empty description="暂无采样数据" />
        </template>

        <div v-for="[wp, rows] in groupedByWaypoint" :key="wp" class="waypoint-block">
          <div class="wp-title">航点 {{ wp + 1 }}</div>

          <div v-if="imageSamplesOfWaypoint(rows).length" class="image-row">
            <div
              v-for="img in imageSamplesOfWaypoint(rows)"
              :key="img.id"
              class="image-card"
            >
              <div class="image-card-title">{{ modalityLabel(img.modalityType) }}</div>
              <el-image
                v-if="sampleImageSrc(img)"
                class="sample-thumb"
                :src="sampleImageSrc(img)"
                :preview-src-list="[sampleImageSrc(img)!]"
                fit="cover"
                preview-teleported
              />
              <div v-else class="no-image">
                <span>无图像</span>
                <small>历史数据未存缩略图，请重新执行巡检上报</small>
              </div>
              <div class="image-meta">{{ payloadSummary(img) }}</div>
            </div>
          </div>

          <el-table :data="rows" size="small" border stripe>
            <el-table-column label="预览" width="88" align="center">
              <template #default="{ row }">
                <el-image
                  v-if="sampleImageSrc(row)"
                  class="table-thumb"
                  :src="sampleImageSrc(row)"
                  :preview-src-list="[sampleImageSrc(row)!]"
                  fit="cover"
                  preview-teleported
                />
                <span v-else-if="isImageModality(row.modalityType)" class="no-thumb">—</span>
              </template>
            </el-table-column>
            <el-table-column label="模态" width="100">
              <template #default="{ row }">
                <el-tag :type="(MODALITY_TAG[row.modalityType] as any) || 'info'" size="small">
                  {{ modalityLabel(row.modalityType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="采样时间" width="170">
              <template #default="{ row }">{{ formatTime(row.capturedAt) }}</template>
            </el-table-column>
            <el-table-column label="位置" min-width="200">
              <template #default="{ row }">
                <span v-if="row.longitude != null">
                  {{ row.longitude }}, {{ row.latitude }} · {{ row.height }}m
                </span>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="载荷摘要" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ payloadSummary(row) }}</template>
            </el-table-column>
            <el-table-column label="详情" width="80">
              <template #default="{ row }">
                <el-popover trigger="click" width="360" placement="left">
                  <template #reference>
                    <el-button type="primary" link>JSON</el-button>
                  </template>
                  <pre class="json-pre">{{ JSON.stringify(payloadForDisplay(row), null, 2) }}</pre>
                </el-popover>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.inspection-data-page {
  min-height: 100%;
}

.card-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.card-header .hint {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}

.query-form {
  margin-bottom: 12px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.session-meta {
  margin-bottom: 16px;
}

.samples-body {
  min-height: 200px;
}

.waypoint-block {
  margin-bottom: 20px;
}

.wp-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.json-pre {
  margin: 0;
  font-size: 12px;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.image-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
}

.image-card {
  width: 220px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
}

.image-card-title {
  padding: 6px 10px;
  font-size: 13px;
  font-weight: 600;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
}

.sample-thumb {
  width: 100%;
  height: 140px;
  display: block;
  cursor: zoom-in;
}

.image-meta {
  padding: 6px 10px;
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
}

.no-image {
  height: 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #909399;
  font-size: 13px;
  padding: 8px;
  text-align: center;
}

.no-image small {
  font-size: 11px;
  line-height: 1.3;
}

.table-thumb {
  width: 64px;
  height: 48px;
  border-radius: 4px;
  cursor: zoom-in;
}

.no-thumb {
  color: #c0c4cc;
}
</style>
