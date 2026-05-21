<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadRequestOptions } from 'element-plus'
import {
  agentMinioHealth,
  agentMinioListObjects,
  agentMinioUpload,
  agentMinioPresign,
  agentMinioDelete,
  type MinioHealth,
  type MinioObject,
  type MinioUploadResult
} from '@/api/agent'

const health = ref<MinioHealth | null>(null)
const healthLoading = ref(false)
const listLoading = ref(false)
const objects = ref<MinioObject[]>([])
const prefix = ref('test/')
const uploadPrefix = ref('test/')

const loadHealth = async () => {
  healthLoading.value = true
  try {
    const res: any = await agentMinioHealth()
    health.value = res.data
    if (res.data?.reachable) {
      ElMessage.success('MinIO 连接正常')
    } else {
      ElMessage.warning(res.data?.message || 'MinIO 不可用')
    }
  } catch {
    health.value = null
  } finally {
    healthLoading.value = false
  }
}

const loadObjects = async () => {
  listLoading.value = true
  try {
    const res: any = await agentMinioListObjects(prefix.value || undefined, 100)
    objects.value = res.data ?? []
  } catch {
    objects.value = []
  } finally {
    listLoading.value = false
  }
}

const handleUpload = async (options: UploadRequestOptions) => {
  try {
    const res: any = await agentMinioUpload(options.file as File, uploadPrefix.value || undefined)
    const data = res.data as MinioUploadResult
    ElMessage.success(`上传成功：${data.objectKey}`)
    await loadObjects()
    options.onSuccess?.(res)
  } catch (e) {
    options.onError?.(e as Error)
  }
}

const copyPresign = async (row: MinioObject) => {
  try {
    const res: any = await agentMinioPresign(row.objectKey)
    const url = res.data?.url as string
    await navigator.clipboard.writeText(url)
    ElMessage.success('预签名 URL 已复制（有效期见配置天数）')
  } catch {
    /* handled by interceptor */
  }
}

const openPresign = async (row: MinioObject) => {
  try {
    const res: any = await agentMinioPresign(row.objectKey)
    const url = res.data?.url as string
    if (url) window.open(url, '_blank')
  } catch {
    /* handled by interceptor */
  }
}

const removeObject = async (row: MinioObject) => {
  try {
    await agentMinioDelete(row.objectKey)
    ElMessage.success('已删除')
    await loadObjects()
  } catch {
    /* handled by interceptor */
  }
}

const formatSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

const openConsole = () => {
  const base = health.value?.endpoint || 'http://127.0.0.1:9000'
  window.open(base.replace(/\/$/, ''), '_blank')
}

onMounted(async () => {
  await loadHealth()
  if (health.value?.reachable) {
    await loadObjects()
  }
})
</script>

<template>
  <div class="minio-page">
    <el-card shadow="never" class="status-card">
      <div class="status-row">
        <div class="status-main">
          <h2 class="page-title">MinIO 对象存储测试</h2>
          <p class="page-subtitle">通过 raccoon-cloud-agent 连接 MinIO，Bucket：inspection</p>
        </div>
        <div class="status-metrics">
          <div class="metric">
            <span class="metric-label">服务状态</span>
            <el-tag v-if="health?.reachable" type="success" size="small">可用</el-tag>
            <el-tag v-else type="danger" size="small">不可用</el-tag>
          </div>
          <div class="metric">
            <span class="metric-label">Bucket</span>
            <span class="metric-value">{{ health?.bucketName ?? '—' }}</span>
          </div>
          <div class="metric">
            <span class="metric-label">URL 有效期</span>
            <span class="metric-value">{{ health?.urlExpireDays ?? 7 }} 天</span>
          </div>
          <div class="metric metric--wide">
            <span class="metric-label">Endpoint</span>
            <span class="metric-value metric-value--mono">{{ health?.endpoint ?? '—' }}</span>
          </div>
        </div>
        <div class="status-actions">
          <el-button :loading="healthLoading" @click="loadHealth">检测连接</el-button>
          <el-button :loading="listLoading" :disabled="!health?.reachable" @click="loadObjects">
            刷新列表
          </el-button>
          <el-button @click="openConsole">打开控制台</el-button>
        </div>
      </div>
      <p v-if="health?.message" class="status-hint">{{ health.message }}</p>
    </el-card>

    <div class="page-body">
      <el-card shadow="never" class="upload-card">
        <template #header>上传文件</template>
        <el-form label-position="top">
          <el-form-item label="对象前缀（目录）">
            <el-input v-model="uploadPrefix" placeholder="例如 test/" />
          </el-form-item>
          <el-form-item>
            <el-upload
              drag
              :http-request="handleUpload"
              :show-file-list="false"
              :disabled="!health?.reachable"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
            </el-upload>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="list-card">
        <template #header>
          <div class="card-header">
            <span>对象列表</span>
            <el-input
              v-model="prefix"
              placeholder="前缀过滤，如 test/"
              clearable
              class="prefix-input"
              @keyup.enter="loadObjects"
            />
          </div>
        </template>
        <el-table v-loading="listLoading" :data="objects" size="small" border stripe max-height="480">
          <el-table-column prop="objectKey" label="对象 Key" min-width="220" show-overflow-tooltip />
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ formatSize(row.size) }}</template>
          </el-table-column>
          <el-table-column prop="lastModified" label="更新时间" width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openPresign(row)">预览</el-button>
              <el-button type="primary" link @click="copyPresign(row)">复制链接</el-button>
              <el-button type="danger" link @click="removeObject(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!listLoading && !objects.length" description="暂无对象，请先上传文件" />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.minio-page {
  width: 100%;
  min-height: calc(100vh - 96px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.status-row {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  flex-wrap: wrap;
}

.status-main {
  flex: 0 0 auto;
}

.page-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}

.page-subtitle {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

.status-metrics {
  flex: 1 1 360px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px 24px;
  align-items: center;
}

.metric {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric--wide {
  flex: 1 1 200px;
  min-width: 0;
}

.metric-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}

.metric-value {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-value--mono {
  font-family: ui-monospace, Consolas, monospace;
}

.status-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.status-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: #909399;
}

.page-body {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.upload-icon {
  font-size: 48px;
  color: #c0c4cc;
  margin-bottom: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.prefix-input {
  max-width: 240px;
}

@media (max-width: 992px) {
  .page-body {
    grid-template-columns: 1fr;
  }

  .status-actions {
    margin-left: 0;
    width: 100%;
  }
}
</style>
