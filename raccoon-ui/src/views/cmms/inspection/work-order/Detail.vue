<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import { iwoDetail, type IwoFull } from '@/api/inspectionWorkOrder'

const route = useRoute()
const router = useRouter()
const orderId = computed(() => Number(route.params.orderId))

const isMobilePatrol = computed(() => route.path.startsWith('/m/'))

const patrolBackTarget = (): RouteLocationRaw => {
  if (isMobilePatrol.value) {
    return '/m/patrol'
  }
  return { path: '/cmms/inspection', query: { tab: 'order' } }
}

const patrolExecutePath = (id: number) =>
  isMobilePatrol.value ? `/m/execute/${id}` : `/cmms/inspection/work-order/execute/${id}`

const detail = ref<IwoFull | null>(null)
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const res: any = await iwoDetail(orderId.value)
    detail.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(load)

const typeLabel: Record<string, string> = {
  path: '路径',
  stop: '停靠',
  collect: '采集',
  report: '上报'
}
</script>

<template>
  <div class="page" v-loading="loading">
    <el-page-header @back="router.push(patrolBackTarget())" content="巡检结果详情" />

    <template v-if="detail">
      <el-descriptions :column="2" border class="block" title="工单信息">
        <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="计划ID">{{ detail.planId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务ID">{{ detail.taskId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="区域">{{ detail.area }}</el-descriptions-item>
        <el-descriptions-item label="班次">{{ detail.shiftType }}</el-descriptions-item>
        <el-descriptions-item label="巡检人">{{ detail.inspectorName }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createByName }}</el-descriptions-item>
        <el-descriptions-item label="计划">{{ detail.planStartTime }} ~ {{ detail.planEndTime }}</el-descriptions-item>
        <el-descriptions-item label="实际">{{ detail.actualStartTime || '-' }} ~ {{ detail.actualEndTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-card class="block" shadow="never">
        <template #header>步骤与采集明细</template>
        <el-table :data="detail.steps" border stripe style="width: 100%">
          <el-table-column prop="stepOrder" label="#" width="50" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ typeLabel[row.type] || row.type }}</template>
          </el-table-column>
          <el-table-column prop="target" label="目标" min-width="100" show-overflow-tooltip />
          <el-table-column prop="description" label="说明" min-width="120" show-overflow-tooltip />
          <el-table-column label="设备" min-width="140">
            <template #default="{ row }">
              {{ row.deviceName || '-' }} / {{ row.deviceCode || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="checkItem" label="检测项" width="120" />
          <el-table-column label="标准值" width="130">
            <template #default="{ row }">
              {{ row.standardMin }} ~ {{ row.standardMax }} {{ row.unit || '' }}
            </template>
          </el-table-column>
          <el-table-column prop="actualValue" label="实测" width="100" />
          <el-table-column label="异常" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.isException" type="danger" size="small">异常</el-tag>
              <span v-else>正常</span>
            </template>
          </el-table-column>
          <el-table-column prop="collectTime" label="采集时间" min-width="160" />
          <el-table-column prop="photoUrl" label="照片" min-width="120" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
        </el-table>
      </el-card>

      <el-button type="primary" style="margin-top: 12px" @click="router.push(patrolExecutePath(orderId))"
        >返回执行</el-button
      >
    </template>
  </div>
</template>

<style scoped>
.page {
  padding: 8px;
}
.block {
  margin-top: 12px;
}
</style>
