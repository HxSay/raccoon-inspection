<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { cmmsDevicePage, type DeviceInfo } from '@/api/cmms'
import { getUserPage, type UserResponse } from '@/api/user'
import { iwoCreate, type IwoStepDraft } from '@/api/inspectionWorkOrder'

const router = useRouter()

const deviceOptions = ref<DeviceInfo[]>([])
const userOptions = ref<UserResponse[]>([])

const form = ref({
  area: '110kV 开关室',
  shiftType: 1,
  inspectorId: 1 as number,
  inspectorName: '',
  planStartTime: '',
  planEndTime: '',
  remark: ''
})

const selectedDeviceIds = ref<number[]>([])
const checkRows = ref<{ name: string; unit: string; min?: number; max?: number; method: string; desc: string }[]>([
  { name: '局放检测', unit: 'pC', min: 0, max: 50, method: '特高频', desc: '' },
  { name: '红外测温', unit: '℃', min: -10, max: 85, method: '红外', desc: '' }
])

const steps = ref<IwoStepDraft[]>([])

const userSelectLabel = (u: UserResponse) => {
  const nick = u.nickname?.trim()
  return nick ? `${u.username}（${nick}）` : u.username
}

const loadDevices = async () => {
  const res: any = await cmmsDevicePage({ page: 1, size: 500 })
  deviceOptions.value = res.data.records
}

const loadUsers = async () => {
  const res: any = await getUserPage({ page: 1, size: 500, status: 1 })
  userOptions.value = res.data.records
  if (!form.value.inspectorName && userOptions.value.length) {
    form.value.inspectorId = userOptions.value[0].id!
    form.value.inspectorName = userOptions.value[0].nickname || userOptions.value[0].username
  }
}

onMounted(async () => {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const s = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:00`
  const eDate = new Date(now.getTime() + 8 * 3600 * 1000)
  const e = `${eDate.getFullYear()}-${pad(eDate.getMonth() + 1)}-${pad(eDate.getDate())} ${pad(eDate.getHours())}:${pad(eDate.getMinutes())}:00`
  form.value.planStartTime = s
  form.value.planEndTime = e
  await loadDevices()
  await loadUsers()
})

const onInspectorChange = (uid: number) => {
  const u = userOptions.value.find((x) => x.id === uid)
  form.value.inspectorName = u ? u.nickname || u.username : ''
}

const buildSteps = () => {
  if (!form.value.area.trim()) {
    ElMessage.warning('请填写巡检区域')
    return
  }
  if (!selectedDeviceIds.value.length) {
    ElMessage.warning('请选择至少一台设备')
    return
  }
  if (!checkRows.value.length) {
    ElMessage.warning('请配置至少一项检测项')
    return
  }
  const devices = selectedDeviceIds.value
    .map((id) => deviceOptions.value.find((d) => d.id === id))
    .filter(Boolean) as DeviceInfo[]
  let n = 1
  const out: IwoStepDraft[] = []
  for (const dev of devices) {
    out.push({
      stepOrder: n++,
      type: 'path',
      target: form.value.area,
      description: `按路径前往 ${dev.deviceName}`,
      deviceId: dev.id,
      deviceName: dev.deviceName
    })
    out.push({
      stepOrder: n++,
      type: 'stop',
      target: dev.location || form.value.area,
      description: `停靠 ${dev.deviceName}，请扫码确认`,
      deviceId: null,
      deviceName: dev.deviceName
    })
    for (const c of checkRows.value) {
      if (!c.name.trim()) continue
      out.push({
        stepOrder: n++,
        type: 'collect',
        deviceId: dev.id!,
        deviceName: dev.deviceName,
        checkItem: c.name.trim(),
        standardMin: c.min,
        standardMax: c.max,
        unit: c.unit || undefined,
        detectionMethod: c.method || undefined,
        description: c.desc || undefined
      })
    }
    out.push({
      stepOrder: n++,
      type: 'report',
      description: `${dev.deviceName} 本段巡检上报`,
      deviceId: dev.id,
      deviceName: dev.deviceName
    })
  }
  steps.value = out
  ElMessage.success(`已生成 ${out.length} 个步骤`)
}

const submit = async () => {
  if (!steps.value.length) {
    ElMessage.warning('请先生成步骤')
    return
  }
  await iwoCreate({
    area: form.value.area,
    shiftType: form.value.shiftType,
    inspectorId: form.value.inspectorId,
    inspectorName: form.value.inspectorName,
    planStartTime: form.value.planStartTime,
    planEndTime: form.value.planEndTime,
    remark: form.value.remark,
    steps: steps.value
  })
  ElMessage.success('创建成功')
  router.push({ path: '/cmms/inspection', query: { tab: 'order' } })
}

const addCheckRow = () => {
  checkRows.value.push({ name: '', unit: '', min: undefined, max: undefined, method: '', desc: '' })
}
</script>

<template>
  <div class="page">
    <el-page-header @back="router.push({ path: '/cmms/inspection', query: { tab: 'order' } })" content="新建巡检工单" />

    <el-card class="card" shadow="never">
      <template #header>基础信息</template>
      <el-form label-width="110px" style="max-width: 720px">
        <el-form-item label="巡检区域" required>
          <el-input v-model="form.area" />
        </el-form-item>
        <el-form-item label="班次" required>
          <el-select v-model="form.shiftType" style="width: 200px">
            <el-option label="早班" :value="1" />
            <el-option label="中班" :value="2" />
            <el-option label="夜班" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="巡检人员" required>
          <el-select v-model="form.inspectorId" filterable style="width: 320px" @change="onInspectorChange">
            <el-option v-for="u in userOptions" :key="u.id!" :label="userSelectLabel(u)" :value="u.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始" required>
          <el-date-picker v-model="form.planStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划结束" required>
          <el-date-picker v-model="form.planEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="card" shadow="never">
      <template #header>设备与检测项</template>
      <el-form label-width="110px">
        <el-form-item label="关联设备">
          <el-select v-model="selectedDeviceIds" multiple filterable style="width: 100%" placeholder="多选设备">
            <el-option
              v-for="d in deviceOptions"
              :key="d.id!"
              :label="`${d.deviceName}（${d.deviceCode}）`"
              :value="d.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="检测项模板">
          <el-table :data="checkRows" border size="small">
            <el-table-column label="检测项" min-width="120"><template #default="{ row }"><el-input v-model="row.name" /></template></el-table-column>
            <el-table-column label="单位" width="90"><template #default="{ row }"><el-input v-model="row.unit" /></template></el-table-column>
            <el-table-column label="标准下限" width="110"><template #default="{ row }"><el-input-number v-model="row.min" :controls="false" /></template></el-table-column>
            <el-table-column label="标准上限" width="110"><template #default="{ row }"><el-input-number v-model="row.max" :controls="false" /></template></el-table-column>
            <el-table-column label="检测方式" width="110"><template #default="{ row }"><el-input v-model="row.method" /></template></el-table-column>
            <el-table-column label="说明" min-width="100"><template #default="{ row }"><el-input v-model="row.desc" /></template></el-table-column>
          </el-table>
          <el-button style="margin-top: 8px" @click="addCheckRow">添加检测项</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="buildSteps">生成有序步骤（路径/停靠/采集/上报）</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="steps.length" class="card" shadow="never">
      <template #header>步骤预览（共 {{ steps.length }} 步）</template>
      <el-table :data="steps" border size="small" max-height="420">
        <el-table-column prop="stepOrder" label="#" width="50" />
        <el-table-column prop="type" label="类型" width="80" />
        <el-table-column prop="target" label="目标" min-width="120" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column prop="deviceName" label="设备" width="120" show-overflow-tooltip />
        <el-table-column prop="checkItem" label="检测项" width="120" />
        <el-table-column label="标准" width="120">
          <template #default="{ row }">
            <span v-if="row.standardMin != null || row.standardMax != null">{{ row.standardMin }} ~ {{ row.standardMax }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px">
        <el-button type="success" @click="submit">提交创建（待下发）</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  padding: 8px;
}
.card {
  margin-top: 12px;
}
</style>
