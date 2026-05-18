<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  agentOllamaHealth,
  agentOllamaModels,
  agentOllamaChat,
  type OllamaHealth,
  type OllamaModel,
  type OllamaChatResponse
} from '@/api/agent'

interface ChatTurn {
  role: 'user' | 'assistant'
  content: string
  model?: string
  elapsedMs?: number
}

const health = ref<OllamaHealth | null>(null)
const healthLoading = ref(false)
const models = ref<OllamaModel[]>([])
const modelsLoading = ref(false)

const form = reactive({
  model: '',
  temperature: 0.7,
  message: ''
})

const chatting = ref(false)
const turns = ref<ChatTurn[]>([])

const loadHealth = async () => {
  healthLoading.value = true
  try {
    const res: any = await agentOllamaHealth()
    health.value = res.data
    if (res.data?.reachable) {
      ElMessage.success('Ollama 连接正常')
    } else {
      ElMessage.warning(res.data?.message || 'Ollama 不可用')
    }
  } catch {
    health.value = null
  } finally {
    healthLoading.value = false
  }
}

const loadModels = async () => {
  modelsLoading.value = true
  try {
    const res: any = await agentOllamaModels()
    models.value = res.data ?? []
    if (!form.model && models.value.length) {
      form.model = health.value?.defaultModel || models.value[0].name
    }
  } catch {
    models.value = []
  } finally {
    modelsLoading.value = false
  }
}

const sendMessage = async () => {
  const text = form.message.trim()
  if (!text) {
    ElMessage.warning('请输入测试内容')
    return
  }
  turns.value.push({ role: 'user', content: text })
  form.message = ''
  chatting.value = true
  try {
    const res: any = await agentOllamaChat({
      message: text,
      model: form.model || undefined,
      temperature: form.temperature
    })
    const data = res.data as OllamaChatResponse
    turns.value.push({
      role: 'assistant',
      content: data.content,
      model: data.model,
      elapsedMs: data.elapsedMs
    })
  } catch (e) {
    turns.value.push({
      role: 'assistant',
      content: e instanceof Error ? e.message : '请求失败'
    })
  } finally {
    chatting.value = false
  }
}

const clearHistory = () => {
  turns.value = []
}

onMounted(async () => {
  await loadHealth()
  await loadModels()
})
</script>

<template>
  <div class="ollama-test-page">
    <el-card shadow="never" class="mb-3">
      <template #header>
        <div class="card-header">
          <span>Ollama 大模型测试</span>
          <span class="hint">通过 raccoon-cloud-agent + Spring AI 调用本地 Ollama</span>
        </div>
      </template>

      <el-descriptions :column="2" border size="small" class="mb-3">
        <el-descriptions-item label="服务状态">
          <el-tag v-if="health?.reachable" type="success">可用</el-tag>
          <el-tag v-else type="danger">不可用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="默认模型">{{ health?.defaultModel ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="Ollama 地址" :span="2">{{ health?.baseUrl ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ health?.message ?? '—' }}</el-descriptions-item>
      </el-descriptions>

      <div class="toolbar">
        <el-button :loading="healthLoading" @click="loadHealth">检测连接</el-button>
        <el-button :loading="modelsLoading" @click="loadModels">刷新模型列表</el-button>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>参数</template>
          <el-form label-position="top">
            <el-form-item label="模型">
              <el-select
                v-model="form.model"
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入模型名"
                class="w-full"
                :loading="modelsLoading"
              >
                <el-option v-for="m in models" :key="m.name" :label="m.name" :value="m.name" />
              </el-select>
            </el-form-item>
            <el-form-item label="温度 (temperature)">
              <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
            <el-form-item label="已安装模型">
              <el-table :data="models" size="small" max-height="200" empty-text="暂无，请先启动 Ollama">
                <el-table-column prop="name" label="名称" show-overflow-tooltip />
              </el-table>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="chat-card">
          <template #header>
            <div class="card-header">
              <span>对话测试</span>
              <el-button type="primary" link @click="clearHistory">清空记录</el-button>
            </div>
          </template>

          <div class="chat-history">
            <el-empty v-if="!turns.length" description="发送一条消息开始测试" />
            <div
              v-for="(t, i) in turns"
              :key="i"
              class="chat-bubble"
              :class="t.role === 'user' ? 'is-user' : 'is-assistant'"
            >
              <div class="bubble-role">{{ t.role === 'user' ? '我' : '模型' }}</div>
              <pre class="bubble-content">{{ t.content }}</pre>
              <div v-if="t.model" class="bubble-meta">
                {{ t.model }}<span v-if="t.elapsedMs != null"> · {{ t.elapsedMs }} ms</span>
              </div>
            </div>
          </div>

          <div class="chat-input">
            <el-input
              v-model="form.message"
              type="textarea"
              :rows="4"
              placeholder="输入测试提示词，例如：用一句话介绍无人机输电巡检"
              :disabled="chatting"
              @keydown.ctrl.enter.prevent="sendMessage"
            />
            <div class="chat-actions">
              <span class="tip">Ctrl + Enter 发送</span>
              <el-button type="primary" :loading="chatting" @click="sendMessage">发送</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.ollama-test-page {
  max-width: 1200px;
}

.card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.card-header .hint {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}

.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.chat-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  min-height: 420px;
}

.chat-history {
  flex: 1;
  min-height: 240px;
  max-height: 360px;
  overflow-y: auto;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 12px;
}

.chat-bubble {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #ebeef5;
}

.chat-bubble.is-user {
  background: #ecf5ff;
  border-color: #d9ecff;
}

.bubble-role {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}

.bubble-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
}

.bubble-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.chat-input {
  margin-top: auto;
}

.chat-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.tip {
  font-size: 12px;
  color: #909399;
}

.w-full {
  width: 100%;
}
</style>
