<script setup lang="ts">
defineProps<{
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp?: number
  loading?: boolean
}>()

const formatTime = (ts?: number) => {
  if (!ts) return ''
  return new Date(ts).toLocaleString()
}
</script>

<template>
  <div :class="['chat-bubble', `chat-bubble--${role}`]">
    <div class="chat-bubble__avatar">
      <el-icon v-if="role === 'user'"><User /></el-icon>
      <el-icon v-else><MagicStick /></el-icon>
    </div>
    <div class="chat-bubble__body">
      <div class="chat-bubble__content">
        <span v-if="loading" class="chat-bubble__loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          AI 正在思考...
        </span>
        <span v-else style="white-space: pre-wrap">{{ content }}</span>
      </div>
      <div v-if="timestamp" class="chat-bubble__time">{{ formatTime(timestamp) }}</div>
      <slot name="extra" />
    </div>
  </div>
</template>

<style scoped>
.chat-bubble {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  align-items: flex-start;
}

.chat-bubble--user {
  flex-direction: row-reverse;
}

.chat-bubble__avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #fff;
  flex-shrink: 0;
}

.chat-bubble--user .chat-bubble__avatar {
  background: #409eff;
}

.chat-bubble--assistant .chat-bubble__avatar {
  background: #2b3a4a;
}

.chat-bubble__body {
  max-width: 78%;
}

.chat-bubble__content {
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.65;
  color: #303133;
}

.chat-bubble--user .chat-bubble__content {
  background: #ecf5ff;
  color: #1f6feb;
}

.chat-bubble--assistant .chat-bubble__content {
  background: #f4f4f5;
}

.chat-bubble__loading {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #909399;
}

.chat-bubble__time {
  margin-top: 6px;
  font-size: 12px;
  color: #c0c4cc;
  text-align: right;
}

.chat-bubble--user .chat-bubble__time {
  text-align: left;
}
</style>
