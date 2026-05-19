<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  agentNeo4jHealth,
  agentNeo4jStats,
  agentNeo4jReadCypher,
  type Neo4jHealth,
  type Neo4jStats
} from '@/api/agent'

const health = ref<Neo4jHealth | null>(null)
const stats = ref<Neo4jStats | null>(null)
const healthLoading = ref(false)
const statsLoading = ref(false)

const cypher = ref('MATCH (n) RETURN labels(n) AS labels, count(*) AS cnt LIMIT 10')
const queryLoading = ref(false)
const queryResult = ref<Record<string, unknown>[]>([])

const loadHealth = async () => {
  healthLoading.value = true
  try {
    const res: any = await agentNeo4jHealth()
    health.value = res.data
    if (res.data?.reachable) {
      ElMessage.success('Neo4j 连接正常')
    } else {
      ElMessage.warning(res.data?.message || 'Neo4j 不可用')
    }
  } catch {
    health.value = null
  } finally {
    healthLoading.value = false
  }
}

const loadStats = async () => {
  statsLoading.value = true
  try {
    const res: any = await agentNeo4jStats()
    stats.value = res.data
  } catch {
    stats.value = null
  } finally {
    statsLoading.value = false
  }
}

const runQuery = async () => {
  const text = cypher.value.trim()
  if (!text) {
    ElMessage.warning('请输入 Cypher')
    return
  }
  queryLoading.value = true
  try {
    const res: any = await agentNeo4jReadCypher(text)
    queryResult.value = res.data ?? []
    ElMessage.success(`返回 ${queryResult.value.length} 行`)
  } catch {
    queryResult.value = []
  } finally {
    queryLoading.value = false
  }
}

const openBrowser = () => {
  const url = health.value?.browserUrl || 'http://127.0.0.1:7474'
  window.open(url, '_blank')
}

onMounted(async () => {
  await loadHealth()
  if (health.value?.reachable) {
    await loadStats()
  }
})
</script>

<template>
  <div class="neo4j-page">
    <el-card shadow="never" class="status-card">
      <div class="status-row">
        <div class="status-main">
          <h2 class="page-title">Neo4j 图数据库</h2>
          <p class="page-subtitle">通过 raccoon-cloud-agent 连接本地 Neo4j（Bolt 协议）</p>
        </div>
        <div class="status-metrics">
          <div class="metric">
            <span class="metric-label">服务状态</span>
            <el-tag v-if="health?.reachable" type="success" size="small">可用</el-tag>
            <el-tag v-else type="danger" size="small">不可用</el-tag>
          </div>
          <div class="metric">
            <span class="metric-label">版本</span>
            <span class="metric-value">{{ health?.version ?? '—' }}</span>
          </div>
          <div class="metric metric--wide">
            <span class="metric-label">Bolt</span>
            <span class="metric-value metric-value--mono">{{ health?.uri ?? '—' }}</span>
          </div>
        </div>
        <div class="status-actions">
          <el-button :loading="healthLoading" @click="loadHealth">检测连接</el-button>
          <el-button :loading="statsLoading" :disabled="!health?.reachable" @click="loadStats">
            刷新统计
          </el-button>
          <el-button @click="openBrowser">打开 Browser</el-button>
        </div>
      </div>
      <p v-if="health?.message" class="status-hint">{{ health.message }}</p>
    </el-card>

    <div class="page-body">
      <el-card shadow="never" class="stats-card">
        <template #header>图统计</template>
        <el-descriptions v-if="stats" :column="1" border size="small">
          <el-descriptions-item label="节点数">{{ stats.nodeCount }}</el-descriptions-item>
          <el-descriptions-item label="关系数">{{ stats.relationshipCount }}</el-descriptions-item>
          <el-descriptions-item label="节点标签">
            <el-tag v-for="l in stats.labels" :key="l" size="small" class="tag-item">{{ l }}</el-tag>
            <span v-if="!stats.labels.length" class="muted">暂无</span>
          </el-descriptions-item>
          <el-descriptions-item label="关系类型">
            <el-tag v-for="t in stats.relationshipTypes" :key="t" type="info" size="small" class="tag-item">
              {{ t }}
            </el-tag>
            <span v-if="!stats.relationshipTypes.length" class="muted">暂无</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="连接成功后自动加载统计" />
      </el-card>

      <el-card shadow="never" class="query-card">
        <template #header>只读 Cypher 查询</template>
        <el-input
          v-model="cypher"
          type="textarea"
          :rows="5"
          placeholder="仅支持 MATCH / RETURN / CALL 等只读语句"
          class="cypher-input"
        />
        <div class="query-actions">
          <span class="tip">禁止 CREATE / MERGE / DELETE 等写入操作</span>
          <el-button type="primary" :loading="queryLoading" :disabled="!health?.reachable" @click="runQuery">
            执行查询
          </el-button>
        </div>
        <el-table
          v-if="queryResult.length"
          :data="queryResult"
          size="small"
          border
          stripe
          class="result-table"
          max-height="360"
        >
          <el-table-column
            v-for="key in Object.keys(queryResult[0] || {})"
            :key="key"
            :prop="key"
            :label="key"
            min-width="120"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ typeof row[key] === 'object' ? JSON.stringify(row[key]) : row[key] }}
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="!queryLoading" description="执行查询后在此展示结果" />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.neo4j-page {
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
  flex: 1 1 320px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px 28px;
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

.tag-item {
  margin: 0 4px 4px 0;
}

.muted {
  color: #909399;
  font-size: 13px;
}

.cypher-input {
  margin-bottom: 8px;
}

.query-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.tip {
  font-size: 12px;
  color: #909399;
}

.result-table {
  width: 100%;
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
