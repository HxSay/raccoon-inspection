<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PointInputs, { type PointTriple } from './PointInputs.vue'
import {
  droneRoutePlanCreate,
  droneRoutePlanPage,
  droneRoutePlanGetById,
  droneMapOptions,
  droneUavOptions,
  type GeoPoint,
  type UavRoutePlan,
  type RoutePlanCreateRequest,
  type RoutePlanView,
  type UavRouteDispatchPayload,
  type UavMapOption,
  type UavInfoOption
} from '@/api/drone'
import { cmmsTaskPage, cmmsDevicePage, type InspectionTask, type DeviceInfo } from '@/api/cmms'

const activeTab = ref('plan')

const algorithmOptions = [
  { label: 'A*', value: 'A*' },
  { label: 'RRT*', value: 'RRT*' }
]

const emptyPoint = (): PointTriple => ({
  longitude: undefined,
  latitude: undefined,
  height: 50
})

const formRef = ref<FormInstance>()
const submitting = ref(false)

const baseForm = reactive({
  taskId: undefined as number | undefined,
  mapId: undefined as number | undefined,
  uavId: undefined as number | undefined,
  algorithm: 'A*'
})

const start = ref<PointTriple>({ longitude: 116.397428, latitude: 39.90923, height: 50 })
const end = ref<PointTriple>({ longitude: 116.407428, latitude: 39.91923, height: 50 })
const waypoints = ref<PointTriple[]>([])
const photoPoints = ref<PointTriple[]>([])
const visitOrder = ref<number[]>([])
const visitDeviceInput = ref<number | undefined>()

const mapOptions = ref<UavMapOption[]>([])
const uavOptions = ref<UavInfoOption[]>([])
const uavOptionsAll = ref<UavInfoOption[]>([])
const taskOptions = ref<InspectionTask[]>([])
const deviceOptions = ref<DeviceInfo[]>([])
const optionsLoading = ref(false)

const rules: FormRules = {
  mapId: [{ required: true, message: '请选择地图', trigger: 'change' }],
  uavId: [{ required: true, message: '请选择无人机', trigger: 'change' }],
  algorithm: [{ required: true, message: '请选择算法', trigger: 'change' }]
}

const loadMapOptions = async () => {
  const res: any = await droneMapOptions()
  mapOptions.value = res.data ?? []
}

const loadUavOptions = async (mapId?: number) => {
  const res: any = await droneUavOptions(mapId)
  uavOptions.value = res.data ?? []
}

const loadAllUavOptions = async () => {
  const res: any = await droneUavOptions()
  uavOptionsAll.value = res.data ?? []
}

const loadTaskOptions = async () => {
  const res: any = await cmmsTaskPage({ page: 1, size: 200 })
  taskOptions.value = res.data?.records ?? []
}

const loadDeviceOptions = async () => {
  const res: any = await cmmsDevicePage({ page: 1, size: 500 })
  deviceOptions.value = res.data?.records ?? []
}

const loadFormOptions = async () => {
  optionsLoading.value = true
  try {
    await Promise.all([loadMapOptions(), loadTaskOptions(), loadDeviceOptions(), loadAllUavOptions()])
    await loadUavOptions(baseForm.mapId)
  } finally {
    optionsLoading.value = false
  }
}

const onMapChange = () => {
  baseForm.uavId = undefined
  loadUavOptions(baseForm.mapId)
}

const mapLabel = (id?: number) => {
  if (id == null) return '—'
  const m = mapOptions.value.find((x) => x.id === id)
  return m ? `${m.mapName} (#${id})` : `#${id}`
}

const uavLabel = (id?: number) => {
  if (id == null) return '—'
  const u = uavOptionsAll.value.find((x) => x.id === id) ?? uavOptions.value.find((x) => x.id === id)
  return u ? `${u.uavName} (#${id})` : `#${id}`
}

const taskLabel = (id?: number) => {
  if (id == null) return '—'
  const t = taskOptions.value.find((x) => x.id === id)
  return t ? `${t.taskName} (#${id})` : `#${id}`
}

const deviceLabel = (id: number) => {
  const d = deviceOptions.value.find((x) => x.id === id)
  return d ? `${d.deviceName} (#${id})` : `设备 #${id}`
}

function formatPoint(p: PointTriple): string {
  if (p.longitude == null || p.latitude == null || p.height == null) {
    throw new Error('请完整填写经度、纬度、高度')
  }
  return `${p.longitude},${p.latitude},${p.height}`
}

function toGeoPoint(p: PointTriple): GeoPoint {
  return {
    longitude: Number(p.longitude),
    latitude: Number(p.latitude),
    height: Number(p.height)
  }
}

function isPointComplete(p: PointTriple) {
  return p.longitude != null && p.latitude != null && p.height != null
}

function buildPathPoints(): GeoPoint[] | undefined {
  const mids = waypoints.value.filter(isPointComplete).map(toGeoPoint)
  if (mids.length === 0) {
    return undefined
  }
  return [toGeoPoint(start.value), ...mids, toGeoPoint(end.value)]
}

const addWaypoint = () => waypoints.value.push(emptyPoint())
const removeWaypoint = (i: number) => waypoints.value.splice(i, 1)
const moveWaypoint = (i: number, dir: -1 | 1) => {
  const j = i + dir
  if (j < 0 || j >= waypoints.value.length) return
  const t = waypoints.value[i]
  waypoints.value[i] = waypoints.value[j]
  waypoints.value[j] = t
}

const addPhotoPoint = () => photoPoints.value.push(emptyPoint())
const removePhotoPoint = (i: number) => photoPoints.value.splice(i, 1)

const addVisitDevice = () => {
  if (visitDeviceInput.value == null) {
    ElMessage.warning('请选择巡检设备')
    return
  }
  if (visitOrder.value.includes(visitDeviceInput.value)) {
    ElMessage.warning('该设备 ID 已添加')
    return
  }
  visitOrder.value.push(visitDeviceInput.value)
  visitDeviceInput.value = undefined
}

const removeVisitDevice = (id: number) => {
  visitOrder.value = visitOrder.value.filter((x) => x !== id)
}

const resetPlanForm = () => {
  baseForm.taskId = undefined
  baseForm.mapId = undefined
  baseForm.uavId = undefined
  baseForm.algorithm = 'A*'
  start.value = { longitude: 116.397428, latitude: 39.90923, height: 50 }
  end.value = { longitude: 116.407428, latitude: 39.91923, height: 50 }
  waypoints.value = []
  photoPoints.value = []
  visitOrder.value = []
  visitDeviceInput.value = undefined
  formRef.value?.clearValidate()
}

const submitPlan = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    formatPoint(start.value)
    formatPoint(end.value)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '起终点坐标不完整')
    return
  }

  const photos = photoPoints.value.filter(isPointComplete).map(toGeoPoint)
  const payload: RoutePlanCreateRequest = {
    taskId: baseForm.taskId,
    mapId: Number(baseForm.mapId),
    uavId: Number(baseForm.uavId),
    startPoint: formatPoint(start.value),
    endPoint: formatPoint(end.value),
    pathPoints: buildPathPoints(),
    photoPoints: photos.length ? photos : [],
    visitOrder: visitOrder.value.length ? [...visitOrder.value] : [],
    algorithm: baseForm.algorithm
  }

  submitting.value = true
  try {
    const res: any = await droneRoutePlanCreate(payload)
    ElMessage.success('路径规划已保存')
    const data = res.data
    if (data?.plan) {
      lastResult.value = data.plan
      lastDispatch.value = data.dispatch
    } else {
      lastResult.value = data
      lastDispatch.value = buildDispatchFromPlan(data)
    }
    activeTab.value = 'result'
    loadPage()
  } finally {
    submitting.value = false
  }
}

const lastResult = ref<UavRoutePlan | null>(null)
const lastDispatch = ref<UavRouteDispatchPayload | null>(null)

const dispatchJsonText = computed(() => {
  if (!lastDispatch.value) return ''
  return JSON.stringify(lastDispatch.value, null, 2)
})

const copyDispatchJson = async () => {
  if (!dispatchJsonText.value) return
  try {
    await navigator.clipboard.writeText(dispatchJsonText.value)
    ElMessage.success('已复制下发 JSON')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

const listLoading = ref(false)
const listQuery = ref({
  current: 1,
  size: 10,
  uavId: undefined as number | undefined,
  taskId: undefined as number | undefined
})
const list = ref<UavRoutePlan[]>([])
const listTotal = ref(0)

const loadPage = async () => {
  listLoading.value = true
  try {
    const res: any = await droneRoutePlanPage(listQuery.value)
    list.value = res.data.records ?? []
    listTotal.value = res.data.total ?? 0
  } finally {
    listLoading.value = false
  }
}

const handleListQuery = () => {
  listQuery.value.current = 1
  loadPage()
}

const handleListReset = () => {
  listQuery.value = { current: 1, size: 10, uavId: undefined, taskId: undefined }
  loadPage()
}

const detailVisible = ref(false)
const detailRow = ref<UavRoutePlan | null>(null)
const detailParsed = ref<{
  pathPoints: GeoPoint[]
  photoPoints: GeoPoint[]
  visitOrder: number[]
}>({ pathPoints: [], photoPoints: [], visitOrder: [] })
const detailDispatch = ref<UavRouteDispatchPayload | null>(null)

const detailDispatchJson = computed(() => {
  if (!detailDispatch.value) return ''
  return JSON.stringify(detailDispatch.value, null, 2)
})

const buildDispatchFromPlan = (plan: UavRoutePlan): UavRouteDispatchPayload => {
  const path = parseJson<GeoPoint[]>(plan.pathPoints, [])
  const takeoff = path[0] ?? parsePointString(plan.startPoint)
  const landing = path.length ? path[path.length - 1] : parsePointString(plan.endPoint)
  return {
    planId: plan.id,
    taskId: plan.taskId,
    mapId: plan.mapId,
    uavId: plan.uavId,
    algorithm: plan.algorithm,
    takeoff,
    landing,
    waypoints: path,
    photoWaypoints: parseJson(plan.photoPoints, []),
    deviceVisitOrder: parseJson(plan.visitOrder, []),
    estimated: {
      distanceM: plan.totalDistance,
      durationSec: plan.estimatedTime,
      batteryPct: plan.estimatedBattery
    }
  }
}

const parsePointString = (raw: string): GeoPoint | undefined => {
  const p = raw.split(',')
  if (p.length < 3) return undefined
  return { longitude: Number(p[0]), latitude: Number(p[1]), height: Number(p[2]) }
}

const parseJson = <T,>(raw: string | undefined, fallback: T): T => {
  if (!raw) return fallback
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

const openDetail = async (row: UavRoutePlan) => {
  if (row.id == null) return
  try {
    const res: any = await droneRoutePlanGetById(row.id)
    const data = res.data
    if (data?.plan) {
      detailRow.value = data.plan
      detailDispatch.value = data.dispatch
    } else {
      detailRow.value = data
      detailDispatch.value = buildDispatchFromPlan(data)
    }
  } catch {
    detailRow.value = row
    detailDispatch.value = buildDispatchFromPlan(row)
  }
  detailParsed.value = {
    pathPoints: parseJson(detailRow.value?.pathPoints, []),
    photoPoints: parseJson(detailRow.value?.photoPoints, []),
    visitOrder: parseJson(detailRow.value?.visitOrder, [])
  }
  detailVisible.value = true
}

const formatDuration = (sec: number) => {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`
}

onMounted(async () => {
  await loadFormOptions()
  loadPage()
})
</script>

<template>
  <div class="route-plan-page">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="新建规划" name="plan">
        <el-form
          ref="formRef"
          v-loading="optionsLoading"
          :model="baseForm"
          :rules="rules"
          label-width="110px"
          class="plan-form"
        >
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="地图" prop="mapId">
                <el-select
                  v-model="baseForm.mapId"
                  filterable
                  clearable
                  placeholder="请选择地图"
                  class="w-full"
                  @change="onMapChange"
                >
                  <el-option
                    v-for="m in mapOptions"
                    :key="m.id"
                    :label="`${m.mapName} (ID:${m.id})`"
                    :value="m.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="无人机" prop="uavId">
                <el-select
                  v-model="baseForm.uavId"
                  filterable
                  clearable
                  placeholder="请先选地图"
                  class="w-full"
                  :disabled="!baseForm.mapId"
                >
                  <el-option
                    v-for="u in uavOptions"
                    :key="u.id"
                    :label="`${u.uavName}${u.uavCode ? ' · ' + u.uavCode : ''} (ID:${u.id})`"
                    :value="u.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="巡检任务">
                <el-select
                  v-model="baseForm.taskId"
                  filterable
                  clearable
                  placeholder="可选，关联巡检任务"
                  class="w-full"
                >
                  <el-option
                    v-for="t in taskOptions"
                    :key="t.id"
                    :label="`${t.taskName}${t.taskCode ? ' · ' + t.taskCode : ''} (ID:${t.id})`"
                    :value="t.id!"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="规划算法" prop="algorithm">
                <el-select v-model="baseForm.algorithm" class="w-full">
                  <el-option v-for="o in algorithmOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">起飞点 / 降落点</el-divider>
          <el-row :gutter="24">
            <el-col :xs="24" :md="12">
              <div class="point-block">
                <div class="point-title">起飞点</div>
                <PointInputs v-model="start" />
              </div>
            </el-col>
            <el-col :xs="24" :md="12">
              <div class="point-block">
                <div class="point-title">降落点</div>
                <PointInputs v-model="end" />
              </div>
            </el-col>
          </el-row>

          <el-divider content-position="left">
            途经航点
            <el-text type="info" size="small" style="margin-left: 8px">按顺序填写；留空则仅连接起终点</el-text>
          </el-divider>
          <div class="table-toolbar">
            <el-button type="primary" plain size="small" @click="addWaypoint">添加途经点</el-button>
          </div>
          <el-table v-if="waypoints.length" :data="waypoints" border size="small" class="point-table">
            <el-table-column label="#" width="50" type="index" />
            <el-table-column label="经度" min-width="120">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.longitude"
                  :precision="6"
                  :step="0.0001"
                  controls-position="right"
                  class="w-full"
                />
              </template>
            </el-table-column>
            <el-table-column label="纬度" min-width="120">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.latitude"
                  :precision="6"
                  :step="0.0001"
                  controls-position="right"
                  class="w-full"
                />
              </template>
            </el-table-column>
            <el-table-column label="高度(m)" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row.height" :min="0" :step="1" controls-position="right" class="w-full" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ $index }">
                <el-button link type="primary" :disabled="$index === 0" @click="moveWaypoint($index, -1)">上移</el-button>
                <el-button
                  link
                  type="primary"
                  :disabled="$index === waypoints.length - 1"
                  @click="moveWaypoint($index, 1)"
                >
                  下移
                </el-button>
                <el-button link type="danger" @click="removeWaypoint($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无途经航点" :image-size="64" />

          <el-divider content-position="left">拍照航点</el-divider>
          <div class="table-toolbar">
            <el-button type="primary" plain size="small" @click="addPhotoPoint">添加拍照点</el-button>
          </div>
          <el-table v-if="photoPoints.length" :data="photoPoints" border size="small" class="point-table">
            <el-table-column label="#" width="50" type="index" />
            <el-table-column label="经度" min-width="120">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.longitude"
                  :precision="6"
                  :step="0.0001"
                  controls-position="right"
                  class="w-full"
                />
              </template>
            </el-table-column>
            <el-table-column label="纬度" min-width="120">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.latitude"
                  :precision="6"
                  :step="0.0001"
                  controls-position="right"
                  class="w-full"
                />
              </template>
            </el-table-column>
            <el-table-column label="高度(m)" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row.height" :min="0" :step="1" controls-position="right" class="w-full" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removePhotoPoint($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无拍照航点" :image-size="64" />

          <el-divider content-position="left">巡检设备访问顺序</el-divider>
          <div class="visit-row">
            <el-select
              v-model="visitDeviceInput"
              filterable
              clearable
              placeholder="选择巡检设备"
              style="width: min(100%, 360px)"
            >
              <el-option
                v-for="d in deviceOptions"
                :key="d.id"
                :label="`${d.deviceName}${d.deviceCode ? ' · ' + d.deviceCode : ''} (ID:${d.id})`"
                :value="d.id!"
                :disabled="visitOrder.includes(d.id!)"
              />
            </el-select>
            <el-button type="primary" plain @click="addVisitDevice">加入顺序</el-button>
          </div>
          <div v-if="visitOrder.length" class="visit-tags">
            <el-tag
              v-for="(id, idx) in visitOrder"
              :key="id"
              closable
              type="info"
              @close="removeVisitDevice(id)"
            >
              {{ idx + 1 }}. {{ deviceLabel(id) }}
            </el-tag>
          </div>

          <div class="form-actions">
            <el-button type="primary" :loading="submitting" @click="submitPlan">生成并保存规划</el-button>
            <el-button @click="resetPlanForm">重置</el-button>
          </div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="规划结果" name="result">
        <el-row v-if="lastResult" :gutter="16">
          <el-col :xs="24" :lg="10">
            <el-card shadow="never" class="result-card">
              <template #header>最近一次规划</template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="规划 ID">{{ lastResult.id }}</el-descriptions-item>
                <el-descriptions-item label="算法">{{ lastResult.algorithm }}</el-descriptions-item>
                <el-descriptions-item label="总距离">{{ lastResult.totalDistance?.toFixed(2) }} m</el-descriptions-item>
                <el-descriptions-item label="预计耗时">{{ formatDuration(lastResult.estimatedTime) }}</el-descriptions-item>
                <el-descriptions-item label="预计耗电">{{ lastResult.estimatedBattery?.toFixed(1) }} %</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ lastResult.createTime }}</el-descriptions-item>
                <el-descriptions-item label="起飞点">{{ lastResult.startPoint }}</el-descriptions-item>
                <el-descriptions-item label="降落点">{{ lastResult.endPoint }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :xs="24" :lg="14">
            <el-card shadow="never" class="result-card json-card">
              <template #header>
                <div class="json-card-header">
                  <span>下发无人机 JSON</span>
                  <el-button type="primary" link @click="copyDispatchJson">复制</el-button>
                </div>
              </template>
              <pre class="dispatch-json">{{ dispatchJsonText }}</pre>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else description="提交规划后将在此展示结果" />
      </el-tab-pane>

      <el-tab-pane label="历史记录" name="history">
        <el-form :inline="true" class="query-form">
          <el-form-item label="无人机">
            <el-select v-model="listQuery.uavId" filterable clearable placeholder="全部" style="width: 220px">
              <el-option
                v-for="u in uavOptionsAll"
                :key="u.id"
                :label="`${u.uavName} (ID:${u.id})`"
                :value="u.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="巡检任务">
            <el-select v-model="listQuery.taskId" filterable clearable placeholder="全部" style="width: 240px">
              <el-option
                v-for="t in taskOptions"
                :key="t.id"
                :label="`${t.taskName} (ID:${t.id})`"
                :value="t.id!"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleListQuery">查询</el-button>
            <el-button @click="handleListReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="listLoading" :data="list" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="无人机" min-width="140">
            <template #default="{ row }">{{ uavLabel(row.uavId) }}</template>
          </el-table-column>
          <el-table-column label="地图" min-width="140">
            <template #default="{ row }">{{ mapLabel(row.mapId) }}</template>
          </el-table-column>
          <el-table-column label="任务" min-width="140">
            <template #default="{ row }">{{ taskLabel(row.taskId) }}</template>
          </el-table-column>
          <el-table-column prop="algorithm" label="算法" width="80" />
          <el-table-column label="总距离(m)" width="110">
            <template #default="{ row }">{{ row.totalDistance?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="预计耗时" width="110">
            <template #default="{ row }">{{ formatDuration(row.estimatedTime) }}</template>
          </el-table-column>
          <el-table-column label="预计耗电" width="100">
            <template #default="{ row }">{{ row.estimatedBattery?.toFixed(1) }}%</template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="160" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="listQuery.current"
            v-model:page-size="listQuery.size"
            :total="listTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadPage"
            @size-change="loadPage"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" title="路径规划详情" width="900px" destroy-on-close>
      <template v-if="detailRow">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="ID">{{ detailRow.id }}</el-descriptions-item>
          <el-descriptions-item label="算法">{{ detailRow.algorithm }}</el-descriptions-item>
          <el-descriptions-item label="无人机">{{ detailRow.uavId }}</el-descriptions-item>
          <el-descriptions-item label="地图">{{ detailRow.mapId }}</el-descriptions-item>
          <el-descriptions-item label="任务">{{ detailRow.taskId ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailRow.createTime }}</el-descriptions-item>
          <el-descriptions-item label="总距离">{{ detailRow.totalDistance?.toFixed(2) }} m</el-descriptions-item>
          <el-descriptions-item label="预计耗时">{{ formatDuration(detailRow.estimatedTime) }}</el-descriptions-item>
          <el-descriptions-item label="起飞点" :span="2">{{ detailRow.startPoint }}</el-descriptions-item>
          <el-descriptions-item label="降落点" :span="2">{{ detailRow.endPoint }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">飞行路径点</el-divider>
        <el-table :data="detailParsed.pathPoints" border size="small" max-height="200">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="longitude" label="经度" />
          <el-table-column prop="latitude" label="纬度" />
          <el-table-column prop="height" label="高度(m)" />
        </el-table>

        <el-divider content-position="left">拍照航点</el-divider>
        <el-table :data="detailParsed.photoPoints" border size="small" max-height="160">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="longitude" label="经度" />
          <el-table-column prop="latitude" label="纬度" />
          <el-table-column prop="height" label="高度(m)" />
        </el-table>
        <el-empty v-if="!detailParsed.photoPoints.length" description="无拍照航点" :image-size="48" />

        <el-divider content-position="left">设备访问顺序</el-divider>
        <el-tag v-for="(id, i) in detailParsed.visitOrder" :key="id" style="margin: 4px">{{ i + 1 }}. {{ deviceLabel(id) }}</el-tag>
        <el-empty v-if="!detailParsed.visitOrder.length" description="未指定访问顺序" :image-size="48" />

        <el-divider content-position="left">下发无人机 JSON</el-divider>
        <pre class="dispatch-json dispatch-json--dialog">{{ detailDispatchJson }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.route-plan-page {
  min-height: 100%;
}
.plan-form {
  padding: 8px 4px 24px;
}
.point-block {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 8px;
}
.point-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}
.table-toolbar {
  margin-bottom: 8px;
}
.point-table {
  margin-bottom: 16px;
}
.visit-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.visit-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.form-actions {
  margin-top: 24px;
}
.w-full {
  width: 100%;
}
.query-form {
  margin-bottom: 12px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.result-card {
  margin-bottom: 16px;
}
.json-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dispatch-json {
  margin: 0;
  padding: 12px;
  max-height: 420px;
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
.dispatch-json--dialog {
  max-height: 280px;
}
</style>
