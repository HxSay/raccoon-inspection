<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ragNeo4jOverview,
  ragNeo4jSchema,
  ragNeo4jNodes,
  ragNeo4jRelationships,
  ragMilvusOverview,
  ragMilvusChunks,
  ragDeleteNeo4jNode,
  ragDeleteNeo4jRelationship,
  ragDeleteMilvusChunk,
  type Neo4jOverview,
  type Neo4jNode,
  type Neo4jRelationship,
  type MilvusCollectionStats,
  type MilvusChunk
} from '@/api/rag'

const activeTab = ref<'neo4j' | 'milvus'>('neo4j')

// ---------- Neo4j ----------
const neoOverview = ref<Neo4jOverview | null>(null)
const neoOverviewLoading = ref(false)

const labelOptions = ref<string[]>([])
const relTypeOptions = ref<string[]>([])

const neoView = ref<'node' | 'relationship'>('node')
const neoLabel = ref<string>('Document')
const neoRelType = ref<string>('HAS_DOCUMENT')
const neoLimit = ref<number>(100)

const neoNodes = ref<Neo4jNode[]>([])
const neoRels = ref<Neo4jRelationship[]>([])
const neoListLoading = ref(false)

const loadNeoOverview = async () => {
  neoOverviewLoading.value = true
  try {
    const res: any = await ragNeo4jOverview()
    neoOverview.value = res.data as Neo4jOverview
  } catch (e: any) {
    ElMessage.error('加载 Neo4j 总览失败：' + (e?.message ?? '未知错误'))
  } finally {
    neoOverviewLoading.value = false
  }
}

const loadNeoSchema = async () => {
  try {
    const res: any = await ragNeo4jSchema()
    const data = res.data || {}
    labelOptions.value = Array.from(data.labels || [])
    relTypeOptions.value = Array.from(data.relationshipTypes || [])
    if (labelOptions.value.length && !labelOptions.value.includes(neoLabel.value)) {
      neoLabel.value = labelOptions.value[0]
    }
    if (relTypeOptions.value.length && !relTypeOptions.value.includes(neoRelType.value)) {
      neoRelType.value = relTypeOptions.value[0]
    }
  } catch (e: any) {
    ElMessage.error('加载 Neo4j schema 失败：' + (e?.message ?? '未知错误'))
  }
}

const loadNeoList = async () => {
  neoListLoading.value = true
  try {
    if (neoView.value === 'node') {
      const res: any = await ragNeo4jNodes(neoLabel.value, neoLimit.value)
      neoNodes.value = (res.data as Neo4jNode[]) ?? []
    } else {
      const res: any = await ragNeo4jRelationships(neoRelType.value, neoLimit.value)
      neoRels.value = (res.data as Neo4jRelationship[]) ?? []
    }
  } catch (e: any) {
    ElMessage.error('查询失败：' + (e?.message ?? '未知错误'))
  } finally {
    neoListLoading.value = false
  }
}

const refreshNeo4j = async () => {
  await Promise.all([loadNeoOverview(), loadNeoSchema()])
  await loadNeoList()
}

// ---------- Milvus ----------
const milvusStats = ref<MilvusCollectionStats | null>(null)
const milvusStatsLoading = ref(false)
const milvusChunks = ref<MilvusChunk[]>([])
const milvusListLoading = ref(false)
const filterDocId = ref<string>('')
const milvusLimit = ref<number>(50)

const loadMilvusStats = async () => {
  milvusStatsLoading.value = true
  try {
    const res: any = await ragMilvusOverview()
    milvusStats.value = res.data as MilvusCollectionStats
  } catch (e: any) {
    ElMessage.error('加载 Milvus 总览失败：' + (e?.message ?? '未知错误'))
  } finally {
    milvusStatsLoading.value = false
  }
}

const loadMilvusChunks = async () => {
  milvusListLoading.value = true
  try {
    const res: any = await ragMilvusChunks({
      docId: filterDocId.value?.trim() || undefined,
      limit: milvusLimit.value
    })
    milvusChunks.value = (res.data as MilvusChunk[]) ?? []
  } catch (e: any) {
    ElMessage.error('查询 chunk 失败：' + (e?.message ?? '未知错误'))
  } finally {
    milvusListLoading.value = false
  }
}

const refreshMilvus = async () => {
  await Promise.all([loadMilvusStats(), loadMilvusChunks()])
}

const formatProps = (obj: Record<string, any>) => {
  if (!obj || !Object.keys(obj).length) return '-'
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

const formatDeviceTag = (d: Record<string, any>) =>
  d.name ? `${d.name} (${d.deviceId})` : String(d.deviceId || '-')

const formatDocTag = (d: Record<string, any>) =>
  d.fileName ? `${d.fileName}` : String(d.docId || '-')

const handleDeleteNode = async (row: Neo4jNode) => {
  const label = row.labels?.[0] || neoLabel.value
  const isRagDoc =
    label === 'Document' &&
    (row.properties?.docId || row.properties?.minioPath || row.properties?.fileName)
  const tip = isRagDoc
    ? '该 Document 为 RAG 入库文档，将联动删除 Milvus 向量与 MinIO 文件，是否继续？'
    : `确定删除 Neo4j 节点 [${label}#${row.internalId}]？`
  try {
    await ElMessageBox.confirm(tip, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await ragDeleteNeo4jNode(row.internalId, label)
    ElMessage.success('已删除')
    await refreshNeo4j()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

const handleDeleteRel = async (row: Neo4jRelationship) => {
  try {
    await ElMessageBox.confirm(
      `确定删除关系 [${row.type}] #${row.relInternalId}？（仅删边，不删节点）`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await ragDeleteNeo4jRelationship(row.relInternalId, row.type)
    ElMessage.success('已删除关系')
    await loadNeoList()
    await loadNeoOverview()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

const handleDeleteChunk = async (row: MilvusChunk) => {
  try {
    await ElMessageBox.confirm(`确定删除 Milvus chunk [${row.docPk}]？`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await ragDeleteMilvusChunk(row.docPk)
    ElMessage.success('已删除')
    await loadMilvusChunks()
    await loadMilvusStats()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

watch(activeTab, (v) => {
  if (v === 'milvus' && !milvusStats.value) {
    refreshMilvus()
  }
})

onMounted(refreshNeo4j)
</script>

<template>
  <div class="rag-storage-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">知识库存储查看</h2>
        <p class="page-subtitle">
          直观查看 Neo4j 图谱与 Milvus 向量库中已落盘的 RAG 数据，便于校验入库结果与排查问题。
        </p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="storage-tabs">
      <!-- ============= Neo4j ============= -->
      <el-tab-pane label="Neo4j 图谱" name="neo4j">
        <el-card v-loading="neoOverviewLoading" shadow="never" class="overview-card">
          <template #header>
            <div class="card-head">
              <span>Neo4j 总览</span>
              <el-button link type="primary" @click="loadNeoOverview">刷新</el-button>
            </div>
          </template>
          <div v-if="neoOverview" class="overview-grid">
            <div class="metric-block">
              <div class="metric-value">{{ neoOverview.totalNodes }}</div>
              <div class="metric-label">节点总数</div>
            </div>
            <div class="metric-block">
              <div class="metric-value">{{ neoOverview.totalRelationships }}</div>
              <div class="metric-label">关系总数</div>
            </div>
            <div class="metric-block stat-block">
              <div class="metric-label">按 Label 分布</div>
              <div class="stat-list">
                <el-tag
                  v-for="s in neoOverview.labels"
                  :key="s.label"
                  type="primary"
                  effect="plain"
                >
                  {{ s.label }} · {{ s.count }}
                </el-tag>
              </div>
            </div>
            <div class="metric-block stat-block">
              <div class="metric-label">按 RelationshipType 分布</div>
              <div class="stat-list">
                <el-tag
                  v-for="s in neoOverview.relationshipTypes"
                  :key="s.type"
                  type="success"
                  effect="plain"
                >
                  {{ s.type }} · {{ s.count }}
                </el-tag>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-card>

        <el-card shadow="never" class="filter-card">
          <el-form inline @submit.prevent>
            <el-form-item label="查看">
              <el-radio-group v-model="neoView">
                <el-radio-button label="node">节点</el-radio-button>
                <el-radio-button label="relationship">关系</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="neoView === 'node'" label="Label">
              <el-select v-model="neoLabel" style="width: 180px">
                <el-option v-for="l in labelOptions" :key="l" :label="l" :value="l" />
              </el-select>
            </el-form-item>
            <el-form-item v-else label="Type">
              <el-select v-model="neoRelType" style="width: 200px">
                <el-option v-for="t in relTypeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="Limit">
              <el-input-number v-model="neoLimit" :min="1" :max="500" :step="10" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadNeoList">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <!-- 节点列表 -->
          <el-table
            v-if="neoView === 'node'"
            v-loading="neoListLoading"
            :data="neoNodes"
            stripe
            border
          >
            <el-table-column prop="internalId" label="ID" width="80" />
            <el-table-column label="Labels" width="160">
              <template #default="{ row }: { row: Neo4jNode }">
                <el-tag v-for="l in row.labels" :key="l" size="small" style="margin-right: 4px">
                  {{ l }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="neoLabel === 'Document'"
              label="关联设备"
              min-width="220"
            >
              <template #default="{ row }: { row: Neo4jNode }">
                <div v-if="(row.relatedDevices || []).length" class="tag-list">
                  <el-tag
                    v-for="d in row.relatedDevices"
                    :key="d.deviceId"
                    type="primary"
                    size="small"
                  >
                    {{ formatDeviceTag(d) }}
                  </el-tag>
                </div>
                <span v-else class="muted">未关联</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="neoLabel === 'Device'"
              label="关联文档"
              min-width="220"
            >
              <template #default="{ row }: { row: Neo4jNode }">
                <div v-if="(row.relatedDocuments || []).length" class="tag-list">
                  <el-tag
                    v-for="d in row.relatedDocuments"
                    :key="d.docId"
                    type="info"
                    size="small"
                  >
                    {{ formatDocTag(d) }}
                  </el-tag>
                </div>
                <span v-else class="muted">未关联</span>
              </template>
            </el-table-column>
            <el-table-column label="Properties" min-width="320">
              <template #default="{ row }: { row: Neo4jNode }">
                <pre class="props-pre">{{ formatProps(row.properties) }}</pre>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }: { row: Neo4jNode }">
                <el-button link type="danger" @click="handleDeleteNode(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="未查询到节点" />
            </template>
          </el-table>

          <!-- 关系列表 -->
          <el-table
            v-else
            v-loading="neoListLoading"
            :data="neoRels"
            stripe
            border
          >
            <el-table-column label="Start" min-width="200">
              <template #default="{ row }: { row: Neo4jRelationship }">
                <div>
                  <el-tag size="small">{{ row.startLabel }}</el-tag>
                </div>
                <pre class="props-pre small">{{ formatProps(row.startNode) }}</pre>
              </template>
            </el-table-column>
            <el-table-column label="Type" width="180" align="center">
              <template #default="{ row }: { row: Neo4jRelationship }">
                <el-tag type="success">-[{{ row.type }}]-&gt;</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="End" min-width="200">
              <template #default="{ row }: { row: Neo4jRelationship }">
                <div>
                  <el-tag size="small">{{ row.endLabel }}</el-tag>
                </div>
                <pre class="props-pre small">{{ formatProps(row.endNode) }}</pre>
              </template>
            </el-table-column>
            <el-table-column label="关系属性" min-width="160">
              <template #default="{ row }: { row: Neo4jRelationship }">
                <pre class="props-pre small">{{ formatProps(row.properties) }}</pre>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }: { row: Neo4jRelationship }">
                <el-button link type="danger" @click="handleDeleteRel(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="未查询到关系" />
            </template>
          </el-table>
          <p v-if="neoView === 'node' && neoLabel === 'Document'" class="table-tip">
            关联设备通过 HAS_DOCUMENT 关系查询；也可切换到「关系」视图查看 Device → Document 明细。
          </p>
        </el-card>
      </el-tab-pane>

      <!-- ============= Milvus ============= -->
      <el-tab-pane label="Milvus 向量库" name="milvus">
        <el-card v-loading="milvusStatsLoading" shadow="never" class="overview-card">
          <template #header>
            <div class="card-head">
              <span>Milvus 集合信息</span>
              <el-button link type="primary" @click="loadMilvusStats">刷新</el-button>
            </div>
          </template>
          <div v-if="milvusStats" class="milvus-meta">
            <el-descriptions :column="3" border>
              <el-descriptions-item label="Database">
                {{ milvusStats.databaseName }}
              </el-descriptions-item>
              <el-descriptions-item label="Collection">
                {{ milvusStats.collectionName }}
              </el-descriptions-item>
              <el-descriptions-item label="行数">
                {{ milvusStats.rowCount }}
              </el-descriptions-item>
              <el-descriptions-item label="向量维度">
                {{ milvusStats.dimension }}
              </el-descriptions-item>
              <el-descriptions-item label="Index">
                {{ milvusStats.indexType || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="Metric">
                {{ milvusStats.metricType || '-' }}
              </el-descriptions-item>
            </el-descriptions>
            <div class="field-section">
              <div class="section-title">字段定义</div>
              <el-table :data="milvusStats.fields" border size="small">
                <el-table-column prop="name" label="字段" min-width="120" />
                <el-table-column prop="dataType" label="类型" width="140" />
                <el-table-column label="主键" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.primaryKey" type="warning" size="small">PK</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="maxLength" label="MaxLength" width="120" align="center" />
                <el-table-column prop="dimension" label="Dimension" width="120" align="center" />
              </el-table>
            </div>
          </div>
          <el-empty v-else description="暂无统计" />
        </el-card>

        <el-card shadow="never" class="filter-card">
          <el-form inline @submit.prevent>
            <el-form-item label="docId">
              <el-input
                v-model="filterDocId"
                placeholder="可选：按 docId metadata 过滤"
                clearable
                style="width: 280px"
              />
            </el-form-item>
            <el-form-item label="Limit">
              <el-input-number v-model="milvusLimit" :min="1" :max="200" :step="10" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadMilvusChunks">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="milvusListLoading" :data="milvusChunks" stripe border>
            <el-table-column prop="docPk" label="Milvus PK" width="280" show-overflow-tooltip />
            <el-table-column prop="fileName" label="fileName" min-width="200" show-overflow-tooltip />
            <el-table-column prop="chunkIndex" label="chunkIndex" width="100" align="center" />
            <el-table-column prop="docId" label="docId" width="280" show-overflow-tooltip />
            <el-table-column label="content" min-width="320">
              <template #default="{ row }: { row: MilvusChunk }">
                <div class="content-cell">{{ row.content }}</div>
              </template>
            </el-table-column>
            <el-table-column label="metadata" width="120">
              <template #default="{ row }: { row: MilvusChunk }">
                <el-popover :width="500" trigger="hover" placement="left">
                  <template #reference>
                    <el-button link type="primary">查看</el-button>
                  </template>
                  <pre class="props-pre">{{ formatProps(row.metadata) }}</pre>
                </el-popover>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }: { row: MilvusChunk }">
                <el-button link type="danger" @click="handleDeleteChunk(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="未查询到向量数据" />
            </template>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.rag-storage-page {
  padding: 16px 20px;
}

.page-header {
  margin-bottom: 14px;
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

.storage-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.overview-card,
.filter-card,
.table-card {
  margin-bottom: 14px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 1fr)) 2fr 2fr;
  gap: 16px;
}

.metric-block {
  background: #fafbff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
  min-height: 84px;
}

.metric-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.metric-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.stat-block .stat-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.props-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Consolas', 'Source Code Pro', monospace;
  font-size: 12px;
  color: #303133;
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
  max-height: 180px;
  overflow: auto;
}

.props-pre.small {
  max-height: 100px;
}

.content-cell {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.5;
  max-height: 100px;
  overflow: auto;
}

.milvus-meta .field-section {
  margin-top: 16px;
}

.section-title {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 8px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.muted {
  color: #909399;
  font-size: 13px;
}

.table-tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: #909399;
}

@media (max-width: 1200px) {
  .overview-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
