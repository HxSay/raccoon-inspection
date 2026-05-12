<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cmmsCategoryList,
  cmmsCategorySave,
  cmmsCategoryDelete,
  cmmsSupplierPage,
  cmmsSupplierSave,
  cmmsSupplierDelete,
  cmmsDeptPage,
  cmmsDeptSave,
  cmmsDeptDelete,
  cmmsWarehousePage,
  cmmsWarehouseSave,
  cmmsWarehouseDelete,
  cmmsDevicePage,
  cmmsDeviceSave,
  cmmsDeviceDelete,
  cmmsPointList,
  cmmsPointSave,
  cmmsPointDelete,
  type DeviceCategory,
  type Supplier,
  type InspectionDept,
  type InspectionWarehouse,
  type DeviceInfo,
  type InspectionPoint
} from '@/api/cmms'

const activeTab = ref('device')

const deviceQuery = ref({ page: 1, size: 10, deviceName: '', deviceCode: '', status: undefined as number | undefined })
const deviceList = ref<DeviceInfo[]>([])
const deviceTotal = ref(0)
const deviceDialog = ref(false)
const deviceForm = ref<DeviceInfo>({
  deviceCode: '',
  deviceName: '',
  status: 1
})

const categoryList = ref<DeviceCategory[]>([])
const catDialog = ref(false)
const catForm = ref<DeviceCategory>({ categoryName: '', sort: 0 })

const supQuery = ref({ page: 1, size: 10, supplierName: '' })
const supList = ref<Supplier[]>([])
const supTotal = ref(0)
const supDialog = ref(false)
const supForm = ref<Supplier>({ supplierName: '' })

const deptQuery = ref({ page: 1, size: 10, deptName: '' })
const deptList = ref<InspectionDept[]>([])
const deptTotal = ref(0)
const deptDialog = ref(false)
const deptForm = ref<InspectionDept>({ deptName: '' })

const whQuery = ref({ page: 1, size: 10 })
const whList = ref<InspectionWarehouse[]>([])
const whTotal = ref(0)
const whDialog = ref(false)
const whForm = ref<InspectionWarehouse>({ warehouseName: '' })

const pointDialog = ref(false)
const pointDeviceId = ref<number>(0)
const pointList = ref<InspectionPoint[]>([])
const pointForm = ref<InspectionPoint>({ deviceId: 0, pointName: '', pointType: 1 })

const deviceStatus = [
  { label: '正常运行', value: 1 },
  { label: '待维护', value: 2 },
  { label: '故障停机', value: 3 },
  { label: '闲置', value: 4 },
  { label: '报废', value: 5 }
]

const loadDevices = async () => {
  const res: any = await cmmsDevicePage(deviceQuery.value)
  deviceList.value = res.data.records
  deviceTotal.value = res.data.total
}

const loadCategories = async () => {
  const res: any = await cmmsCategoryList()
  categoryList.value = res.data
}

const loadSuppliers = async () => {
  const res: any = await cmmsSupplierPage(supQuery.value)
  supList.value = res.data.records
  supTotal.value = res.data.total
}

const loadDepts = async () => {
  const res: any = await cmmsDeptPage(deptQuery.value)
  deptList.value = res.data.records
  deptTotal.value = res.data.total
}

const loadWarehouses = async () => {
  const res: any = await cmmsWarehousePage(whQuery.value)
  whList.value = res.data.records
  whTotal.value = res.data.total
}

onMounted(() => loadDevices())

watch(activeTab, (name) => {
  if (name === 'device') loadDevices()
  if (name === 'category') loadCategories()
  if (name === 'supplier') loadSuppliers()
  if (name === 'dept') loadDepts()
  if (name === 'warehouse') loadWarehouses()
})

const openDevice = (row?: DeviceInfo) => {
  deviceForm.value = row
    ? { ...row }
    : { deviceCode: '', deviceName: '', status: 1, inspectionCycle: 7, maintenanceCycle: 30 }
  deviceDialog.value = true
}

const saveDevice = async () => {
  await cmmsDeviceSave(deviceForm.value)
  ElMessage.success('保存成功')
  deviceDialog.value = false
  loadDevices()
}

const delDevice = async (id: number) => {
  await ElMessageBox.confirm('确认删除该设备？')
  await cmmsDeviceDelete(id)
  ElMessage.success('已删除')
  loadDevices()
}

const openCat = (row?: DeviceCategory) => {
  catForm.value = row ? { ...row } : { categoryName: '', sort: 0 }
  catDialog.value = true
}

const saveCat = async () => {
  await cmmsCategorySave(catForm.value)
  ElMessage.success('保存成功')
  catDialog.value = false
  loadCategories()
}

const delCat = async (id: number) => {
  await ElMessageBox.confirm('确认删除？')
  await cmmsCategoryDelete(id)
  loadCategories()
}

const openSup = (row?: Supplier) => {
  supForm.value = row ? { ...row } : { supplierName: '' }
  supDialog.value = true
}

const saveSup = async () => {
  await cmmsSupplierSave(supForm.value)
  supDialog.value = false
  loadSuppliers()
}

const delSup = async (id: number) => {
  await ElMessageBox.confirm('确认删除？')
  await cmmsSupplierDelete(id)
  loadSuppliers()
}

const openDept = (row?: InspectionDept) => {
  deptForm.value = row ? { ...row } : { deptName: '' }
  deptDialog.value = true
}

const saveDept = async () => {
  await cmmsDeptSave(deptForm.value)
  deptDialog.value = false
  loadDepts()
}

const delDept = async (id: number) => {
  await ElMessageBox.confirm('确认删除？')
  await cmmsDeptDelete(id)
  loadDepts()
}

const openWh = (row?: InspectionWarehouse) => {
  whForm.value = row ? { ...row } : { warehouseName: '' }
  whDialog.value = true
}

const saveWh = async () => {
  await cmmsWarehouseSave(whForm.value)
  whDialog.value = false
  loadWarehouses()
}

const delWh = async (id: number) => {
  await ElMessageBox.confirm('确认删除？')
  await cmmsWarehouseDelete(id)
  loadWarehouses()
}

const openPoints = async (deviceId: number) => {
  pointDeviceId.value = deviceId
  const res: any = await cmmsPointList(deviceId)
  pointList.value = res.data
  pointForm.value = { deviceId, pointName: '', pointType: 1, sort: 0 }
  pointDialog.value = true
}

const savePoint = async () => {
  pointForm.value.deviceId = pointDeviceId.value
  await cmmsPointSave(pointForm.value)
  ElMessage.success('保存成功')
  pointForm.value = { deviceId: pointDeviceId.value, pointName: '', pointType: 1, sort: 0 }
  const res: any = await cmmsPointList(pointDeviceId.value)
  pointList.value = res.data
}

const delPoint = async (id: number) => {
  await cmmsPointDelete(id)
  await openPoints(pointDeviceId.value)
}
</script>

<template>
  <div class="page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="设备台账" name="device">
        <el-form :inline="true" class="toolbar">
          <el-form-item label="名称">
            <el-input v-model="deviceQuery.deviceName" clearable placeholder="设备名称" />
          </el-form-item>
          <el-form-item label="编号">
            <el-input v-model="deviceQuery.deviceCode" clearable placeholder="设备编号" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="deviceQuery.status" clearable placeholder="全部" style="width: 140px">
              <el-option v-for="o in deviceStatus" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="() => { deviceQuery.page = 1; loadDevices() }">查询</el-button>
            <el-button @click="deviceQuery = { page: 1, size: 10, deviceName: '', deviceCode: '', status: undefined }; loadDevices()">重置</el-button>
            <el-button type="success" @click="openDevice()">新增设备</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="deviceList" border stripe>
          <el-table-column prop="deviceCode" label="设备编号" width="120" />
          <el-table-column prop="deviceName" label="设备名称" min-width="140" />
          <el-table-column prop="model" label="型号" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              {{ deviceStatus.find((s) => s.value === row.status)?.label || row.status }}
            </template>
          </el-table-column>
          <el-table-column prop="location" label="位置" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDevice(row)">编辑</el-button>
              <el-button link type="primary" @click="openPoints(row.id)">巡检点</el-button>
              <el-button link type="danger" @click="delDevice(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="deviceQuery.page"
          v-model:page-size="deviceQuery.size"
          :total="deviceTotal"
          layout="total, prev, pager, next"
          @current-change="loadDevices"
          @size-change="loadDevices"
          class="pager"
        />
      </el-tab-pane>

      <el-tab-pane label="设备分类" name="category">
        <el-button type="primary" class="mb" @click="openCat()">新增分类</el-button>
        <el-table :data="categoryList" border stripe>
          <el-table-column prop="categoryName" label="分类名称" />
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCat(row)">编辑</el-button>
              <el-button link type="danger" @click="delCat(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="供应商" name="supplier">
        <el-form :inline="true">
          <el-form-item label="名称">
            <el-input v-model="supQuery.supplierName" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="() => { supQuery.page = 1; loadSuppliers() }">查询</el-button>
            <el-button type="success" @click="openSup()">新增</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="supList" border stripe>
          <el-table-column prop="supplierName" label="供应商" />
          <el-table-column prop="contactPerson" label="联系人" width="120" />
          <el-table-column prop="contactPhone" label="电话" width="130" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openSup(row)">编辑</el-button>
              <el-button link type="danger" @click="delSup(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="supQuery.page"
          v-model:page-size="supQuery.size"
          :total="supTotal"
          layout="total, prev, pager, next"
          @current-change="loadSuppliers"
        />
      </el-tab-pane>

      <el-tab-pane label="部门" name="dept">
        <el-form :inline="true">
          <el-form-item label="部门名">
            <el-input v-model="deptQuery.deptName" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="() => { deptQuery.page = 1; loadDepts() }">查询</el-button>
            <el-button type="success" @click="openDept()">新增</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="deptList" border stripe>
          <el-table-column prop="deptName" label="部门名称" />
          <el-table-column prop="leaderUserId" label="负责人用户ID" width="140" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDept(row)">编辑</el-button>
              <el-button link type="danger" @click="delDept(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="deptQuery.page"
          v-model:page-size="deptQuery.size"
          :total="deptTotal"
          layout="total, prev, pager, next"
          @current-change="loadDepts"
        />
      </el-tab-pane>

      <el-tab-pane label="仓库" name="warehouse">
        <el-button type="success" class="mb" @click="openWh()">新增仓库</el-button>
        <el-table :data="whList" border stripe>
          <el-table-column prop="warehouseName" label="仓库名称" />
          <el-table-column prop="location" label="位置" />
          <el-table-column prop="managerUserId" label="管理员用户ID" width="140" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openWh(row)">编辑</el-button>
              <el-button link type="danger" @click="delWh(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="whQuery.page"
          v-model:page-size="whQuery.size"
          :total="whTotal"
          layout="total, prev, pager, next"
          @current-change="loadWarehouses"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="deviceDialog" title="设备" width="640px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="设备编号" required>
          <el-input v-model="deviceForm.deviceCode" :disabled="!!deviceForm.id" />
        </el-form-item>
        <el-form-item label="设备名称" required>
          <el-input v-model="deviceForm.deviceName" />
        </el-form-item>
        <el-form-item label="型号"><el-input v-model="deviceForm.model" /></el-form-item>
        <el-form-item label="序列号"><el-input v-model="deviceForm.serialNumber" /></el-form-item>
        <el-form-item label="分类ID"><el-input-number v-model="deviceForm.categoryId" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="部门ID"><el-input-number v-model="deviceForm.deptId" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="责任人用户ID"><el-input-number v-model="deviceForm.managerUserId" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="供应商ID"><el-input-number v-model="deviceForm.supplierId" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="安装位置"><el-input v-model="deviceForm.location" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="deviceForm.status" style="width: 100%">
            <el-option v-for="o in deviceStatus" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="巡检周期(天)"><el-input-number v-model="deviceForm.inspectionCycle" :min="1" /></el-form-item>
        <el-form-item label="保养周期(天)"><el-input-number v-model="deviceForm.maintenanceCycle" :min="1" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="deviceForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deviceDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDevice">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="catDialog" title="设备分类" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="catForm.categoryName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="catForm.sort" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="catForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCat">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="supDialog" title="供应商" width="520px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="supForm.supplierName" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="supForm.contactPerson" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="supForm.contactPhone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="supForm.address" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="supDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSup">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deptDialog" title="部门" width="480px">
      <el-form label-width="100px">
        <el-form-item label="部门名称" required><el-input v-model="deptForm.deptName" /></el-form-item>
        <el-form-item label="父部门ID"><el-input-number v-model="deptForm.parentId" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="负责人用户ID"><el-input-number v-model="deptForm.leaderUserId" :min="0" controls-position="right" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDept">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="whDialog" title="仓库" width="480px">
      <el-form label-width="90px">
        <el-form-item label="仓库名称" required><el-input v-model="whForm.warehouseName" /></el-form-item>
        <el-form-item label="位置"><el-input v-model="whForm.location" /></el-form-item>
        <el-form-item label="管理员用户ID"><el-input-number v-model="whForm.managerUserId" :min="0" controls-position="right" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="whDialog = false">取消</el-button>
        <el-button type="primary" @click="saveWh">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pointDialog" title="巡检点" width="720px" destroy-on-close>
      <el-table :data="pointList" size="small" border class="mb">
        <el-table-column prop="pointName" label="点名称" />
        <el-table-column prop="pointType" label="类型" width="90">
          <template #default="{ row }">{{ row.pointType === 2 ? '自动' : '手动' }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="delPoint(row.id)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider>新增巡检点</el-divider>
      <el-form :inline="true">
        <el-form-item label="名称"><el-input v-model="pointForm.pointName" placeholder="如 主轴温度" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="pointForm.pointType" style="width: 100px">
            <el-option :value="1" label="手动" />
            <el-option :value="2" label="自动" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位"><el-input v-model="pointForm.unit" placeholder="℃" style="width: 80px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="savePoint">添加</el-button></el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
}
.mb {
  margin-bottom: 12px;
}
</style>
