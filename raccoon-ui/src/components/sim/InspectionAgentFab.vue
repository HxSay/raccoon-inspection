<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { dispatchTaskGenerate } from '@/api/droneDispatch'
import { nlpTaskParse, type NlpTaskParseResponse } from '@/api/droneNlp'
import type { UavRouteDispatchPayload } from '@/api/drone'
import ChatBubble from '@/components/rag/ChatBubble.vue'
import {
  formatTaskSummary,
  inspectionTaskToDispatch,
  MSG_INSPECTION_DISPATCH_ACK,
  MSG_INSPECTION_MISSION_COMPLETE,
  MSG_INSPECTION_MISSION_ERROR,
  MSG_INSPECTION_STATUS,
  postDispatchToSimIframe
} from '@/utils/inspectionBridge'

const props = defineProps<{
  /** 仿真 iframe，用于 postMessage 下发航线 */
  simIframe?: HTMLIFrameElement | null
}>()

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  loading?: boolean
  error?: string
}

const panelOpen = ref(false)
const inputText = ref('')
const sending = ref(false)
const messages = ref<ChatMessage[]>([])
const chatBodyRef = ref<HTMLElement | null>(null)
const missionRunning = ref(false)

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
  missionRunning.value = false
}

function onSimMessage(ev: MessageEvent) {
  const data = ev.data as { type?: string; status?: string; summary?: Record<string, unknown>; message?: string }
  if (!data?.type) return

  if (data.type === MSG_INSPECTION_DISPATCH_ACK) {
    missionRunning.value = true
    const n = (data as { waypointCount?: number }).waypointCount
    pushAssistant(`航线已加载（${n ?? '?'} 个航点），无人机正在起飞巡检…`)
    return
  }

  if (data.type === MSG_INSPECTION_STATUS && data.status) {
    if (
      data.status.includes('自主巡检') ||
      data.status.includes('拍照') ||
      data.status.includes('返航') ||
      data.status.includes('上报')
    ) {
      pushAssistant(`[状态] ${data.status}`, false)
    }
    return
  }

  if (data.type === MSG_INSPECTION_MISSION_COMPLETE && data.summary) {
    missionRunning.value = false
    const s = data.summary as {
      durationSec?: number
      distanceM?: number
      photoCount?: number
      telemetrySent?: number
      multimodalUploaded?: boolean
      multimodalError?: string
    }
    const uploadLine = s.multimodalUploaded
      ? '多模态巡检结果已上报云端。'
      : s.multimodalError
        ? `多模态上报：${s.multimodalError}`
        : '多模态上报未执行。'
    pushAssistant(
      [
        '巡检任务已完成。',
        `飞行 ${s.durationSec?.toFixed(0) ?? '—'} s，航程 ${s.distanceM?.toFixed(1) ?? '—'} m。`,
        `拍照 ${s.photoCount ?? 0} 次，遥测上报 ${s.telemetrySent ?? 0} 条。`,
        uploadLine
      ].join('\n')
    )
    return
  }

  if (data.type === MSG_INSPECTION_MISSION_ERROR) {
    missionRunning.value = false
    pushAssistant(`任务异常：${data.message ?? '未知错误'}`, true)
  }
}

function pushAssistant(content: string, isError = false) {
  messages.value.push({
    id: genId(),
    role: 'assistant',
    content,
    timestamp: Date.now(),
    error: isError ? content : undefined
  })
  void scrollToBottom()
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text) {
    ElMessage.warning('请输入巡检指令')
    return
  }
  if (sending.value) return
  if (missionRunning.value) {
    ElMessage.warning('无人机巡检进行中，请等待任务结束')
    return
  }

  messages.value.push({
    id: genId(),
    role: 'user',
    content: text,
    timestamp: Date.now()
  })
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
    let dispatchPayload: UavRouteDispatchPayload | null = null
    let summaryLines: string[] = []
    let parseSource = ''

    // 1) 调度中枢：自动分派终端 + 全局路径规划
    try {
      const hubRes: any = await dispatchTaskGenerate({
        userInput: text,
        enableSimulation: true,
        autoDispatch: true
      })
      const hub = hubRes.data
      if (hub?.assigned && hub?.workOrder?.payload) {
        dispatchPayload = hub.workOrder.payload
        parseSource = '调度中枢'
        summaryLines = [
          `任务：${hub.taskId ?? '—'}`,
          `分派终端：${hub.assignedTerminalName ?? hub.assignedTerminalId ?? '—'}`,
          hub.bidPrice != null ? `竞拍价：${hub.bidPrice.toFixed(3)}` : '',
          hub.priority ? `优先级：${hub.priority}` : '',
          hub.pathPlan?.distanceM != null
            ? `航程：${hub.pathPlan.distanceM.toFixed(1)} m / ${hub.pathPlan.durationSec ?? '—'} s`
            : ''
        ].filter(Boolean) as string[]
      } else if (hub?.message && hub.message !== 'OK') {
        summaryLines.push(`调度提示：${hub.message}`)
      }
    } catch (hubErr: unknown) {
      const msg = hubErr instanceof Error ? hubErr.message : String(hubErr)
      summaryLines.push(`调度中枢暂不可用（${msg}），将使用 NLP 解析…`)
    }

    // 2) NLP 解析兜底（含「所有杆塔」自动展开）
    const res: any = await nlpTaskParse(text)
    const data = res.data as NlpTaskParseResponse

    if (data?.needFollowUp) {
      assistantMsg.content = data.followUpQuestion ?? '请补充巡检区域与设备信息。'
      return
    }

    if (!data?.task && !dispatchPayload) {
      assistantMsg.content = '解析失败：未生成巡检任务，请换种说法重试。'
      assistantMsg.error = assistantMsg.content
      return
    }

    if (!dispatchPayload && data?.task) {
      dispatchPayload = inspectionTaskToDispatch(data.task)
      const summary = formatTaskSummary(data.task, data.slots)
      parseSource = data.parseSource === 'RULE' ? '规则解析' : 'LLM 解析'
      summaryLines = [`已理解巡检意图（${parseSource}）：`, summary]
    }

    assistantMsg.content =
      (summaryLines.length ? summaryLines.join('\n') : '任务已生成') +
      '\n\n正在向仿真无人机下发航线…'

    if (!props.simIframe) {
      assistantMsg.content += '\n\n（仿真 iframe 未就绪，无法启动飞行）'
      ElMessage.warning('仿真页面未加载完成')
      return
    }

    const posted = postDispatchToSimIframe(props.simIframe, dispatchPayload!, {
      autoStart: true,
      userInput: text
    })
    if (!posted) {
      assistantMsg.content += '\n\n下发失败：无法访问仿真窗口。'
      ElMessage.error('无法向仿真页下发指令')
    }
  } catch (e: any) {
    assistantMsg.error = e?.message ?? '任务解析失败'
    assistantMsg.content = `解析失败：${assistantMsg.error}`
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

onMounted(() => {
  window.addEventListener('message', onSimMessage)
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onSimMessage)
})

watch(panelOpen, (open) => {
  if (open) scrollToBottom()
})
</script>

<template>
  <div class="agent-fab-root">
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

        <div class="agent-panel__hint">
          用自然语言下达巡检任务，例如：
          <em>明天上午对输电线路场景的杆塔1和杆塔2做例行巡检</em>
        </div>

        <div ref="chatBodyRef" class="agent-panel__body">
          <div v-if="!messages.length" class="agent-panel__empty">
            <p>我是巡检任务 Agent，可：</p>
            <ul>
              <li>解析自然语言中的区域、设备、优先级</li>
              <li>自动生成航线并指挥仿真无人机起飞</li>
              <li>任务结束后上报遥测与多模态巡检结果</li>
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
            placeholder="输入巡检指令，如：紧急复巡变电站场景的主变压器和断路器"
            :disabled="sending || missionRunning"
            resize="none"
            @keydown="onKeydown"
          />
          <el-button
            type="primary"
            class="agent-panel__send"
            :loading="sending"
            :disabled="missionRunning"
            @click="sendMessage"
          >
            {{ missionRunning ? '巡检中…' : '下达指令' }}
          </el-button>
        </div>
      </div>
    </transition>

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

.agent-panel__hint {
  flex-shrink: 0;
  padding: 8px 12px;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
}

.agent-panel__hint em {
  font-style: normal;
  color: #409eff;
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
