<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ragIngest,
  ragListDocuments,
  ragDeleteDocument,
  ragCleanupOrphans,
  ragAttachDevices,
  ragDetachDevice,
  ragListDevices,
  type RagDocument,
  type RagDevice
} from '@/api/rag'
import DeviceSelector from '@/components/rag/DeviceSelector.vue'
import PdfUploader from '@/components/rag/PdfUploader.vue'
import PdfPreviewDialog from '@/components/rag/PdfPreviewDialog.vue'

const DOC_TYPES = [
  { value: '巡检规程', label: '巡检规程' },
  { value: '故障手册', label: '故障手册' },
  { value: '技术图纸', label: '技术图纸' },
  { value: '运维记录', label: '运维记录' },
  { value: '其他', label: '其他' }
]

const documents = ref<RagDocument[]>([])
const loading = ref(false)
const keyword = ref('')
const filterDeviceId = ref<string | undefined>(undefined)

const selectedDocs = ref<RagDocument[]>([])

const onSelectionChange = (rows: RagDocument[]) => {
  selectedDocs.value = rows ?? []
}

const page = ref(1)
const pageSize = ref(10)

const pagedDocs = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return documents.value.slice(start, start + pageSize.value)
})

const handleSearch = async () => {
  loading.value = true
  try {
    const res: any = await ragListDocuments({
      keyword: keyword.value || undefined,
      deviceId: filterDeviceId.value || undefined
    })
    documents.value = (res.data as RagDocument[]) ?? []
    page.value = 1
  } catch (e: any) {
    ElMessage.error('加载文档列表失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  keyword.value = ''
  filterDeviceId.value = undefined
  return handleSearch()
}

// ---------- 上传弹窗 ----------
type UploadStage = 'idle' | 'uploading' | 'processing' | 'done'

const uploadOpen = ref(false)
const uploadFile = ref<File | null>(null)
const uploadDeviceId = ref<string | undefined>(undefined)
const uploadDocType = ref<string>('巡检规程')
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadStage = ref<UploadStage>('idle')
const uploadElapsed = ref(0)
let elapsedTimer: number | null = null
const uploaderRef = ref<InstanceType<typeof PdfUploader> | null>(null)

const stageLabel = (stage: UploadStage) => {
  switch (stage) {
    case 'uploading':
      return '正在上传到服务端...'
    case 'processing':
      return '已上传，服务端处理中：MinIO 存原文 → PDF 解析 → 切块 → Ollama 向量化 → Milvus / Neo4j 写入'
    case 'done':
      return '完成'
    default:
      return ''
  }
}

const startElapsedTimer = () => {
  uploadElapsed.value = 0
  if (elapsedTimer) window.clearInterval(elapsedTimer)
  elapsedTimer = window.setInterval(() => {
    uploadElapsed.value += 1
  }, 1000)
}

const stopElapsedTimer = () => {
  if (elapsedTimer) {
    window.clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

const openUpload = () => {
  uploadFile.value = null
  uploadDeviceId.value = undefined
  uploadDocType.value = '巡检规程'
  uploadProgress.value = 0
  uploadStage.value = 'idle'
  uploadOpen.value = true
}

const handleUpload = async () => {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择 PDF 文件')
    return
  }
  if (!uploadDocType.value) {
    ElMessage.warning('请选择文档类型')
    return
  }
  uploading.value = true
  uploadProgress.value = 0
  uploadStage.value = 'uploading'
  startElapsedTimer()
  try {
    const res: any = await ragIngest(
      uploadFile.value,
      uploadDeviceId.value || '',
      uploadDocType.value,
      (percent) => {
        uploadProgress.value = percent
        if (percent >= 100) {
          uploadStage.value = 'processing'
        }
      }
    )
    uploadStage.value = 'done'
    uploadProgress.value = 100
    ElMessage.success(res.msg || '入库成功')
    uploadOpen.value = false
    uploaderRef.value?.clear()
    await handleSearch()
  } catch (e: any) {
    ElMessage.error('入库失败：' + (e?.message ?? '未知错误'))
  } finally {
    stopElapsedTimer()
    uploading.value = false
  }
}

// ---------- 预览 ----------
const previewOpen = ref(false)
const previewDocId = ref<string | undefined>(undefined)
const previewFileName = ref<string | undefined>(undefined)

const handlePreview = (row: RagDocument) => {
  previewDocId.value = row.docId
  previewFileName.value = row.fileName
  previewOpen.value = true
}

// ---------- 删除 ----------
const handleDelete = async (row: RagDocument) => {
  try {
    await ElMessageBox.confirm(
      `确定删除文档 [${row.fileName}] ? 将同时清理 Milvus 向量、Neo4j 节点与 MinIO 对象。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await ragDeleteDocument(row.docId)
    ElMessage.success('已删除')
    await handleSearch()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

const handleCleanup = async () => {
  try {
    await ElMessageBox.confirm(
      '将清理 Neo4j 中 docId / fileName / uploadTime 缺失的脏 Document 节点，是否继续？',
      '清理脏数据',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const res: any = await ragCleanupOrphans()
    const deleted = res.data?.deleted ?? 0
    ElMessage.success(`清理完成，共移除 ${deleted} 条脏节点`)
    await handleSearch()
  } catch (e: any) {
    ElMessage.error('清理失败：' + (e?.message ?? '未知错误'))
  }
}

const handleBatchDelete = async () => {
  const targets = selectedDocs.value.filter((r) => r && r.docId)
  if (!targets.length) {
    if (!selectedDocs.value.length) {
      ElMessage.warning('请先勾选要删除的文档')
    } else {
      ElMessage.warning('选中的行没有有效的 docId（可能是脏数据），请刷新后重试')
    }
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${targets.length} 个文档？将同时清理 Milvus 向量、Neo4j 节点与 MinIO 对象。`,
      '批量删除',
      { type: 'warning' }
    )
  } catch {
    return
  }
  let ok = 0
  let fail = 0
  for (const row of targets) {
    try {
      await ragDeleteDocument(row.docId)
      ok++
    } catch (e) {
      fail++
      console.error('删除失败', row.docId, e)
    }
  }
  if (fail === 0) {
    ElMessage.success(`已删除 ${ok} 个文档`)
  } else {
    ElMessage.warning(`完成：成功 ${ok} 个，失败 ${fail} 个（详见控制台）`)
  }
  selectedDocs.value = []
  await handleSearch()
}

const formatTime = (ts?: number) => (ts ? new Date(ts).toLocaleString() : '-')

// ---------- 多设备关联 ----------
const attachOpen = ref(false)
const attachTarget = ref<RagDocument | null>(null)
const attachDeviceIds = ref<string[]>([])
const attachSubmitting = ref(false)
const deviceOptions = ref<RagDevice[]>([])

const loadDeviceOptions = async () => {
  try {
    const res: any = await ragListDevices()
    deviceOptions.value = (res.data as RagDevice[]) ?? []
  } catch (e: any) {
    console.warn('加载设备列表失败', e)
  }
}

const openAttachDialog = async (row: RagDocument) => {
  attachTarget.value = row
  attachDeviceIds.value = []
  await loadDeviceOptions()
  attachOpen.value = true
}

const submitAttach = async () => {
  if (!attachTarget.value) return
  const ids = (attachDeviceIds.value || []).map((s) => (s || '').trim()).filter(Boolean)
  if (!ids.length) {
    ElMessage.warning('请至少选择或输入一个设备')
    return
  }
  // 过滤掉已经存在的关联，避免无意义请求
  const existing = new Set((attachTarget.value.devices || []).map((d) => d.deviceId))
  const newOnes = ids.filter((id) => !existing.has(id))
  if (!newOnes.length) {
    ElMessage.info('选中的设备已经全部关联')
    attachOpen.value = false
    return
  }
  attachSubmitting.value = true
  try {
    await ragAttachDevices(attachTarget.value.docId, newOnes)
    ElMessage.success(`已关联 ${newOnes.length} 个设备`)
    attachOpen.value = false
    await handleSearch()
  } catch (e: any) {
    ElMessage.error('关联失败：' + (e?.message ?? '未知错误'))
  } finally {
    attachSubmitting.value = false
  }
}

const handleDetachDevice = async (row: RagDocument, device: RagDevice) => {
  try {
    await ElMessageBox.confirm(
      `确定解除文档 [${row.fileName}] 与设备 [${device.name || device.deviceId}] 的关联？`,
      '解除关联',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await ragDetachDevice(row.docId, device.deviceId)
    ElMessage.success('已解除关联')
    await handleSearch()
  } catch (e: any) {
    ElMessage.error('解除失败：' + (e?.message ?? '未知错误'))
  }
}

onMounted(handleSearch)
</script>

<template>
  <div class="rag-doc-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">文档管理</h2>
        <p class="page-subtitle">
          PDF 入库 → MinIO 存原文 + Milvus 向量化 + Neo4j 设备关联，构建巡检知识库
        </p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="'Upload'" @click="openUpload">上传文档</el-button>
        <el-button :icon="'Refresh'" @click="handleSearch">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="文件名">
          <el-input
            v-model="keyword"
            placeholder="按文件名关键字筛选"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="设备">
          <div style="width: 220px">
            <DeviceSelector
              v-model="filterDeviceId"
              placeholder="按设备筛选"
              allow-create
            />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button
            type="danger"
            plain
            :disabled="!selectedDocs.length"
            @click="handleBatchDelete"
          >
            批量删除{{ selectedDocs.length ? `（${selectedDocs.length}）` : '' }}
          </el-button>
          <el-button type="warning" plain @click="handleCleanup">清理脏数据</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="pagedDocs"
        row-key="docId"
        stripe
        border
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" :reserve-selection="true" />
        <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
        <el-table-column label="关联设备" min-width="260">
          <template #default="{ row }: { row: RagDocument }">
            <div class="device-tags">
              <el-tag
                v-for="d in row.devices || []"
                :key="d.deviceId"
                type="primary"
                closable
                @close="handleDetachDevice(row, d)"
              >
                {{ d.name || d.deviceId }}
              </el-tag>
              <el-button
                link
                type="primary"
                class="attach-btn"
                @click="openAttachDialog(row)"
              >
                + 关联
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="docType" label="文档类型" width="120">
          <template #default="{ row }: { row: RagDocument }">
            <el-tag v-if="row.docType" effect="plain">{{ row.docType }}</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切块数" width="90" align="center" />
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }: { row: RagDocument }">
            {{ formatTime(row.uploadTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }: { row: RagDocument }">
            <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无文档，点击右上角上传 PDF 入库" />
        </template>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="documents.length"
          :page-sizes="[10, 20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </el-card>

    <!-- 上传弹窗 -->
    <el-dialog
      v-model="uploadOpen"
      title="上传 PDF 文档到知识库"
      width="640px"
      :close-on-click-modal="false"
      :close-on-press-escape="!uploading"
      destroy-on-close
    >
      <el-form label-width="100px" @submit.prevent>
        <el-form-item label="PDF 文件" required>
          <PdfUploader
            ref="uploaderRef"
            :max-size-m-b="50"
            @select="(f: File) => (uploadFile = f)"
            @clear="uploadFile = null"
          />
        </el-form-item>
        <el-form-item label="关联设备">
          <DeviceSelector
            v-model="uploadDeviceId"
            placeholder="可输入新的设备 ID 创建关联"
            allow-create
          />
        </el-form-item>
        <el-form-item label="文档类型" required>
          <el-select v-model="uploadDocType" style="width: 100%">
            <el-option v-for="t in DOC_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="uploading || uploadProgress > 0" label="上传进度">
          <div style="width: 100%">
            <!-- 阶段 1：真实上传进度（基于 axios onUploadProgress）-->
            <el-progress
              v-if="uploadStage !== 'processing'"
              :percentage="uploadProgress"
              :status="uploadStage === 'done' ? 'success' : undefined"
            />
            <!-- 阶段 2：服务端处理（向量化耗时不可预测，使用条纹动画）-->
            <el-progress
              v-else
              :percentage="100"
              :stroke-width="14"
              striped
              striped-flow
              :duration="6"
              :show-text="false"
            />
            <div class="upload-stage-tip">
              <span>{{ stageLabel(uploadStage) }}</span>
              <span v-if="uploading" class="upload-elapsed">已耗时 {{ uploadElapsed }}s</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="uploading" @click="uploadOpen = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">
          {{ uploading ? '处理中...' : '提交入库' }}
        </el-button>
      </template>
    </el-dialog>

    <PdfPreviewDialog
      v-model="previewOpen"
      :doc-id="previewDocId"
      :file-name="previewFileName"
    />

    <!-- 关联设备弹窗 -->
    <el-dialog
      v-model="attachOpen"
      title="关联设备"
      width="540px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-if="attachTarget" class="attach-context">
        <div class="attach-doc">
          文档：<span class="attach-doc-name">{{ attachTarget.fileName }}</span>
        </div>
        <div v-if="(attachTarget.devices || []).length" class="attach-existing">
          已关联：
          <el-tag
            v-for="d in attachTarget.devices"
            :key="d.deviceId"
            type="info"
            class="attach-existing-tag"
          >
            {{ d.name || d.deviceId }}
          </el-tag>
        </div>
      </div>
      <el-form label-width="80px">
        <el-form-item label="新设备" required>
          <el-select
            v-model="attachDeviceIds"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="可搜索已有设备或直接输入新设备 ID 回车创建"
            style="width: 100%"
          >
            <el-option
              v-for="d in deviceOptions"
              :key="d.deviceId"
              :label="d.name ? `${d.name} (${d.deviceId})` : d.deviceId"
              :value="d.deviceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <span class="attach-tip">
            支持多选；输入未注册的 deviceId 回车后会作为新设备创建并关联。
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="attachSubmitting" @click="attachOpen = false">取消</el-button>
        <el-button type="primary" :loading="attachSubmitting" @click="submitAttach">
          确定关联
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.rag-doc-page {
  padding: 16px 20px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.page-subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.filter-card {
  margin-bottom: 16px;
}

.table-card {
  border-radius: 6px;
}

.pagination {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.muted {
  color: #909399;
}

.upload-stage-tip {
  margin-top: 6px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
}

.upload-elapsed {
  color: #909399;
  white-space: nowrap;
  margin-left: 12px;
}

.device-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.attach-btn {
  padding: 0 4px;
  height: 24px;
  line-height: 24px;
}

.attach-context {
  margin-bottom: 14px;
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
}

.attach-doc-name {
  color: #303133;
  font-weight: 500;
}

.attach-existing {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.attach-existing-tag {
  margin-left: 2px;
}

.attach-tip {
  font-size: 12px;
  color: #909399;
}
</style>
