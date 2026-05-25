<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  agentMilvusTestInsert,
  agentMilvusTestSearch,
  agentMilvusTestGetById,
  type MilvusTestDocument
} from '@/api/agent-milvus-test'
import { logMilvusTestError, type MilvusTestErrorLog } from '@/utils/milvus-test-error'

const DEVICE_TYPES = ['主变', '断路器', '互感器', '其他']
const FAULT_TYPES = ['温度异常', '跳闸', '数据异常', '其他']

const genUuid = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `doc-${Date.now()}`

// ---------- 录入区 ----------
const docId = ref(genUuid())
const content = ref('')
const deviceType = ref('主变')
const faultType = ref('温度异常')
const source = ref('测试录入')
const insertLoading = ref(false)
const lastError = ref<MilvusTestErrorLog | null>(null)
const errorLogJson = ref('')

const showError = (action: string, e: unknown) => {
  const log = logMilvusTestError(action, e)
  lastError.value = log
  errorLogJson.value = JSON.stringify(log, null, 2)
  ElMessage.error(log.displayText)
}

const clearError = () => {
  lastError.value = null
  errorLogJson.value = ''
}

const resetDocId = () => {
  docId.value = genUuid()
}

const handleInsert = async () => {
  if (!content.value.trim()) {
    ElMessage.warning('请填写文档内容')
    return
  }
  insertLoading.value = true
  clearError()
  try {
    const res: any = await agentMilvusTestInsert({
      docId: docId.value.trim(),
      content: content.value.trim(),
      deviceType: deviceType.value,
      faultType: faultType.value,
      source: source.value || '测试录入'
    })
    ElMessage.success(res.data?.message || '入库成功')
    resetDocId()
  } catch (e: unknown) {
    showError('提交入库', e)
  } finally {
    insertLoading.value = false
  }
}

const handleGetById = async () => {
  if (!docId.value.trim()) {
    ElMessage.warning('请填写文档 ID')
    return
  }
  insertLoading.value = true
  clearError()
  try {
    const res: any = await agentMilvusTestGetById({ docId: docId.value.trim() })
    const doc = res.data as MilvusTestDocument
    if (doc) {
      content.value = doc.content || ''
      const meta = doc.metadata || {}
      if (meta.deviceType) deviceType.value = String(meta.deviceType)
      if (meta.faultType) faultType.value = String(meta.faultType)
      if (meta.source) source.value = String(meta.source)
      ElMessage.success('已查到该文档，表单已回填')
    }
  } catch (e: unknown) {
    showError('按 ID 验证', e)
  } finally {
    insertLoading.value = false
  }
}

// ---------- 检索区 ----------
const query = ref('')
const topK = ref(3)
const searchLoading = ref(false)
const searchResults = ref<MilvusTestDocument[]>([])

const handleSearch = async () => {
  if (!query.value.trim()) {
    ElMessage.warning('请填写查询词')
    return
  }
  searchLoading.value = true
  searchResults.value = []
  clearError()
  try {
    const res: any = await agentMilvusTestSearch({
      query: query.value.trim(),
      topK: topK.value
    })
    searchResults.value = res.data ?? []
    if (!searchResults.value.length) {
      ElMessage.info('未检索到匹配文档')
    }
  } catch (e: unknown) {
    showError('向量检索', e)
  } finally {
    searchLoading.value = false
  }
}

const copyErrorLog = async () => {
  if (!errorLogJson.value) return
  try {
    await navigator.clipboard.writeText(errorLogJson.value)
    ElMessage.success('错误日志已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择下方文本复制')
  }
}

const metaStr = (doc: MilvusTestDocument, key: string) => {
  const v = doc.metadata?.[key]
  return v != null ? String(v) : '-'
}

const formatScore = (score?: number) => {
  if (score == null) return '-'
  return score.toFixed(4)
}
</script>

<template>
  <div class="milvus-test-page">
    <div class="page-header">
      <h2 class="page-title">Milvus 向量库测试</h2>
      <p class="page-subtitle">调试巡检 RAG：故障知识入库与相似检索（raccoon-cloud-agent）</p>
    </div>

    <el-row :gutter="20">
      <!-- 故障知识录入 -->
      <el-col :span="12">
        <el-card shadow="never" class="section-card">
          <template #header>
            <span class="section-title">故障知识录入区</span>
          </template>

          <el-form label-width="100px" @submit.prevent>
            <el-form-item label="文档 ID">
              <el-input v-model="docId" placeholder="默认 UUID，可自定义">
                <template #append>
                  <el-button @click="resetDocId">重新生成</el-button>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="设备类型">
              <el-select v-model="deviceType" style="width: 100%">
                <el-option v-for="t in DEVICE_TYPES" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>

            <el-form-item label="故障类型">
              <el-select v-model="faultType" style="width: 100%">
                <el-option v-for="t in FAULT_TYPES" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>

            <el-form-item label="来源">
              <el-input v-model="source" placeholder="测试录入" />
            </el-form-item>

            <el-form-item label="文档内容">
              <el-input
                v-model="content"
                type="textarea"
                :rows="10"
                placeholder="输入故障现象、处理步骤等，将作为向量检索原文"
              />
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                :loading="insertLoading"
                @click="handleInsert"
              >
                {{ insertLoading ? '处理中...' : '提交入库' }}
              </el-button>
              <el-button :loading="insertLoading" @click="handleGetById">
                按 ID 验证
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 向量检索 -->
      <el-col :span="12">
        <el-card shadow="never" class="section-card">
          <template #header>
            <span class="section-title">向量检索测试区</span>
          </template>

          <el-form label-width="100px" @submit.prevent>
            <el-form-item label="查询词">
              <el-input
                v-model="query"
                placeholder="例如：主变温度高怎么办"
                @keyup.enter="handleSearch"
              />
            </el-form-item>

            <el-form-item label="TopK">
              <el-input-number v-model="topK" :min="1" :max="50" />
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                :loading="searchLoading"
                @click="handleSearch"
              >
                {{ searchLoading ? '处理中...' : '向量检索' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div v-if="searchLoading" class="hint">处理中...</div>

          <div v-else-if="searchResults.length" class="result-list">
            <div
              v-for="(item, idx) in searchResults"
              :key="item.docId + idx"
              class="result-item"
            >
              <div class="result-meta">
                <el-tag type="success" size="small">
                  相似度 {{ formatScore(item.score) }}
                </el-tag>
                <span class="result-id">ID: {{ item.docId }}</span>
                <el-tag size="small">{{ metaStr(item, 'deviceType') }}</el-tag>
                <el-tag size="small" type="warning">{{ metaStr(item, 'faultType') }}</el-tag>
              </div>
              <p class="result-content">{{ item.content }}</p>
            </div>
          </div>

          <el-empty v-else description="输入查询词并检索后在此显示结果" />
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="lastError" shadow="never" class="error-log-card">
      <template #header>
        <div class="error-log-header">
          <span class="section-title">调试错误日志（同时已输出到浏览器 Console）</span>
          <div>
            <el-button size="small" @click="copyErrorLog">复制 JSON</el-button>
            <el-button size="small" @click="clearError">清除</el-button>
          </div>
        </div>
      </template>
      <p class="error-summary">{{ lastError.displayText }}</p>
      <pre class="error-pre">{{ errorLogJson }}</pre>
      <p class="error-hint">打开开发者工具 (F12) → Console，筛选 <code>[MilvusTest]</code> 查看完整对象。</p>
    </el-card>
  </div>
</template>

<style scoped>
.milvus-test-page {
  padding: 16px 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
}

.page-subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.section-card {
  min-height: 520px;
}

.section-title {
  font-weight: 600;
}

.hint {
  color: #909399;
  padding: 12px 0;
}

.result-list {
  margin-top: 8px;
  max-height: 420px;
  overflow-y: auto;
}

.result-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  background: #fafafa;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.result-id {
  font-size: 12px;
  color: #606266;
}

.result-content {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  color: #303133;
}

.error-log-card {
  margin-top: 16px;
  border-color: #fde2e2;
}

.error-log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.error-summary {
  margin: 0 0 12px;
  color: #f56c6c;
  font-size: 14px;
  white-space: pre-wrap;
}

.error-pre {
  margin: 0;
  padding: 12px;
  max-height: 280px;
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  font-size: 12px;
  line-height: 1.5;
  border-radius: 6px;
}

.error-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: #909399;
}

.error-hint code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
