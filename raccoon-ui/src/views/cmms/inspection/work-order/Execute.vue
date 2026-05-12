<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  iwoDetail,
  iwoStepExecute,
  iwoStepCollect,
  iwoScanDevice,
  type IwoFull,
  type IwoStepVO
} from '@/api/inspectionWorkOrder'

const route = useRoute()
const router = useRouter()
const orderId = computed(() => Number(route.params.orderId))

const detail = ref<IwoFull | null>(null)
const loading = ref(false)

const collectForm = ref({ actualValue: '', photoUrl: '', remark: '' })
const scanCode = ref('')

const currentStep = computed(() => {
  const steps = detail.value?.steps || []
  return steps.find((s) => !isStepDone(s)) || null
})

function isStepDone(s: IwoStepVO) {
  if (s.type === 'collect') {
    return !!(s.actualValue && String(s.actualValue).trim())
  }
  return !!s.collectTime
}

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

const doExecute = async () => {
  const cur = currentStep.value
  if (!cur) {
    ElMessage.success('已全部完成')
    return
  }
  if (cur.type === 'collect') {
    ElMessage.info('采集步骤请填写实测值后提交采集')
    return
  }
  await iwoStepExecute({ detailId: cur.id, remark: collectForm.value.remark || undefined })
  ElMessage.success('步骤已完成')
  collectForm.value = { actualValue: '', photoUrl: '', remark: '' }
  await load()
}

const doCollect = async () => {
  const cur = currentStep.value
  if (!cur || cur.type !== 'collect') {
    ElMessage.warning('当前不是采集步骤')
    return
  }
  if (!collectForm.value.actualValue.trim()) {
    ElMessage.warning('请填写实测值')
    return
  }
  await iwoStepCollect({
    detailId: cur.id,
    actualValue: collectForm.value.actualValue,
    photoUrl: collectForm.value.photoUrl || undefined,
    remark: collectForm.value.remark || undefined
  })
  ElMessage.success('采集已保存')
  collectForm.value = { actualValue: '', photoUrl: '', remark: '' }
  await load()
}

const doScan = async () => {
  const cur = currentStep.value
  if (!cur || cur.type !== 'stop') {
    ElMessage.warning('当前不是停靠步骤')
    return
  }
  if (!scanCode.value.trim()) {
    ElMessage.warning('请输入或扫描设备编号')
    return
  }
  await iwoScanDevice({ detailId: cur.id, deviceCode: scanCode.value.trim() })
  ElMessage.success('设备已绑定')
  scanCode.value = ''
  await load()
}

const outOfRange = (s: IwoStepVO) => {
  if (s.type !== 'collect' || !collectForm.value.actualValue) return false
  const v = parseFloat(collectForm.value.actualValue.replace(/[^\d.-]/g, ''))
  if (Number.isNaN(v)) return false
  if (s.standardMin != null && v < s.standardMin) return true
  if (s.standardMax != null && v > s.standardMax) return true
  return false
}
</script>

<template>
  <div class="page" v-loading="loading">
    <el-page-header @back="router.push({ path: '/cmms/inspection', query: { tab: 'order' } })" content="巡检执行" />

    <template v-if="detail">
      <el-descriptions :column="2" border class="block" title="工单概要">
        <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="区域">{{ detail.area }}</el-descriptions-item>
        <el-descriptions-item label="巡检人">{{ detail.inspectorName }}</el-descriptions-item>
      </el-descriptions>

      <el-card v-if="currentStep" class="block" shadow="never">
        <template #header>
          <span>当前步骤 #{{ currentStep.stepOrder }} — {{ currentStep.type }}</span>
        </template>
        <p class="muted">{{ currentStep.description }}</p>
        <p v-if="currentStep.target"><strong>目标：</strong>{{ currentStep.target }}</p>
        <p v-if="currentStep.deviceName"><strong>设备：</strong>{{ currentStep.deviceName }}（{{ currentStep.deviceCode || '未绑定' }}）</p>

        <template v-if="currentStep.type === 'stop'">
          <el-divider />
          <el-input v-model="scanCode" placeholder="扫描或输入设备编号 deviceCode" clearable style="max-width: 360px" />
          <el-button type="primary" style="margin-left: 8px" @click="doScan">绑定设备</el-button>
          <el-divider />
          <el-button type="success" @click="doExecute">完成停靠</el-button>
        </template>

        <template v-else-if="currentStep.type === 'collect'">
          <el-divider />
          <div v-if="currentStep.checkItem" class="row">
            <span>检测项：{{ currentStep.checkItem }}</span>
            <span class="gap">标准：{{ currentStep.standardMin }} ~ {{ currentStep.standardMax }} {{ currentStep.unit || '' }}</span>
            <span v-if="currentStep.detectionMethod">方式：{{ currentStep.detectionMethod }}</span>
          </div>
          <el-form label-width="100px" style="max-width: 520px; margin-top: 12px">
            <el-form-item label="实测值" required>
              <el-input v-model="collectForm.actualValue" :class="{ danger: outOfRange(currentStep) }" />
              <span v-if="outOfRange(currentStep)" class="danger-text">超出标准范围（前端提示，以后端判定为准）</span>
            </el-form-item>
            <el-form-item label="照片URL">
              <el-input v-model="collectForm.photoUrl" placeholder="可填图片地址，多个用英文逗号分隔" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="collectForm.remark" type="textarea" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="doCollect">提交采集</el-button>
            </el-form-item>
          </el-form>
        </template>

        <template v-else>
          <el-divider />
          <el-button type="success" @click="doExecute">完成本步</el-button>
        </template>
      </el-card>

      <el-card v-else class="block" shadow="never">
        <el-result icon="success" title="本工单步骤已全部完成" />
        <el-button type="primary" @click="router.push(`/cmms/inspection/work-order/detail/${orderId}`)">查看结果详情</el-button>
      </el-card>

      <el-card class="block" shadow="never">
        <template #header>步骤总览</template>
        <el-timeline>
          <el-timeline-item
            v-for="s in detail.steps"
            :key="s.id"
            :type="isStepDone(s) ? 'success' : s.id === currentStep?.id ? 'warning' : 'info'"
            :timestamp="'#' + s.stepOrder"
          >
            <strong>{{ s.type }}</strong>
            {{ s.description }}
            <span v-if="s.type === 'collect' && s.actualValue"> — 实测 {{ s.actualValue }}</span>
            <el-tag v-if="s.isException" type="danger" size="small" style="margin-left: 6px">异常</el-tag>
          </el-timeline-item>
        </el-timeline>
      </el-card>
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
.muted {
  color: #909399;
}
.row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.gap {
  margin-left: 12px;
}
.danger :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
.danger-text {
  color: #f56c6c;
  margin-left: 8px;
  font-size: 13px;
}
</style>
