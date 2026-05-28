<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { droneMapOptions, type UavMapOption } from '@/api/drone'
import {
  fieldSceneDevicePage,
  fieldSceneDeviceCreate,
  fieldSceneDeviceUpdate,
  fieldSceneDeviceDelete,
  fieldSceneDeviceInitBuiltin,
  DEVICE_TYPE_OPTIONS,
  SYNC_SOURCE_LABEL,
  type FieldSceneDeviceVO
} from '@/api/fieldSceneDevice'

const router = useRouter()
const loading = ref(false)
const mapOptions = ref<UavMapOption[]>([])
const tableData = ref<FieldSceneDeviceVO[]>([])
const total = ref(0)

const query = reactive({
  current: 1,
  size: 10,
  mapId: undefined as number | undefined,
  sceneType: undefined as string | undefined,
  keyword: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增现场设备')
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  mapId: undefined as number | undefined,
  deviceName: '',
  deviceType: 'TOWER',
  longitude: undefined as number | undefined,
  latitude: undefined as number | undefined,
  height: undefined as number | undefined,
  sceneX: undefined as number | undefined,
  sceneY: undefined as number | undefined,
  sceneZ: undefined as number | undefined,
  sceneType: '',
  sceneObjectId: '',
  locationDesc: '',
  status: 1,
  remark: ''
})

const rules: FormRules = {
  mapId: [{ required: true, message: '请选择虚拟场景', trigger: 'change' }],
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const loadMaps = async () => {
  const res: any = await droneMapOptions()
  mapOptions.value = res.data ?? []
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await fieldSceneDevicePage(query)
    tableData.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.keyword = ''
  query.mapId = undefined
  query.sceneType = undefined
  query.current = 1
  loadData()
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    mapId: query.mapId,
    deviceName: '',
    deviceType: 'CUSTOM',
    longitude: undefined,
    latitude: undefined,
    height: undefined,
    sceneX: undefined,
    sceneY: undefined,
    sceneZ: undefined,
    sceneType: '',
    sceneObjectId: '',
    locationDesc: '',
    status: 1,
    remark: ''
  })
  dialogTitle.value = '新增现场设备'
  dialogVisible.value = true
}

const openEdit = (row: FieldSceneDeviceVO) => {
  Object.assign(form, row)
  dialogTitle.value = '编辑现场设备'
  dialogVisible.value = true
}

const onMapChange = (mapId: number) => {
  const m = mapOptions.value.find((x) => x.id === mapId)
  if (m?.sceneType) form.sceneType = m.sceneType
}

const submit = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form, syncSource: 'MANUAL' }
    if (form.id) {
      await fieldSceneDeviceUpdate(form.id, payload)
      ElMessage.success('已更新')
    } else {
      await fieldSceneDeviceCreate(payload)
      ElMessage.success('已添加')
    }
    dialogVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}

const remove = async (row: FieldSceneDeviceVO) => {
  await ElMessageBox.confirm(`确定删除「${row.deviceName}」？`, '提示', { type: 'warning' })
  try {
    await fieldSceneDeviceDelete(row.id!)
    ElMessage.success('已删除')
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '删除失败')
  }
}

const initBuiltin = async () => {
  const res: any = await fieldSceneDeviceInitBuiltin()
  ElMessage.success(`已初始化 ${res.data?.initialized ?? 0} 基内置杆塔坐标`)
  loadData()
}

const goSim = () => {
  router.push('/sim/drone')
}

const typeLabel = (t: string) => DEVICE_TYPE_OPTIONS.find((o) => o.value === t)?.label ?? t

onMounted(async () => {
  await loadMaps()
  await loadData()
})
</script>

<template>
  <div class="field-device-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">现场设备管理</h2>
        <p class="page-desc">
          对应仿真场景中的杆塔、阀门等设备；在仿真 3D 编辑器中添加物体会自动同步到此。与 CMMS「设备台账」相互独立。
        </p>
      </div>
      <div class="header-actions">
        <el-button @click="initBuiltin">初始化内置杆塔</el-button>
        <el-button @click="goSim">打开仿真场景</el-button>
        <el-button type="primary" @click="openCreate">新增设备</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" @submit.prevent="loadData">
        <el-form-item label="虚拟场景">
          <el-select v-model="query.mapId" clearable placeholder="全部" style="width: 200px">
            <el-option v-for="m in mapOptions" :key="m.id" :label="m.mapName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="名称/位置/对象ID" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table v-loading="loading" :data="tableData" stripe border>
      <el-table-column prop="deviceName" label="设备名称" min-width="120" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeLabel(row.deviceType) }}</template>
      </el-table-column>
      <el-table-column prop="mapName" label="虚拟场景" min-width="140" />
      <el-table-column label="WGS84 坐标" min-width="200">
        <template #default="{ row }">
          <span v-if="row.longitude != null">
            {{ row.longitude }}, {{ row.latitude }}, {{ row.height }} m
          </span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="场景坐标 (X,Y,Z)" min-width="150">
        <template #default="{ row }">
          <span v-if="row.sceneX != null">{{ row.sceneX }}, {{ row.sceneY }}, {{ row.sceneZ }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="96" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.syncSource === 'SCENE_SYNC' ? 'success' : row.syncSource === 'BUILTIN' ? 'warning' : 'info'">
            {{ SYNC_SOURCE_LABEL[row.syncSource ?? ''] ?? row.syncSource }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sceneObjectId" label="场景对象ID" min-width="120" show-overflow-tooltip />
      <el-table-column prop="locationDesc" label="位置描述" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="72" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            type="danger"
            :disabled="row.syncSource === 'BUILTIN'"
            @click="remove(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="108px">
        <el-form-item label="虚拟场景" prop="mapId">
          <el-select v-model="form.mapId" class="w-full" @change="onMapChange">
            <el-option v-for="m in mapOptions" :key="m.id" :label="m.mapName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="form.deviceName" placeholder="如：杆塔1" />
        </el-form-item>
        <el-form-item label="设备类型" prop="deviceType">
          <el-select v-model="form.deviceType" class="w-full">
            <el-option v-for="o in DEVICE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="经度">
          <el-input-number v-model="form.longitude" :controls="false" class="w-full" />
        </el-form-item>
        <el-form-item label="纬度">
          <el-input-number v-model="form.latitude" :controls="false" class="w-full" />
        </el-form-item>
        <el-form-item label="高度(m)">
          <el-input-number v-model="form.height" :controls="false" class="w-full" />
        </el-form-item>
        <el-form-item label="场景坐标">
          <div class="coord-row">
            <el-input-number v-model="form.sceneX" :controls="false" placeholder="X" />
            <el-input-number v-model="form.sceneY" :controls="false" placeholder="Y" />
            <el-input-number v-model="form.sceneZ" :controls="false" placeholder="Z" />
          </div>
        </el-form-item>
        <el-form-item label="场景对象ID">
          <el-input v-model="form.sceneObjectId" placeholder="仿真同步时自动填充" />
        </el-form-item>
        <el-form-item label="位置描述">
          <el-input v-model="form.locationDesc" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.field-device-page {
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}
.page-desc {
  margin: 0;
  color: #909399;
  font-size: 13px;
  max-width: 640px;
}
.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.filter-card {
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.text-muted {
  color: #c0c4cc;
}
.coord-row {
  display: flex;
  gap: 8px;
}
.w-full {
  width: 100%;
}
</style>
