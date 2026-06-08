<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  closeLoopExportAudit,
  closeLoopIngest,
  closeLoopRecent,
  type CloseLoopAudit,
  type CloseLoopReport,
  type CloseLoopResult
} from '@/api/closeLoop'

const loading = ref(false)
const exporting = ref(false)
const audits = ref<CloseLoopAudit[]>([])

const STATUS_META: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  COMPLETED: { label: '已闭环', type: 'success' },
  PARTIAL: { label: '部分完成·已补检', type: 'warning' },
  MANUAL_REQUIRED: { label: '需人工介入', type: 'danger' },
  COLLECTING: { label: '结果回收中', type: 'info' }
}

function statusMeta(s?: string) {
  return STATUS_META[s ?? ''] ?? { label: s ?? '—', type: 'info' as const }
}

function ratePct(r?: number): number {
  return Math.round((r ?? 0) * 100)
}

function alertType(s?: string): 'success' | 'warning' | 'info' | 'error' {
  const type = statusMeta(s).type
  return type === 'danger' ? 'error' : type
}

async function loadAudits() {
  loading.value = true
  try {
    const res: any = await closeLoopRecent(50)
    audits.value = (res?.data ?? []) as CloseLoopAudit[]
  } catch (e: unknown) {
    ElMessage.error('加载闭环审计失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    loading.value = false
  }
}

async function exportAudits() {
  exporting.value = true
  try {
    const res: any = await closeLoopExportAudit(200)
    const blob = res?.data instanceof Blob ? res.data : new Blob([res?.data ?? ''], { type: 'application/pdf' })
    const disposition = String(res?.headers?.['content-disposition'] ?? '')
    const matched = disposition.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
    const fileName = matched?.[1] ? decodeURIComponent(matched[1]) : `close-loop-audit-${Date.now()}.pdf`
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('审计记录已导出')
  } catch (e: unknown) {
    ElMessage.error('导出闭环审计失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    exporting.value = false
  }
}

/* ------- 详情抽屉 ------- */
const detailVisible = ref(false)
const detailReport = ref<CloseLoopReport | null>(null)
const detailMissed = ref<Record<string, any> | null>(null)
const detailRow = ref<CloseLoopAudit | null>(null)

function openDetail(row: CloseLoopAudit) {
  detailRow.value = row
  detailReport.value = safeParse<CloseLoopReport>(row.reportJson)
  detailMissed.value = safeParse<Record<string, any>>(row.missedItemsJson)
  detailVisible.value = true
}

function safeParse<T>(json?: string): T | null {
  if (!json) return null
  try {
    return JSON.parse(json) as T
  } catch {
    return null
  }
}

/* ------- 手动闭环核对（演示/联调用） ------- */
const manual = reactive({
  taskId: '',
  mapId: 1,
  plannedWaypointCount: 5,
  finishedWaypointCount: 3,
  plannedDeviceIds: '',
  requiredDataTypes: 'VISIBLE',
  skipReschedule: false
})
const manualSubmitting = ref(false)
const manualResult = ref<CloseLoopResult | null>(null)

const manualDeviceIdList = computed(() =>
  manual.plannedDeviceIds
    .split(/[,，\s]+/)
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isFinite(n) && n > 0)
)

async function submitManual() {
  if (!manual.taskId.trim()) {
    ElMessage.warning('请填写任务 ID')
    return
  }
  manualSubmitting.value = true
  manualResult.value = null
  try {
    const captured = Array.from({ length: Math.max(0, manual.finishedWaypointCount) }, (_, i) => ({
      waypointIndex: i,
      dataTypes: manual.requiredDataTypes
        .split(/[,，\s]+/)
        .map((s) => s.trim().toUpperCase())
        .filter(Boolean)
    }))
    const res: any = await closeLoopIngest({
      taskId: manual.taskId.trim(),
      mapId: manual.mapId,
      plannedDeviceIds: manualDeviceIdList.value,
      plannedWaypointCount: manual.plannedWaypointCount,
      finishedWaypointCount: manual.finishedWaypointCount,
      requiredDataTypes: manual.requiredDataTypes
        .split(/[,，\s]+/)
        .map((s) => s.trim().toUpperCase())
        .filter(Boolean),
      capturedPoints: captured,
      skipReschedule: manual.skipReschedule
    })
    manualResult.value = (res?.data ?? null) as CloseLoopResult | null
    ElMessage.success('闭环核对完成')
    await loadAudits()
  } catch (e: unknown) {
    ElMessage.error('闭环核对失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    manualSubmitting.value = false
  }
}

onMounted(loadAudits)
</script>

<template>
  <div class="close-loop-page">
    <el-card shadow="never" class="intro-card">
      <template #header>
        <div class="card-header">
          <span class="title">任务规划 Agent · 智能巡检任务闭环</span>
          <div class="header-actions">
            <el-button :loading="exporting" size="small" type="primary" plain @click="exportAudits">导出审计</el-button>
            <el-button :loading="loading" size="small" @click="loadAudits">刷新</el-button>
          </div>
        </div>
      </template>
      <div class="intro">
        任务执行末端的统一收口能力：
        <el-tag size="small" type="info" effect="plain">① 多机结果回收</el-tag>
        <el-tag size="small" type="info" effect="plain">② 完整性核对</el-tag>
        <el-tag size="small" type="info" effect="plain">③ 报告自动生成</el-tag>
        <el-tag size="small" type="info" effect="plain">④ 数据分层归档</el-tag>
        <el-tag size="small" type="info" effect="plain">⑤ 未完成项重调度</el-tag>
        <el-tag size="small" type="info" effect="plain">⑥ 工单闭环</el-tag>
        <el-tag size="small" type="info" effect="plain">⑦ 审计导出</el-tag>
        <p class="desc">
          边缘巡检任务完成后自动回收多机结果，对照计划快照核对点位/设备/多模态数据完整性；达标即闭环归档，
          存在漏检或数据缺口时自动生成补检任务并下发，确保巡检任务 100% 闭环可追溯。
        </p>
      </div>
    </el-card>

    <el-card shadow="never" class="manual-card">
      <template #header>
        <span class="title">手动闭环核对（演示 / 联调）</span>
      </template>
      <el-form :model="manual" inline label-width="92px" class="manual-form">
        <el-form-item label="任务 ID">
          <el-input v-model="manual.taskId" placeholder="dispatchTaskId" style="width: 200px" />
        </el-form-item>
        <el-form-item label="地图 ID">
          <el-input-number v-model="manual.mapId" :min="1" controls-position="right" style="width: 120px" />
        </el-form-item>
        <el-form-item label="计划航点">
          <el-input-number v-model="manual.plannedWaypointCount" :min="0" controls-position="right" style="width: 120px" />
        </el-form-item>
        <el-form-item label="完成航点">
          <el-input-number v-model="manual.finishedWaypointCount" :min="0" controls-position="right" style="width: 120px" />
        </el-form-item>
        <el-form-item label="计划设备ID">
          <el-input v-model="manual.plannedDeviceIds" placeholder="如 1,2,3,4,5" style="width: 200px" />
        </el-form-item>
        <el-form-item label="要求模态">
          <el-input v-model="manual.requiredDataTypes" placeholder="VISIBLE,THERMAL" style="width: 180px" />
        </el-form-item>
        <el-form-item label="跳过补检">
          <el-switch v-model="manual.skipReschedule" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="manualSubmitting" @click="submitManual">触发闭环</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="manualResult"
        :title="`闭环判定：${statusMeta(manualResult.closeStatus).label} · 完成率 ${ratePct(manualResult.completionRate)}%`"
        :type="alertType(manualResult.closeStatus)"
        :closable="false"
        show-icon
      >
        <div class="manual-result">
          <p>{{ manualResult.report?.executiveSummary ?? manualResult.message }}</p>
          <p v-if="manualResult.rescheduled">
            已生成补检任务：<el-tag size="small" type="warning">{{ manualResult.reinspectTaskId }}</el-tag>
          </p>
        </div>
      </el-alert>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span class="title">闭环审计记录</span>
      </template>
      <el-table v-loading="loading" :data="audits" stripe size="small" @row-click="openDetail">
        <el-table-column prop="taskId" label="任务 ID" min-width="150" show-overflow-tooltip />
        <el-table-column prop="taskName" label="任务名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="闭环状态" width="150">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.closeStatus).type" size="small">
              {{ statusMeta(row.closeStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成率" width="160">
          <template #default="{ row }">
            <el-progress
              :percentage="ratePct(row.completionRate)"
              :status="row.closeStatus === 'COMPLETED' ? 'success' : row.closeStatus === 'MANUAL_REQUIRED' ? 'exception' : 'warning'"
              :stroke-width="12"
            />
          </template>
        </el-table-column>
        <el-table-column label="补检任务" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.rescheduleTaskId" size="small" type="warning" effect="plain">
              {{ row.rescheduleTaskId }}
            </el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="rescheduleCount" label="补检轮次" width="90" align="center" />
        <el-table-column prop="closeTime" label="闭环时间" width="170" />
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="巡检闭环报告" size="460px">
      <template v-if="detailReport">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="任务">{{ detailReport.taskName ?? detailRow?.taskId }}</el-descriptions-item>
          <el-descriptions-item label="闭环状态">
            <el-tag :type="statusMeta(detailReport.closeStatus).type" size="small">
              {{ statusMeta(detailReport.closeStatus).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="完成率">{{ ratePct(detailReport.completionRate) }}%</el-descriptions-item>
          <el-descriptions-item label="终端数">{{ detailReport.terminalCount }}</el-descriptions-item>
          <el-descriptions-item label="航点完成">
            {{ detailReport.finishedWaypointCount }} / {{ detailReport.plannedWaypointCount }}
          </el-descriptions-item>
          <el-descriptions-item label="漏检设备">{{ detailReport.missedDeviceCount }}</el-descriptions-item>
          <el-descriptions-item label="数据缺口">{{ detailReport.dataGapCount }}</el-descriptions-item>
          <el-descriptions-item label="AI 异常">{{ detailReport.defectCount }}</el-descriptions-item>
          <el-descriptions-item label="航程">{{ detailReport.totalDistanceM?.toFixed?.(1) ?? '—' }} m</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detailReport.durationSec?.toFixed?.(0) ?? '—' }} s</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ detailReport.generateTime }}</el-descriptions-item>
        </el-descriptions>
        <div class="summary-box">
          <div class="summary-title">执行摘要</div>
          <p>{{ detailReport.executiveSummary }}</p>
        </div>
        <div v-if="detailMissed?.dataGaps?.length || detailMissed?.missedDeviceIds?.length" class="summary-box">
          <div class="summary-title">未完成项明细</div>
          <p v-if="detailMissed?.missedDeviceIds?.length">
            漏检设备 ID：{{ detailMissed.missedDeviceIds.join('、') }}
          </p>
          <p v-if="detailMissed?.missedWaypointCount">漏检航点数：{{ detailMissed.missedWaypointCount }}</p>
          <ul v-if="detailMissed?.dataGaps?.length" class="gap-list">
            <li v-for="(g, i) in detailMissed.dataGaps" :key="i">
              航点 #{{ g.waypointIndex ?? '?' }}：{{ g.message }}
            </li>
          </ul>
        </div>
      </template>
      <el-empty v-else description="无报告详情" />
    </el-drawer>
  </div>
</template>

<style scoped>
.close-loop-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.title {
  font-weight: 600;
}
.intro {
  font-size: 13px;
  color: #606266;
  line-height: 1.9;
}
.intro .el-tag {
  margin-right: 6px;
}
.intro .desc {
  margin: 10px 0 0;
}
.manual-form {
  display: flex;
  flex-wrap: wrap;
  row-gap: 4px;
}
.manual-result p {
  margin: 4px 0;
}
.muted {
  color: #c0c4cc;
}
.summary-box {
  margin-top: 14px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  color: #303133;
}
.summary-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.gap-list {
  margin: 6px 0 0;
  padding-left: 18px;
}
.summary-box p {
  margin: 4px 0;
  line-height: 1.6;
}
</style>
