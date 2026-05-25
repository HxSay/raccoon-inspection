/**
 * Milvus 测试页：统一解析接口错误并输出到控制台，便于调试。
 */
export interface MilvusTestErrorLog {
  action: string
  time: string
  message?: string
  httpStatus?: number
  httpStatusText?: string
  url?: string
  method?: string
  /** 后端 HxResult 或 Spring 默认错误体 */
  responseData?: unknown
  /** 格式化后的展示文本 */
  displayText: string
}

export function logMilvusTestError(action: string, error: unknown): MilvusTestErrorLog {
  const err = error as {
    message?: string
    response?: {
      status?: number
      statusText?: string
      data?: Record<string, unknown>
    }
    config?: { url?: string; method?: string }
  }

  const responseData = err.response?.data
  const hxMsg =
    typeof responseData === 'object' && responseData !== null
      ? String((responseData as { msg?: string }).msg ?? (responseData as { message?: string }).message ?? '')
      : ''

  const parts: string[] = []
  if (err.response?.status) {
    parts.push(`HTTP ${err.response.status} ${err.response.statusText ?? ''}`.trim())
  }
  if (hxMsg) parts.push(hxMsg)
  else if (err.message) parts.push(err.message)

  if (typeof responseData === 'object' && responseData !== null) {
    const extra = (responseData as { error?: string }).error ?? (responseData as { detail?: string }).detail
    if (extra && !parts.join(' ').includes(String(extra))) {
      parts.push(String(extra))
    }
  }

  const log: MilvusTestErrorLog = {
    action,
    time: new Date().toISOString(),
    message: err.message,
    httpStatus: err.response?.status,
    httpStatusText: err.response?.statusText,
    url: err.config?.url,
    method: err.config?.method,
    responseData,
    displayText: parts.filter(Boolean).join('\n') || '未知错误'
  }

  console.group(`[MilvusTest] ${action} 失败`)
  console.error('摘要:', log.displayText)
  console.error('完整载荷:', log)
  if (responseData !== undefined) {
    console.error('response.data:', responseData)
  }
  console.groupEnd()

  return log
}
