<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ragChat, type RagChatResponse } from '@/api/rag'
import ChatBubble from '@/components/rag/ChatBubble.vue'
import DeviceSelector from '@/components/rag/DeviceSelector.vue'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  loading?: boolean
  error?: string
  elapsedMs?: number
  model?: string
}

const panelOpen = ref(false)
const inputText = ref('')
const sending = ref(false)
const deviceId = ref<string | undefined>(undefined)
const messages = ref<ChatMessage[]>([])
const chatBodyRef = ref<HTMLElement | null>(null)

const genId = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `m-${Date.now()}-${Math.random().toString(16).slice(2)}`

const scrollToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

const togglePanel = () => {
  panelOpen.value = !panelOpen.value
}

const clearChat = () => {
  messages.value = []
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text) {
    ElMessage.warning('请输入问题')
    return
  }
  if (sending.value) return

  const userMsg: ChatMessage = {
    id: genId(),
    role: 'user',
    content: text,
    timestamp: Date.now()
  }
  messages.value.push(userMsg)
  inputText.value = ''

  const assistantMsg: ChatMessage = {
    id: genId(),
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    loading: true
  }
  messages.value.push(assistantMsg)
  await scrollToBottom()

  sending.value = true
  try {
    const res: any = await ragChat({
      question: text,
      deviceId: deviceId.value || undefined,
      topK: 5
    })
    const data = res.data as RagChatResponse
    assistantMsg.content = data?.answer ?? '（无回答内容）'
    assistantMsg.elapsedMs = data?.elapsedMs
    assistantMsg.model = data?.model
  } catch (e: any) {
    assistantMsg.error = e?.message ?? '问答失败'
    assistantMsg.content = `生成失败：${assistantMsg.error}`
    ElMessage.error(assistantMsg.error)
  } finally {
    assistantMsg.loading = false
    sending.value = false
    await scrollToBottom()
  }
}

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

watch(panelOpen, (open) => {
  if (open) scrollToBottom()
})
</script>

<template>
  <div class="agent-fab-root">
    <!-- 问答面板 -->
    <transition name="agent-panel">
      <div v-show="panelOpen" class="agent-panel">
        <div class="agent-panel__header">
          <div class="agent-panel__title">
            <el-icon class="agent-panel__icon"><MagicStick /></el-icon>
            <span>智能巡检 Agent</span>
          </div>
          <div class="agent-panel__actions">
            <el-button link type="info" @click="clearChat">清空</el-button>
            <el-button link type="info" @click="panelOpen = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="agent-panel__device">
          <span class="agent-panel__label">关联设备</span>
          <DeviceSelector
            v-model="deviceId"
            placeholder="可选：按设备过滤知识库"
            clearable
          />
        </div>

        <div ref="chatBodyRef" class="agent-panel__body">
          <div v-if="!messages.length" class="agent-panel__empty">
            <p>我是巡检 AI 助手，可解答：</p>
            <ul>
              <li>设备故障与处置建议</li>
              <li>巡检规程与运维知识</li>
              <li>仿真场景中的杆塔 / 无人机相关问题</li>
            </ul>
          </div>
          <template v-else>
            <ChatBubble
              v-for="m in messages"
              :key="m.id"
              :role="m.role"
              :content="m.content"
              :timestamp="m.timestamp"
              :loading="m.loading"
            />
          </template>
        </div>

        <div class="agent-panel__footer">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            placeholder="输入巡检相关问题，Enter 发送，Shift+Enter 换行"
            :disabled="sending"
            resize="none"
            @keydown="onKeydown"
          />
          <el-button
            type="primary"
            class="agent-panel__send"
            :loading="sending"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
      </div>
    </transition>

    <!-- 悬浮气泡 -->
    <button
      type="button"
      class="agent-fab"
      :class="{ 'agent-fab--active': panelOpen }"
      title="智能巡检 Agent"
      @click="togglePanel"
    >
      <el-icon :size="26"><ChatDotRound /></el-icon>
      <span v-if="!panelOpen" class="agent-fab__label">AI 助手</span>
    </button>
  </div>
</template>

<style scoped>
.agent-fab-root {
  position: absolute;
  right: 20px;
  bottom: 24px;
  z-index: 30;
  pointer-events: none;
}

.agent-fab-root > * {
  pointer-events: auto;
}

.agent-fab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.45);
  transition: transform 0.2s, box-shadow 0.2s;
}

.agent-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.55);
}

.agent-fab--active {
  background: linear-gradient(135deg, #606266 0%, #303133 100%);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
}

.agent-fab__label {
  white-space: nowrap;
}

.agent-panel {
  position: absolute;
  right: 0;
  bottom: 64px;
  width: 400px;
  max-width: calc(100vw - 40px);
  height: 520px;
  max-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.28);
  border: 1px solid #e4e7ed;
}

.agent-panel__header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: linear-gradient(135deg, #304156 0%, #1f2d3d 100%);
  color: #fff;
}

.agent-panel__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.agent-panel__icon {
  font-size: 18px;
  color: #79bbff;
}

.agent-panel__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.agent-panel__actions .el-button {
  color: #c0c4cc;
}

.agent-panel__actions .el-button:hover {
  color: #fff;
}

.agent-panel__device {
  flex-shrink: 0;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}

.agent-panel__label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.agent-panel__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  background: #f5f7fa;
}

.agent-panel__empty {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.agent-panel__empty p {
  margin: 0 0 8px;
  font-weight: 500;
}

.agent-panel__empty ul {
  margin: 0;
  padding-left: 18px;
}

.agent-panel__footer {
  flex-shrink: 0;
  padding: 10px 12px;
  border-top: 1px solid #ebeef5;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.agent-panel__send {
  align-self: flex-end;
}

.agent-panel-enter-active,
.agent-panel-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.agent-panel-enter-from,
.agent-panel-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.96);
}
</style>
