<script setup lang="ts">
import type { EdgeTerminalMetrics } from '@/sim/edgeMetrics'
import type { DeployMode } from '@/sim/types'
import type { TowerCoordRow } from '@/sim/towerInspectionCoords'

defineProps<{
  edgeMetrics: EdgeTerminalMetrics
  patrolTowerCoordRows: TowerCoordRow[]
  routeFetchLoading: boolean
  routeFetchRawJson: string
  missionJson: string
  roleLabel: (role: TowerCoordRow['role']) => string
}>()

const emit = defineEmits<{
  applyNetwork: []
  copyTowerCoord: [row: TowerCoordRow]
  copyAllPhotoCoords: []
  pullRoute: []
  tryWaypointLimit: []
}>()

const sceneTabModel = defineModel<'patrol' | 'substation' | 'thermal'>('sceneTab', { required: true })
const viewModeModel = defineModel<'aerial' | 'ground'>('viewMode', { required: true })
const deployModeModel = defineModel<DeployMode>('deployMode', { required: true })
const simulateDisconnectModel = defineModel<boolean>('simulateDisconnect', { required: true })
const simulateLowBatteryModel = defineModel<boolean>('simulateLowBattery', { required: true })
const simulateRtkLostModel = defineModel<boolean>('simulateRtkLost', { required: true })
const routeFetchUavIdModel = defineModel<number | undefined>('routeFetchUavId')
const routeFetchPlanIdModel = defineModel<number | undefined>('routeFetchPlanId')
</script>

<template>
  <div class="sim-panel flex min-h-0 flex-1 flex-col overflow-hidden border-t border-[var(--ia-border)] bg-[var(--ia-panel)] font-mono text-[11px] text-[var(--ia-muted)]">
    <div class="shrink-0 border-b border-[var(--ia-border)] px-2 py-1.5">
      <div class="mb-1 text-[10px] font-semibold uppercase tracking-wider text-[var(--ia-accent)]">仿真场景</div>
      <el-radio-group v-model="sceneTabModel" size="small" class="scene-tab-rg flex w-full flex-col gap-0.5">
        <el-radio value="patrol" class="!mr-0">输电巡检场地</el-radio>
        <el-radio value="substation" class="!mr-0">变电站场景</el-radio>
        <el-radio value="thermal" class="!mr-0">火电站巡检</el-radio>
      </el-radio-group>
    </div>

    <div class="sim-panel-scroll min-h-0 flex-1 overflow-y-auto px-2 py-2">
      <el-collapse class="sim-collapse" :model-value="['edge', 'view', 'deploy', 'fault', 'wp', 'tower', 'route']">
        <el-collapse-item title="边缘终端负载" name="edge">
          <div class="grid grid-cols-2 gap-2 text-[11px]">
            <div>
              <span class="text-[var(--ia-muted)]">CPU</span>
              <div class="text-sm text-[#e8f0f8]">{{ edgeMetrics.cpuPercent.toFixed(1) }} %</div>
            </div>
            <div>
              <span class="text-[var(--ia-muted)]">存储</span>
              <div class="text-sm text-[#e8f0f8]">{{ edgeMetrics.storagePercent.toFixed(1) }} %</div>
            </div>
            <div class="col-span-2">
              <span class="text-[var(--ia-muted)]">网络</span>
              <div class="text-sm text-[#e8f0f8]">{{ edgeMetrics.networkMbps.toFixed(1) }} Mbps</div>
              <div class="text-[10px] text-[#6a8aa8]">{{ edgeMetrics.networkLabel }}</div>
            </div>
          </div>
        </el-collapse-item>

        <el-collapse-item v-if="sceneTabModel === 'patrol'" title="场景视角" name="view">
          <el-radio-group v-model="viewModeModel" size="small" class="ia-radio-tight flex flex-col gap-0.5">
            <el-radio value="aerial">鸟瞰（默认轨道）</el-radio>
            <el-radio value="ground">地面观察（人眼高度）</el-radio>
          </el-radio-group>
        </el-collapse-item>

        <el-collapse-item title="部署模式" name="deploy">
          <el-radio-group v-model="deployModeModel" size="small" class="ia-radio-tight flex flex-col gap-1">
            <el-radio value="groundStation">地面站（+100ms RTT）</el-radio>
            <el-radio value="onboard">机载（+20ms RTT）</el-radio>
          </el-radio-group>
        </el-collapse-item>

        <el-collapse-item title="异常注入" name="fault">
          <el-switch v-model="simulateDisconnectModel" active-text="断网" @change="emit('applyNetwork')" />
          <p class="mt-1 text-[10px]">断网时遥测缓存，恢复后补报</p>
          <el-divider class="!my-2 !border-[var(--ia-border)]" />
          <el-switch v-model="simulateLowBatteryModel" active-text="低电量起飞" />
          <el-divider class="!my-2 !border-[var(--ia-border)]" />
          <el-switch v-model="simulateRtkLostModel" active-text="非 RTK 固定解" />
        </el-collapse-item>

        <el-collapse-item title="航点上限（65535）" name="wp">
          <el-button size="small" class="!font-mono" @click="emit('tryWaypointLimit')">触发校验</el-button>
        </el-collapse-item>

        <el-collapse-item v-if="sceneTabModel === 'patrol'" title="杆塔参考坐标（WGS84）" name="tower">
          <div class="mb-1 flex justify-end">
            <el-button type="primary" link size="small" class="!font-mono" @click="emit('copyAllPhotoCoords')">
              复制拍照点
            </el-button>
          </div>
          <el-table :data="patrolTowerCoordRows" size="small" stripe max-height="200" class="tower-coord-table">
            <el-table-column prop="label" label="位置" min-width="88" show-overflow-tooltip />
            <el-table-column label="类型" width="40">
              <template #default="{ row }">
                <span class="text-[9px] text-[var(--ia-accent)]">{{ roleLabel(row.role) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="longitude" label="经度" min-width="76" />
            <el-table-column prop="latitude" label="纬度" min-width="76" />
            <el-table-column label="" width="36" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="emit('copyTowerCoord', row)">复制</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>

        <el-collapse-item title="智能巡检路径（云端）" name="route">
          <div class="flex flex-col gap-2">
            <div class="grid grid-cols-2 gap-2">
              <div>
                <div class="mb-0.5 text-[10px]">无人机 ID</div>
                <el-input-number
                  v-model="routeFetchUavIdModel"
                  :min="1"
                  :controls="false"
                  class="!w-full"
                  size="small"
                />
              </div>
              <div>
                <div class="mb-0.5 text-[10px]">规划 planId</div>
                <el-input-number
                  v-model="routeFetchPlanIdModel"
                  :min="1"
                  :controls="false"
                  class="!w-full"
                  size="small"
                />
              </div>
            </div>
            <el-button
              type="primary"
              size="small"
              class="!font-mono"
              :loading="routeFetchLoading"
              @click="emit('pullRoute')"
            >
              拉取并转换 Waypoint JSON
            </el-button>
          </div>
          <el-collapse v-if="routeFetchRawJson || missionJson" class="ia-collapse ia-collapse-json mt-2">
            <el-collapse-item v-if="routeFetchRawJson" title="后台路径 JSON" name="dispatch">
              <pre class="max-h-28 overflow-auto text-[10px] text-[#8ab4f8]">{{ routeFetchRawJson }}</pre>
            </el-collapse-item>
            <el-collapse-item v-if="missionJson" title="Waypoint JSON" name="mission">
              <pre class="max-h-28 overflow-auto text-[10px] text-[#6ecf9b]">{{ missionJson }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<style scoped>
.sim-panel-scroll {
  scrollbar-width: thin;
}

:deep(.sim-collapse) {
  border: none;
  --el-collapse-header-bg-color: transparent;
  --el-collapse-content-bg-color: transparent;
}
:deep(.sim-collapse .el-collapse-item__header) {
  font-family: ui-monospace, monospace;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ia-muted);
  height: 32px;
  line-height: 32px;
  padding-left: 4px;
  border-bottom: 1px solid var(--ia-border);
}
:deep(.sim-collapse .el-collapse-item__wrap) {
  border: none;
}
:deep(.sim-collapse .el-collapse-item__content) {
  padding: 8px 4px 10px;
}

:deep(.scene-tab-rg .el-radio) {
  margin-right: 0;
  height: auto;
  line-height: 1.35;
  font-size: 10px;
  color: #c8d4e0;
}

.ia-radio-tight :deep(.el-radio) {
  margin-right: 0;
  height: auto;
  line-height: 1.25;
}
</style>
