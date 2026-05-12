<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cmmsPlanPage,
  cmmsPlanSave,
  cmmsPlanDelete,
  cmmsPlanGenerateTasks,
  cmmsTaskPage,
  cmmsTaskSave,
  cmmsTaskDelete,
  cmmsTaskComplete,
  cmmsRecordList,
  cmmsDevicePage,
  cmmsPointList,
  type InspectionPlan,
  type InspectionTask,
  type InspectionRecord,
  type DeviceInfo,
  type InspectionPoint
} from '@/api/cmms'
import { getUserPage, type UserResponse } from '@/api/user'

const tab = ref('plan')

const planQuery = ref({ page: 1, size: 10, planName: '', status: undefined as number | undefined })
const planList = ref<InspectionPlan[]>([])
const planTotal = ref(0)
const planDialog = ref(false)
const planForm = ref<InspectionPlan>({
  planName: '',
  deviceIds: '[]',
  cycleType: 1,
  cycleValue: 1,
  execUserId: 1,
  startTime: '',
  status: 1
})

const deviceOptions = ref<DeviceInfo[]>([])
const userOptions = ref<UserResponse[]>([])
const selectedDeviceIds = ref<number[]>([])

const taskQuery = ref({ page: 1, size: 10, status: undefined as number | undefined })
const taskList = ref<InspectionTask[]>([])
const taskTotal = ref(0)

const completeOpen = ref(false)
const currentTask = ref<InspectionTask | null>(null)
const points = ref<InspectionPoint[]>([])
const lines = ref<{ pointId: number; checkValue: string; isNormal: number }[]>([])

const recordOpen = ref(false)
const records = ref<InspectionRecord[]>([])

const cycleLabels: Record<number, string> = { 1: '天', 2: '周', 3: '月' }

const planStatus = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 }
]

const taskStatus = [
  { label: '待执行', value: 0 },
  { label: '执行中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已过期', value: 3 }
]

const loadPlans = async () => {
  const res: any = await cmmsPlanPage(planQuery.value)
  planList.value = res.data.records
  planTotal.value = res.data.total
}

const loadTasks = async () => {
  const res: any = await cmmsTaskPage(taskQuery.value)
  taskList.value = res.data.records
  taskTotal.value = res.data.total
}

const loadDevices = async () => {
  const res: any = await cmmsDevicePage({ page: 1, size: 500 })
  deviceOptions.value = res.data.records
}

const loadUsers = async () => {
  const res: any = await getUserPage({ page: 1, size: 500, status: 1 })
  userOptions.value = res.data.records
}

const userSelectLabel = (u: UserResponse) => {
  const nick = u.nickname?.trim()
  return nick ? `${u.username}（${nick}）` : u.username
}

onMounted(async () => {
  await loadDevices()
  await loadUsers()
  loadPlans()
  loadTasks()
})

const syncDeviceIdsJson = () => {
  planForm.value.deviceIds = JSON.stringify(selectedDeviceIds.value)
}

const openPlan = (row?: InspectionPlan) => {
  if (row) {
    planForm.value = { ...row }
    try {
      selectedDeviceIds.value = JSON.parse(row.deviceIds || '[]') as number[]
    } catch {
      selectedDeviceIds.value = []
    }
  } else {
    const now = new Date()
    const pad = (n: number) => (n < 10 ? '0' + n : '' + n)
    const local =
      now.getFullYear() +
      '-' +
      pad(now.getMonth() + 1) +
      '-' +
      pad(now.getDate()) +
      ' ' +
      pad(now.getHours()) +
      ':' +
      pad(now.getMinutes()) +
      ':' +
      pad(now.getSeconds())
    planForm.value = {
      planName: '',
      deviceIds: '[]',
      cycleType: 1,
      cycleValue: 1,
      execUserId: 1,
      startTime: local,
      status: 1
    }
    selectedDeviceIds.value = []
  }
  planDialog.value = true
}

const savePlan = async () => {
  syncDeviceIdsJson()
  const p = planForm.value
  const body: InspectionPlan = {
    planName: p.planName,
    deviceIds: p.deviceIds,
    cycleType: p.cycleType,
    cycleValue: p.cycleValue,
    execUserId: p.execUserId,
    startTime: p.startTime,
    status: p.status ?? 1
  }
  if (p.id != null) body.id = p.id
  if (p.endTime) body.endTime = p.endTime
  await cmmsPlanSave(body)
  ElMessage.success('保存成功')
  planDialog.value = false
  loadPlans()
}

const delPlan = async (id: number) => {
  await ElMessageBox.confirm('确认删除计划？')
  await cmmsPlanDelete(id)
  loadPlans()
}

const genTasks = async (id: number) => {
  const res: any = await cmmsPlanGenerateTasks(id)
  if (res.code === 200) {
    ElMessage.success(res.msg || '生成成功')
    loadTasks()
  }
}

const openTask = (row?: InspectionTask) => {
  if (!row) {
    const now = new Date()
    const pad = (n: number) => (n < 10 ? '0' + n : '' + n)
    const local =
      now.getFullYear() +
      '-' +
      pad(now.getMonth() + 1) +
      '-' +
      pad(now.getDate()) +
      ' ' +
      pad(now.getHours()) +
      ':' +
      pad(now.getMinutes()) +
      ':' +
      pad(now.getSeconds())
    taskForm.value = {
      deviceId: deviceOptions.value[0]?.id ?? 1,
      taskName: '手动巡检任务',
      execUserId: userOptions.value[0]?.id ?? 1,
      planExecuteTime: local,
      status: 0
    }
  } else {
    taskForm.value = { ...row }
  }
  taskDialog.value = true
}

const taskDialog = ref(false)
const taskForm = ref<InspectionTask>({
  deviceId: 1,
  taskName: '',
  execUserId: 1,
  planExecuteTime: '',
  status: 0
})

const saveTask = async () => {
  await cmmsTaskSave(taskForm.value)
  ElMessage.success('保存成功')
  taskDialog.value = false
  loadTasks()
}

const delTask = async (id: number) => {
  await ElMessageBox.confirm('确认删除任务？')
  await cmmsTaskDelete(id)
  loadTasks()
}

const openComplete = async (row: InspectionTask) => {
  currentTask.value = row
  const res: any = await cmmsPointList(row.deviceId)
  points.value = res.data
  lines.value = points.value.map((p) => ({
    pointId: p.id!,
    checkValue: '',
    isNormal: 1
  }))
  completeOpen.value = true
}

const submitComplete = async () => {
  if (!currentTask.value?.id) return
  await cmmsTaskComplete({
    taskId: currentTask.value.id,
    records: lines.value.map((l) => ({
      pointId: l.pointId,
      checkValue: l.checkValue,
      isNormal: l.isNormal
    }))
  })
  ElMessage.success('已提交')
  completeOpen.value = false
  loadTasks()
}

const viewRecords = async (taskId: number) => {
  const res: any = await cmmsRecordList(taskId)
  records.value = res.data
  recordOpen.value = true
}

watch(tab, (v) => {
  if (v === 'plan') loadPlans()
  if (v === 'task') loadTasks()
})
</script>

<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="巡检计划" name="plan">
        <el-form :inline="true">
          <el-form-item label="计划名">
            <el-input v-model="planQuery.planName" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="planQuery.status" clearable placeholder="全部" style="width: 120px">
              <el-option v-for="o in planStatus" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="() => { planQuery.page = 1; loadPlans() }">查询</el-button>
            <el-button type="success" @click="openPlan()">新增计划</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="planList" border stripe>
          <el-table-column prop="planName" label="计划名称" min-width="140" />
          <el-table-column label="周期" width="120">
            <template #default="{ row }">每 {{ row.cycleValue }} {{ cycleLabels[row.cycleType] || '' }}</template>
          </el-table-column>
          <el-table-column prop="execUserId" label="执行人ID" width="100" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPlan(row)">编辑</el-button>
              <el-button link type="success" @click="genTasks(row.id)">生成任务</el-button>
              <el-button link type="danger" @click="delPlan(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="planQuery.page"
          v-model:page-size="planQuery.size"
          :total="planTotal"
          layout="total, prev, pager, next"
          @current-change="loadPlans"
        />
      </el-tab-pane>

      <el-tab-pane label="巡检任务" name="task">
        <el-form :inline="true">
          <el-form-item label="状态">
            <el-select v-model="taskQuery.status" clearable placeholder="全部" style="width: 120px">
              <el-option v-for="o in taskStatus" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="() => { taskQuery.page = 1; loadTasks() }">查询</el-button>
            <el-button type="success" @click="openTask()">手动建任务</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="taskList" border stripe>
          <el-table-column prop="taskCode" label="任务单号" width="170" />
          <el-table-column prop="taskName" label="任务名称" min-width="160" />
          <el-table-column prop="deviceId" label="设备ID" width="90" />
          <el-table-column prop="planExecuteTime" label="计划执行时间" width="170" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              {{ taskStatus.find((s) => s.value === row.status)?.label ?? row.status }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status !== 2" link type="primary" @click="openComplete(row)">填报完成</el-button>
              <el-button link type="info" @click="viewRecords(row.id)">记录</el-button>
              <el-button link type="danger" @click="delTask(row.id)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="taskQuery.page"
          v-model:page-size="taskQuery.size"
          :total="taskTotal"
          layout="total, prev, pager, next"
          @current-change="loadTasks"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="planDialog" title="巡检计划" width="640px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="计划名称" required><el-input v-model="planForm.planName" /></el-form-item>
        <el-form-item label="关联设备">
          <el-select v-model="selectedDeviceIds" multiple filterable placeholder="选择设备" style="width: 100%" @change="syncDeviceIdsJson">
            <el-option v-for="d in deviceOptions" :key="d.id" :label="d.deviceName + ' (' + d.deviceCode + ')'" :value="d.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="周期类型">
          <el-radio-group v-model="planForm.cycleType">
            <el-radio :label="1">天</el-radio>
            <el-radio :label="2">周</el-radio>
            <el-radio :label="3">月</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="周期值"><el-input-number v-model="planForm.cycleValue" :min="1" /></el-form-item>
        <el-form-item label="执行人用户ID"><el-input-number v-model="planForm.execUserId" :min="1" /></el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker v-model="planForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker v-model="planForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="planForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialog = false">取消</el-button>
        <el-button type="primary" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="taskDialog" title="巡检任务" width="560px">
      <el-form label-width="110px">
        <el-form-item label="设备">
          <el-select v-model="taskForm.deviceId" filterable placeholder="选择设备" style="width: 100%">
            <el-option
              v-for="d in deviceOptions"
              :key="d.id"
              :label="d.deviceName + '（' + d.deviceCode + '）'"
              :value="d.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名称"><el-input v-model="taskForm.taskName" /></el-form-item>
        <el-form-item label="执行人">
          <el-select v-model="taskForm.execUserId" filterable placeholder="选择执行人" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="userSelectLabel(u)" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划执行时间">
          <el-date-picker v-model="taskForm.planExecuteTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="taskForm.status" style="width: 100%">
            <el-option v-for="o in taskStatus" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="completeOpen" title="填报巡检结果" width="720px" destroy-on-close>
      <el-table :data="lines" border>
        <el-table-column label="巡检点" width="160">
          <template #default="{ row }">
            {{ points.find((p) => p.id === row.pointId)?.pointName }}
          </template>
        </el-table-column>
        <el-table-column label="检查值">
          <template #default="{ row }">
            <el-input v-model="row.checkValue" />
          </template>
        </el-table-column>
        <el-table-column label="是否正常" width="140">
          <template #default="{ row }">
            <el-select v-model="row.isNormal" style="width: 100px">
              <el-option :value="1" label="正常" />
              <el-option :value="0" label="异常" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="completeOpen = false">取消</el-button>
        <el-button type="primary" @click="submitComplete">提交并完成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recordOpen" title="巡检记录" width="720px">
      <el-table :data="records" border size="small">
        <el-table-column prop="pointId" label="点ID" width="80" />
        <el-table-column prop="checkValue" label="检查值" />
        <el-table-column prop="isNormal" label="正常" width="80">
          <template #default="{ row }">{{ row.isNormal === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
</style>
