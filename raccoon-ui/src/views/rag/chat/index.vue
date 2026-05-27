<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ragChat, type RagChatResponse, type RagReference } from '@/api/rag'
import DeviceSelector from '@/components/rag/DeviceSelector.vue'
import ChatBubble from '@/components/rag/ChatBubble.vue'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  loading?: boolean
  references?: RagReference[]
  deviceContext?: RagChatResponse['deviceContext']
  elapsedMs?: number
  model?: string
  error?: string
}

interface ChatSession {
  id: string
  title: string
  createdAt: number
  deviceId?: string
  messages: ChatMessage[]
}

const STORAGE_KEY = 'rag-chat-sessions-v1'

const sessions = ref<ChatSession[]>([])
const activeId = ref<string>('')
const inputText = ref('')
const sending = ref(false)
const topK = ref(5)
const chatBodyRef = ref<HTMLElement | null>(null)

const activeSession = computed(
  () => sessions.value.find((s) => s.id === activeId.value) || sessions.value[0]
)

const restoreFromStorage = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return false
    const data = JSON.parse(raw) as ChatSession[]
    if (Array.isArray(data) && data.length) {
      sessions.value = data
      activeId.value = data[0].id
      return true
    }
  } catch (e) {
    console.warn('恢复 RAG 会话失败', e)
  }
  return false
}

const persist = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.value))
  } catch (e) {
    console.warn('保存 RAG 会话失败', e)
  }
}

const genId = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `s-${Date.now()}-${Math.random().toString(16).slice(2)}`

const newSession = (deviceId?: string): ChatSession => ({
  id: genId(),
  title: '新会话',
  createdAt: Date.now(),
  deviceId,
  messages: []
})

const createSession = () => {
  const s = newSession()
  sessions.value.unshift(s)
  activeId.value = s.id
  persist()
}

const switchSession = (id: string) => {
  activeId.value = id
}

const deleteSession = async (s: ChatSession) => {
  try {
    await ElMessageBox.confirm(`确定删除会话 "${s.title}" ？`, '删除会话', { type: 'warning' })
  } catch {
    return
  }
  sessions.value = sessions.value.filter((x) => x.id !== s.id)
  if (activeId.value === s.id) {
    activeId.value = sessions.value[0]?.id ?? ''
  }
  if (!sessions.value.length) {
    createSession()
  }
  persist()
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

const sendMessage = async (retryFromUser?: ChatMessage) => {
  const session = activeSession.value
  if (!session) return
  const text = retryFromUser?.content ?? inputText.value.trim()
  if (!text) {
    ElMessage.warning('请输入问题')
    return
  }
  if (!retryFromUser) {
    const userMsg: ChatMessage = {
      id: genId(),
      role: 'user',
      content: text,
      timestamp: Date.now()
    }
    session.messages.push(userMsg)
    inputText.value = ''
    if (session.title === '新会话' || !session.title) {
      session.title = text.length > 24 ? text.slice(0, 24) + '...' : text
    }
  }

  const assistantMsg: ChatMessage = {
    id: genId(),
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    loading: true
  }
  session.messages.push(assistantMsg)
  persist()
  await scrollToBottom()

  sending.value = true
  try {
    const res: any = await ragChat({
      question: text,
      deviceId: session.deviceId || undefined,
      topK: topK.value
    })
    const data = res.data as RagChatResponse
    assistantMsg.content = data?.answer ?? ''
    assistantMsg.references = data?.references ?? []
    assistantMsg.deviceContext = data?.deviceContext
    assistantMsg.elapsedMs = data?.elapsedMs
    assistantMsg.model = data?.model
  } catch (e: any) {
    assistantMsg.error = e?.message ?? '问答失败'
    assistantMsg.content = `（生成失败：${assistantMsg.error}）`
  } finally {
    assistantMsg.loading = false
    sending.value = false
    persist()
    await scrollToBottom()
  }
}

const retry = (msg: ChatMessage) => {
  const session = activeSession.value
  if (!session) return
  const idx = session.messages.findIndex((m) => m.id === msg.id)
  if (idx <= 0) return
  const userMsg = session.messages[idx - 1]
  if (!userMsg || userMsg.role !== 'user') return
  session.messages.splice(idx, 1)
  sendMessage(userMsg)
}

const formatScore = (s?: number) => (s == null ? '-' : s.toFixed(4))

const refsByGroup = (refs?: RagReference[]) => {
  const vector = (refs || []).filter((r) => r.source === 'vector')
  const graph = (refs || []).filter((r) => r.source === 'graph')
  return { vector, graph }
}

const onDeviceChange = (val?: string) => {
  if (activeSession.value) {
    activeSession.value.deviceId = val
    persist()
  }
}

watch(activeId, scrollToBottom)

onMounted(() => {
  if (!restoreFromStorage()) {
    sessions.value = [newSession()]
    activeId.value = sessions.value[0].id
    persist()
  }
  scrollToBottom()
})
</script>

<template>
  <div class="rag-chat-page">
    <!-- 会话列表 -->
    <div class="sessions">
      <div class="sessions__header">
        <span>历史会话</span>
        <el-button size="small" type="primary" @click="createSession">新建</el-button>
      </div>
      <div class="sessions__list">
        <div
          v-for="s in sessions"
          :key="s.id"
          :class="['session-item', { 'session-item--active': s.id === activeId }]"
          @click="switchSession(s.id)"
        >
          <div class="session-item__title">{{ s.title || '未命名会话' }}</div>
          <div class="session-item__meta">
            <span>{{ new Date(s.createdAt).toLocaleString() }}</span>
            <el-icon class="session-item__del" @click.stop="deleteSession(s)">
              <Delete />
            </el-icon>
          </div>
        </div>
        <el-empty v-if="!sessions.length" description="暂无会话" />
      </div>
    </div>

    <!-- 对话区 -->
    <div class="chat">
      <div class="chat__header">
        <div class="chat__title">
          智能问答
          <span class="chat__subtitle">设备语境 + 向量检索 + 图谱关联</span>
        </div>
        <div class="chat__toolbar">
          <div class="device-picker">
            <span class="picker-label">关注设备：</span>
            <div style="width: 260px">
              <DeviceSelector
                :model-value="activeSession?.deviceId"
                placeholder="（可选）从设备管理中选择"
                @change="onDeviceChange"
              />
            </div>
          </div>
          <div class="topk-picker">
            <span class="picker-label">TopK：</span>
            <el-input-number v-model="topK" :min="1" :max="20" size="small" />
          </div>
        </div>
      </div>

      <div ref="chatBodyRef" class="chat__body">
        <el-empty
          v-if="!activeSession || !activeSession.messages.length"
          description="向 AI 提问巡检/故障/规程相关问题，例如：主变温度高如何处置？"
        />
        <template v-else>
          <template v-for="m in activeSession.messages" :key="m.id">
            <ChatBubble
              :role="m.role"
              :content="m.content"
              :timestamp="m.timestamp"
              :loading="m.loading"
            >
              <template v-if="m.role === 'assistant' && !m.loading" #extra>
                <div v-if="m.error" class="msg-error">
                  <el-button size="small" type="primary" link @click="retry(m)">重试</el-button>
                </div>
                <el-collapse
                  v-if="m.references && m.references.length"
                  class="ref-collapse"
                >
                  <el-collapse-item
                    :title="`引用文档 / 图谱信息（${m.references.length} 条 · 耗时 ${m.elapsedMs ?? '-'} ms · 模型 ${m.model ?? '-'}）`"
                    name="refs"
                  >
                    <div v-if="refsByGroup(m.references).vector.length" class="ref-section">
                      <div class="ref-section__title">向量检索片段</div>
                      <div
                        v-for="(r, idx) in refsByGroup(m.references).vector"
                        :key="`v-${idx}`"
                        class="ref-card"
                      >
                        <div class="ref-card__meta">
                          <el-tag size="small" type="success">相似度 {{ formatScore(r.score) }}</el-tag>
                          <span v-if="r.fileName">{{ r.fileName }}</span>
                          <span v-if="r.chunkIndex != null">#chunk-{{ r.chunkIndex }}</span>
                          <el-link
                            v-if="r.minioUrl"
                            :href="r.minioUrl"
                            target="_blank"
                            type="primary"
                          >
                            打开原文
                          </el-link>
                        </div>
                        <p class="ref-card__content">{{ r.content }}</p>
                      </div>
                    </div>
                    <div v-if="refsByGroup(m.references).graph.length" class="ref-section">
                      <div class="ref-section__title">图谱关联信息</div>
                      <ul class="ref-graph">
                        <li v-for="(r, idx) in refsByGroup(m.references).graph" :key="`g-${idx}`">
                          {{ r.content }}
                        </li>
                      </ul>
                    </div>
                    <div v-if="m.deviceContext?.device" class="ref-section">
                      <div class="ref-section__title">设备上下文</div>
                      <div class="device-ctx">
                        <el-descriptions :column="2" border size="small">
                          <el-descriptions-item label="设备 ID">
                            {{ m.deviceContext.device.deviceId }}
                          </el-descriptions-item>
                          <el-descriptions-item label="名称">
                            {{ m.deviceContext.device.name || '-' }}
                          </el-descriptions-item>
                          <el-descriptions-item label="类型">
                            {{ m.deviceContext.device.type || '-' }}
                          </el-descriptions-item>
                          <el-descriptions-item label="站点">
                            {{ m.deviceContext.device.station || '-' }}
                          </el-descriptions-item>
                        </el-descriptions>
                      </div>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </template>
            </ChatBubble>
          </template>
        </template>
      </div>

      <div class="chat__input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="3"
          placeholder="输入问题，Ctrl/Cmd + Enter 发送"
          resize="none"
          :disabled="sending"
          @keydown.enter.exact.prevent="sendMessage()"
          @keydown.ctrl.enter.exact.prevent="sendMessage()"
          @keydown.meta.enter.exact.prevent="sendMessage()"
        />
        <div class="chat__input-actions">
          <span class="tips">提示：左上方下拉选择设备可以获得更精准的图谱关联回答</span>
          <el-button type="primary" :loading="sending" @click="sendMessage()">
            {{ sending ? 'AI 思考中...' : '发送 ' }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rag-chat-page {
  display: flex;
  gap: 16px;
  height: calc(100vh - 110px);
  padding: 16px 20px;
}

.sessions {
  width: 260px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sessions__header {
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
}

.sessions__list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 10px 12px;
  margin-bottom: 6px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.2s;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item--active {
  background: #ecf5ff;
  border-color: #d9ecff;
}

.session-item__title {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.session-item__meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.session-item__del {
  cursor: pointer;
}
.session-item__del:hover {
  color: #f56c6c;
}

.chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  overflow: hidden;
}

.chat__header {
  padding: 14px 18px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.chat__title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.chat__subtitle {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.chat__toolbar {
  display: flex;
  align-items: center;
  gap: 18px;
}

.device-picker,
.topk-picker {
  display: flex;
  align-items: center;
}

.picker-label {
  color: #606266;
  font-size: 13px;
  margin-right: 6px;
}

.chat__body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  background: #fafbfc;
}

.chat__input {
  border-top: 1px solid #ebeef5;
  padding: 12px 16px;
  background: #fff;
}

.chat__input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.tips {
  font-size: 12px;
  color: #909399;
}

.msg-error {
  margin-top: 4px;
}

.ref-collapse {
  margin-top: 8px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #ebeef5;
}

.ref-section {
  margin-bottom: 12px;
}

.ref-section__title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  margin-bottom: 8px;
}

.ref-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fafbfc;
}

.ref-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #606266;
}

.ref-card__content {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
}

.ref-graph {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: #303133;
  line-height: 1.7;
}

.device-ctx :deep(.el-descriptions__title) {
  display: none;
}

@media (max-width: 1100px) {
  .rag-chat-page {
    flex-direction: column;
    height: auto;
  }
  .sessions {
    width: 100%;
    max-height: 240px;
  }
}
</style>
