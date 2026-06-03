<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  faultIngest,
  faultOfflineScenario,
  faultDisasterScenario,
  faultRecentAudits,
  type FaultAuditVO,
  type FaultHandleResult
} from '@/api/faultHandle'
import { fieldSceneDeviceByMap } from '@/api/fieldSceneDevice'
import type { UavRouteDispatchPayload } from '@/api/drone'
import { broadcastDispatchToSim } from '@/utils/inspectionBridge'

const LEVEL_TAG: Record<string, string> = {
  GENERAL: 'info',
  SERIOUS: 'warning',
  CRITICAL: 'danger'
}

const form = reactive({
  deviceId: undefined as number | undefined,
  terminalId: undefined as number | undefined,
  mapId: 1 as number | undefined,
  faultType: 'VISUAL_DEFECT',
  confidence: 0.82,
  description: '',
  /** 仅手动演示时开启；默认走 5 分钟去重 */
  skipDedupe: false
})

const submitting = ref(false)
const lastResult = ref<FaultHandleResult | null>(null)
const audits = ref<FaultAuditVO[]>([])
const devices = ref<{ id: number; deviceName?: string }[]>([])

const parseExpandCount = (row: FaultAuditVO & { detailJson?: string }) => {
  if (!row.detailJson) return '—'
  try {
    const d = JSON.parse(row.detailJson) as {
      dispatch?: { expandedDeviceCount?: number }
      expandedScope?: { relatedDeviceIds?: number[] }
    }
    const n = d.dispatch?.expandedDeviceCount
    if (n != null) return String(n)
    const rel = d.expandedScope?.relatedDeviceIds?.length ?? 0
    return rel > 0 ? String(rel + 1) : '—'
  } catch {
    return '—'
  }
}

const faultTypeOptions = [
  { value: 'VISUAL_DEFECT', label: '可见光缺陷' },
  { value: 'TEMP_ABNORMAL', label: '温度异常' },
  { value: 'VIBRATION', label: '振动异常' },
  { value: 'GAS_LEAK', label: '气体泄漏' },
  { value: 'FIRE', label: '火情' },
  { value: 'TERMINAL_OFFLINE', label: '终端离线' }
]

const loadDevices = async () => {
  if (!form.mapId) return
  try {
    const res: any = await fieldSceneDeviceByMap(form.mapId)
    devices.value = (res.data ?? []).map((d: any) => ({
      id: d.id,
      deviceName: d.deviceName || d.name
    }))
  } catch {
    devices.value = []
  }
}

const dispatchReinspectToSim = () => {
  const payload = lastResult.value?.dispatch?.routePayload as UavRouteDispatchPayload | undefined
  if (!payload?.waypoints?.length && !payload?.photoWaypoints?.length) {
    ElMessage.warning('最近处置结果无仿真航线，请重新上报火情或确认 drone 已重启')
    return
  }
  broadcastDispatchToSim(payload, {
    autoStart: true,
    userInput: '故障分级页手动下发扩范围复巡',
    dispatchTaskId: lastResult.value?.dispatch?.reinspectTaskId
  })
  ElMessage.success('已通过广播向仿真页下发扩范围复巡（请确保仿真页已打开）')
}

const loadAudits = async () => {
  try {
    const res: any = await faultRecentAudits(30)
    audits.value = res.data ?? []
  } catch {
    audits.value = []
  }
}

const submitIngest = async () => {
  if (!form.faultType) {
    ElMessage.warning('请选择故障类型')
    return
  }
  submitting.value = true
  try {
    const res: any = await faultIngest({
      deviceId: form.deviceId,
      terminalId: form.terminalId,
      mapId: form.mapId,
      faultType: form.faultType,
      confidence: form.confidence,
      description: form.description || undefined,
      skipDedupe: form.skipDedupe,
      extData: {
        simulation: form.skipDedupe,
        ...(form.faultType === 'TEMP_ABNORMAL' ? { maxTemp: 125 } : {})
      }
    })
    lastResult.value = res.data
    ElMessage.success(res.data?.message || '已提交分级处理')
    if (res.data?.dispatch?.routePayload) {
      broadcastDispatchToSim(res.data.dispatch.routePayload, {
        autoStart: true,
        userInput: '故障上报自动扩范围复巡',
        dispatchTaskId: res.data.dispatch.reinspectTaskId
      })
      ElMessage.info('已向仿真页广播扩范围复巡航线')
    }
    await loadAudits()
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

const testOffline = async () => {
  if (!form.terminalId) {
    ElMessage.warning('请填写发现异常的终端 ID')
    return
  }
  submitting.value = true
  try {
    const res: any = await faultOfflineScenario(form.terminalId, 60)
    lastResult.value = res.data
    ElMessage.success('离线场景已触发')
    await loadAudits()
  } finally {
    submitting.value = false
  }
}

const testDisaster = async () => {
  submitting.value = true
  try {
    const res: any = await faultDisasterScenario({
      mapId: form.mapId,
      alertType: 'STORM',
      description: '仿真环境灾害告警',
      severity: 0.98
    })
    lastResult.value = res.data
    ElMessage.success('灾害场景已触发')
    await loadAudits()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadDevices()
  await loadAudits()
})
</script>

<template>
  <div class="fault-grade-page">
    <el-card shadow="never" class="mb-16">
      <template #header>
        <span>故障事件上报（分级处理入口）</span>
      </template>
      <el-form :model="form" label-width="120px" class="form-grid">
        <el-form-item label="场景 mapId">
          <el-input-number v-model="form.mapId" :min="1" @change="loadDevices" />
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="form.deviceId" clearable filterable placeholder="选择设备" style="width: 100%">
            <el-option
              v-for="d in devices"
              :key="d.id"
              :label="`${d.deviceName || '设备'} (#${d.id})`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="终端 ID">
          <el-input-number v-model="form.terminalId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="故障类型">
          <el-select v-model="form.faultType" style="width: 100%">
            <el-option v-for="o in faultTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="AI 置信度">
          <el-slider v-model="form.confidence" :min="0" :max="1" :step="0.01" show-input />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="异常现象描述" />
        </el-form-item>
        <el-form-item label="演示模式">
          <el-switch v-model="form.skipDedupe" active-text="跳过去重（仅调试）" inactive-text="5 分钟内同异常不重复（默认）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitIngest">上报并分级处置</el-button>
          <el-button :loading="submitting" @click="testOffline">模拟终端离线</el-button>
          <el-button type="danger" :loading="submitting" @click="testDisaster">模拟环境灾害</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" class="mb-16">
      <el-col :span="12">
        <el-card v-if="lastResult" shadow="never">
          <template #header>最近处置结果</template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="事件 ID">{{ lastResult.eventId }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ lastResult.status }}</el-descriptions-item>
            <el-descriptions-item label="定级">
              <el-tag v-if="lastResult.level" :type="LEVEL_TAG[lastResult.level] || 'info'">
                {{ lastResult.level }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="处置动作">{{ lastResult.plan?.action }}</el-descriptions-item>
            <el-descriptions-item label="复巡任务">{{ lastResult.dispatch?.reinspectTaskId }}</el-descriptions-item>
            <el-descriptions-item label="扩范围设备数">
              {{ lastResult.dispatch?.expandedDeviceCount ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="分配终端">{{ lastResult.dispatch?.assignedTerminalId }}</el-descriptions-item>
            <el-descriptions-item label="仿真航线">
              <el-tag v-if="lastResult.dispatch?.routePayload" type="success" size="small">已生成</el-tag>
              <el-tag v-else type="info" size="small">无</el-tag>
              <el-button
                v-if="lastResult.dispatch?.routePayload"
                link
                type="primary"
                style="margin-left: 8px"
                @click="dispatchReinspectToSim"
              >
                下发仿真复巡
              </el-button>
            </el-descriptions-item>
            <el-descriptions-item label="说明">{{ lastResult.message }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>处置说明</template>
          <ul class="hint-list">
            <li>一般（GENERAL）：24h 内计划复巡，不扩范围</li>
            <li>严重（SERIOUS）：15 分钟内复巡，Neo4j 扩 2 层关联设备</li>
            <li>紧急（CRITICAL）：3 分钟内应急核查，输电火情扩至全线 5 基杆塔并自动下发仿真复巡</li>
            <li>生产环境：同设备同类型 5 分钟内重复上报将自动合并；演示模式请开启「跳过去重」</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span>分级处置审计（最近 30 条）</span>
        <el-button link type="primary" class="fr" @click="loadAudits">刷新</el-button>
      </template>
      <el-table :data="audits" stripe size="small">
        <el-table-column prop="handleTime" label="时间" width="170" />
        <el-table-column prop="eventId" label="事件 ID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="faultLevel" label="定级" width="100">
          <template #default="{ row }">
            <el-tag :type="LEVEL_TAG[row.faultLevel] || 'info'" size="small">{{ row.faultLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responseAction" label="动作" width="160" />
        <el-table-column prop="reinspectTaskId" label="复巡任务" min-width="140" show-overflow-tooltip />
        <el-table-column label="扩范围" width="72">
          <template #default="{ row }">
            {{ parseExpandCount(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="handleResult" label="结果" width="90" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.fault-grade-page {
  padding: 16px;
}
.mb-16 {
  margin-bottom: 16px;
}
.form-grid {
  max-width: 720px;
}
.hint-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.fr {
  float: right;
}
</style>
