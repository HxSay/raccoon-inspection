<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  inspectionRobotScenes,
  inspectionRobotCreate,
  inspectionRobotUpdate,
  inspectionRobotDelete,
  ROBOT_TYPE_OPTIONS,
  SCENE_TYPE_LABEL,
  type InspectionRobotVO,
  type InspectionSceneVO
} from '@/api/inspectionRobot'

const loading = ref(false)
const scenes = ref<InspectionSceneVO[]>([])
const selectedSceneId = ref<number | null>(null)

const dialogVisible = ref(false)
const dialogTitle = ref('添加巡检机器人')
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  id: undefined as number | undefined,
  uavName: '',
  uavCode: '',
  robotType: 'UAV',
  mapId: undefined as number | undefined,
  markerLabel: '',
  markerColor: '#409EFF',
  sceneX: undefined as number | undefined,
  sceneY: undefined as number | undefined,
  sceneZ: undefined as number | undefined,
  status: 1,
  remark: ''
})

const rules: FormRules = {
  uavName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  robotType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  mapId: [{ required: true, message: '请选择虚拟场景', trigger: 'change' }],
  markerLabel: [{ required: true, message: '请输入场景标记', trigger: 'blur' }]
}

const selectedScene = computed(() =>
  scenes.value.find((s) => s.id === selectedSceneId.value) ?? null
)

const robotsInScene = computed(() => selectedScene.value?.robots ?? [])

const loadScenes = async () => {
  loading.value = true
  try {
    const res: any = await inspectionRobotScenes()
    scenes.value = res.data ?? []
    if (!selectedSceneId.value && scenes.value.length) {
      selectedSceneId.value = scenes.value[0].id
    }
  } finally {
    loading.value = false
  }
}

const selectScene = (id: number) => {
  selectedSceneId.value = id
}

const resetForm = () => {
  form.id = undefined
  form.uavName = ''
  form.uavCode = ''
  form.robotType = 'UAV'
  form.mapId = selectedSceneId.value ?? undefined
  form.markerLabel = ''
  form.markerColor = '#409EFF'
  form.sceneX = undefined
  form.sceneY = undefined
  form.sceneZ = undefined
  form.status = 1
  form.remark = ''
}

const openCreate = () => {
  resetForm()
  form.mapId = selectedSceneId.value ?? undefined
  dialogTitle.value = '添加巡检机器人'
  dialogVisible.value = true
}

const openEdit = (row: InspectionRobotVO) => {
  form.id = row.id
  form.uavName = row.uavName
  form.uavCode = row.uavCode ?? ''
  form.robotType = row.robotType
  form.mapId = row.mapId
  form.markerLabel = row.markerLabel
  form.markerColor = row.markerColor ?? '#409EFF'
  form.sceneX = row.sceneX
  form.sceneY = row.sceneY
  form.sceneZ = row.sceneZ
  form.status = row.status ?? 1
  form.remark = row.remark ?? ''
  dialogTitle.value = '编辑巡检机器人'
  dialogVisible.value = true
}

const onTypeChange = () => {
  if (!form.markerLabel && form.uavCode) {
    form.markerLabel = form.uavCode
  }
}

const submit = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form }
    if (form.id) {
      await inspectionRobotUpdate(form.id, payload)
      ElMessage.success('已更新')
    } else {
      await inspectionRobotCreate(payload)
      ElMessage.success('已添加')
    }
    dialogVisible.value = false
    await loadScenes()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}

const remove = async (row: InspectionRobotVO) => {
  await ElMessageBox.confirm(`确定删除「${row.uavName}」？`, '提示', { type: 'warning' })
  await inspectionRobotDelete(row.id!)
  ElMessage.success('已删除')
  await loadScenes()
}

const robotTypeLabel = (t: string) => ROBOT_TYPE_OPTIONS.find((o) => o.value === t)?.label ?? t

onMounted(loadScenes)
</script>

<template>
  <div class="robot-manage-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">巡检机器人管理</h2>
        <p class="page-desc">
          管理无人机、机器狗、地面巡检机器人等，绑定虚拟场景；场景标记将显示在仿真中便于识别。
        </p>
      </div>
      <el-button type="primary" :disabled="!selectedSceneId" @click="openCreate">
        <el-icon><Plus /></el-icon>
        添加机器人
      </el-button>
    </div>

    <div v-loading="loading" class="layout">
      <aside class="scene-panel">
        <div class="panel-title">虚拟场景</div>
        <div
          v-for="s in scenes"
          :key="s.id"
          class="scene-card"
          :class="{ 'scene-card--active': selectedSceneId === s.id }"
          @click="selectScene(s.id)"
        >
          <div class="scene-card__name">{{ s.mapName }}</div>
          <div class="scene-card__meta">
            <el-tag size="small" type="info">{{ SCENE_TYPE_LABEL[s.sceneType ?? ''] ?? s.sceneType }}</el-tag>
            <span>{{ s.robotCount }} 台机器人</span>
          </div>
          <div v-if="s.robots.length" class="scene-card__markers">
            <span
              v-for="r in s.robots"
              :key="r.id"
              class="marker-chip"
              :style="{ borderColor: r.markerColor, color: r.markerColor }"
            >
              {{ r.markerLabel }}
            </span>
          </div>
        </div>
      </aside>

      <main class="robot-panel">
        <template v-if="selectedScene">
          <div class="robot-panel__head">
            <span>{{ selectedScene.mapName }} · 机器人列表</span>
          </div>
          <el-table :data="robotsInScene" stripe border size="small">
            <el-table-column label="场景标记" width="110" align="center">
              <template #default="{ row }">
                <span
                  class="marker-badge"
                  :style="{ background: row.markerColor, borderColor: row.markerColor }"
                >
                  {{ row.markerLabel }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="uavName" label="名称" min-width="140" />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ robotTypeLabel(row.robotType) }}</template>
            </el-table-column>
            <el-table-column prop="uavCode" label="编码" width="100" />
            <el-table-column label="场景坐标 (X,Y,Z)" min-width="160">
              <template #default="{ row }">
                <span v-if="row.sceneX != null">{{ row.sceneX }}, {{ row.sceneY }}, {{ row.sceneZ }}</span>
                <span v-else class="text-muted">默认机位</span>
              </template>
            </el-table-column>
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
                <el-button link type="danger" @click="remove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!robotsInScene.length" description="该场景暂无机器人，点击右上角添加" />
        </template>
      </main>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="虚拟场景" prop="mapId">
          <el-select v-model="form.mapId" placeholder="选择场景" class="w-full">
            <el-option v-for="s in scenes" :key="s.id" :label="s.mapName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="机器人类型" prop="robotType">
          <el-select v-model="form.robotType" class="w-full" @change="onTypeChange">
            <el-option v-for="o in ROBOT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="uavName">
          <el-input v-model="form.uavName" placeholder="如：巡检无人机-01" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.uavCode" placeholder="如：UAV-001" />
        </el-form-item>
        <el-form-item label="场景标记" prop="markerLabel">
          <el-input v-model="form.markerLabel" placeholder="仿真中显示，如 UAV-01" maxlength="32" />
        </el-form-item>
        <el-form-item label="标记颜色">
          <el-color-picker v-model="form.markerColor" />
          <span
            class="marker-preview"
            :style="{ background: form.markerColor, borderColor: form.markerColor }"
          >
            {{ form.markerLabel || '预览' }}
          </span>
        </el-form-item>
        <el-form-item label="场景坐标">
          <div class="coord-row">
            <el-input-number v-model="form.sceneX" :controls="false" placeholder="X" />
            <el-input-number v-model="form.sceneY" :controls="false" placeholder="Y" />
            <el-input-number v-model="form.sceneZ" :controls="false" placeholder="Z" />
          </div>
          <div class="form-tip">留空则使用仿真默认机位；输电场景可参考机巢 (0,3,40)</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
.robot-manage-page {
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
}
.layout {
  display: flex;
  gap: 16px;
  min-height: 480px;
}
.scene-panel {
  width: 280px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
}
.panel-title {
  font-weight: 600;
  margin-bottom: 10px;
  font-size: 14px;
}
.scene-card {
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.scene-card:hover {
  border-color: #409eff;
}
.scene-card--active {
  border-color: #409eff;
  background: #ecf5ff;
}
.scene-card__name {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 6px;
}
.scene-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}
.scene-card__markers {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.marker-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border: 2px solid;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.9);
}
.robot-panel {
  flex: 1;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  min-width: 0;
}
.robot-panel__head {
  font-weight: 600;
  margin-bottom: 12px;
}
.marker-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  border: 2px solid;
}
.marker-preview {
  margin-left: 12px;
  padding: 4px 12px;
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  border: 2px solid;
}
.coord-row {
  display: flex;
  gap: 8px;
}
.coord-row .el-input-number {
  width: 100px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.text-muted {
  color: #c0c4cc;
}
.w-full {
  width: 100%;
}
</style>
