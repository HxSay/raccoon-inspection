<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveWorkOrder,
  getAuditOrderDetail,
  listPendingAuditOrders,
  rejectWorkOrder,
  type WorkOrderAuditDetailDTO
} from '@/api/workOrderAudit'

const loading = ref(false)
const orders = ref<Array<{ id: number; orderNo: string; area: string; auditDeadline?: string }>>([])
const selectedId = ref<number | null>(null)
const detail = ref<WorkOrderAuditDetailDTO | null>(null)
const rejectReason = ref('')

const loadList = async () => {
  loading.value = true
  try {
    const res: any = await listPendingAuditOrders({ page: 1, size: 50 })
    const records = res.data?.records ?? []
    orders.value = records.map((o: any) => ({
      id: o.id,
      orderNo: o.orderNo,
      area: o.area,
      auditDeadline: o.auditDeadline
    }))
    if (orders.value.length && !selectedId.value) {
      await selectOrder(orders.value[0].id)
    }
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载待审列表失败')
  } finally {
    loading.value = false
  }
}

const selectOrder = async (id: number) => {
  selectedId.value = id
  rejectReason.value = ''
  try {
    const res: any = await getAuditOrderDetail(id)
    detail.value = res.data
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载详情失败')
  }
}

const onApprove = async () => {
  if (!selectedId.value) return
  await ElMessageBox.confirm('确认审核通过并正式下发巡检任务？', '审核通过')
  try {
    await approveWorkOrder({ workOrderId: selectedId.value })
    ElMessage.success('审核通过')
    detail.value = null
    selectedId.value = null
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '审核失败')
  }
}

const onReject = async () => {
  if (!selectedId.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await rejectWorkOrder({ workOrderId: selectedId.value, rejectReason: rejectReason.value.trim() })
    ElMessage.success('已驳回，系统将重新规划')
    detail.value = null
    selectedId.value = null
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '驳回失败')
  }
}

onMounted(loadList)
</script>

<template>
  <div class="audit-page">
    <h2>巡检工单审核（移动端）</h2>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card v-loading="loading" shadow="never">
          <template #header>待审核 ({{ orders.length }})</template>
          <el-empty v-if="!orders.length" description="暂无待审工单" />
          <el-menu v-else :default-active="String(selectedId)">
            <el-menu-item
              v-for="o in orders"
              :key="o.id"
              :index="String(o.id)"
              @click="selectOrder(o.id)"
            >
              <div class="order-item">
                <div>{{ o.orderNo }}</div>
                <div class="sub">{{ o.area }}</div>
              </div>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card v-if="detail" shadow="never">
          <template #header>
            {{ detail.orderNo }} · {{ detail.area }}
            <el-tag type="warning" style="margin-left: 8px">待审核</el-tag>
          </template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="调度任务">{{ detail.dispatchTaskId }}</el-descriptions-item>
            <el-descriptions-item label="分配终端">{{ detail.terminalName }}</el-descriptions-item>
            <el-descriptions-item label="优先级">{{ detail.priorityCode }}</el-descriptions-item>
            <el-descriptions-item label="审核截止">{{ detail.auditDeadline }}</el-descriptions-item>
            <el-descriptions-item label="分配理由" :span="2">{{ detail.assignReason }}</el-descriptions-item>
          </el-descriptions>
          <h4 style="margin-top: 16px">标准巡检步骤</h4>
          <el-table :data="detail.steps ?? []" size="small" border>
            <el-table-column prop="stepOrder" label="#" width="50" />
            <el-table-column prop="type" label="类型" width="80" />
            <el-table-column prop="deviceName" label="设备" />
            <el-table-column prop="checkItem" label="检测项" />
            <el-table-column prop="standardRange" label="标准范围" />
            <el-table-column prop="description" label="说明" show-overflow-tooltip />
          </el-table>
          <div class="actions">
            <el-button type="primary" @click="onApprove">审核通过</el-button>
            <el-input
              v-model="rejectReason"
              placeholder="驳回原因"
              style="max-width: 280px; margin: 0 8px"
            />
            <el-button type="danger" plain @click="onReject">驳回</el-button>
          </div>
        </el-card>
        <el-empty v-else description="请选择待审工单" />
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.audit-page {
  padding: 16px;
}
.order-item .sub {
  font-size: 12px;
  color: #888;
}
.actions {
  margin-top: 20px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
