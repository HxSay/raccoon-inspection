<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cmmsWoPage, cmmsWoSave, cmmsWoDelete, cmmsWoStatus, type WorkOrder } from '@/api/cmms'

const query = ref({ page: 1, size: 10, orderCode: '', status: undefined as number | undefined, deviceId: undefined as number | undefined })
const list = ref<WorkOrder[]>([])
const total = ref(0)
const dialog = ref(false)
const form = ref<WorkOrder>({
  deviceId: 1,
  source: 2,
  orderType: 1,
  level: 2,
  faultDescription: ''
})

const sourceOpt = [
  { label: '巡检上报', value: 1 },
  { label: '手动报修', value: 2 },
  { label: '故障预警', value: 3 }
]
const typeOpt = [
  { label: '故障维修', value: 1 },
  { label: '预防性保养', value: 2 }
]
const levelOpt = [
  { label: '紧急', value: 1 },
  { label: '普通', value: 2 },
  { label: '低', value: 3 }
]
const statusOpt = [
  { label: '待处理', value: 0 },
  { label: '处理中', value: 1 },
  { label: '待验收', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已取消', value: 4 }
]

const load = async () => {
  const res: any = await cmmsWoPage(query.value)
  list.value = res.data.records
  total.value = res.data.total
}

onMounted(load)

const open = (row?: WorkOrder) => {
  form.value = row
    ? { ...row }
    : { deviceId: 1, source: 2, orderType: 1, level: 2, faultDescription: '', status: 0 }
  dialog.value = true
}

const save = async () => {
  await cmmsWoSave(form.value)
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

const del = async (id: number) => {
  await ElMessageBox.confirm('确认删除工单？')
  await cmmsWoDelete(id)
  load()
}

const setStatus = async (id: number, status: number) => {
  await cmmsWoStatus(id, status)
  ElMessage.success('状态已更新')
  load()
}
</script>

<template>
  <div class="page">
    <el-form :inline="true">
      <el-form-item label="工单号">
        <el-input v-model="query.orderCode" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
          <el-option v-for="o in statusOpt" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="设备ID">
        <el-input-number v-model="query.deviceId" :min="0" controls-position="right" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="() => { query.page = 1; load() }">查询</el-button>
        <el-button type="success" @click="open()">新建工单</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" border stripe>
      <el-table-column prop="orderCode" label="工单号" width="170" />
      <el-table-column prop="deviceId" label="设备ID" width="90" />
      <el-table-column label="来源" width="100">
        <template #default="{ row }">{{ sourceOpt.find((s) => s.value === row.source)?.label }}</template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ typeOpt.find((s) => s.value === row.orderType)?.label }}</template>
      </el-table-column>
      <el-table-column prop="faultDescription" label="故障描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ statusOpt.find((s) => s.value === row.status)?.label }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button v-if="row.status === 0" link type="success" @click="setStatus(row.id!, 1)">受理</el-button>
          <el-button v-if="row.status === 1" link type="warning" @click="setStatus(row.id!, 2)">待验收</el-button>
          <el-button v-if="row.status === 2" link type="success" @click="setStatus(row.id!, 3)">完成</el-button>
          <el-button link type="danger" @click="del(row.id!)">删</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
    />

    <el-dialog v-model="dialog" title="维修工单" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="设备ID" required><el-input-number v-model="form.deviceId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source" style="width: 100%">
            <el-option v-for="o in sourceOpt" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单类型">
          <el-select v-model="form.orderType" style="width: 100%">
            <el-option v-for="o in typeOpt" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-select v-model="form.level" style="width: 100%">
            <el-option v-for="o in levelOpt" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="指派用户ID"><el-input-number v-model="form.assignUserId" :min="0" controls-position="right" style="width: 100%" /></el-form-item>
        <el-form-item label="故障描述"><el-input v-model="form.faultDescription" type="textarea" /></el-form-item>
        <el-form-item label="维修内容"><el-input v-model="form.repairContent" type="textarea" /></el-form-item>
        <el-form-item label="维修费用"><el-input-number v-model="form.repairCost" :min="0" :precision="2" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
</style>
