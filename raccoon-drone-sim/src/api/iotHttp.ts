interface HxResult {
  code?: number
  msg?: string
}

/** 解析 iot-data 接口失败原因（含 Vite 代理 ECONNREFUSED 时的 500） */
export async function readIotApiError(res: Response, fallback: string): Promise<string> {
  const ct = res.headers.get('content-type') ?? ''
  try {
    if (ct.includes('application/json')) {
      const json = (await res.json()) as HxResult
      if (json.msg) return json.msg
    } else {
      const text = (await res.text()).trim()
      if (text) return text.length > 240 ? `${text.slice(0, 240)}…` : text
    }
  } catch {
    /* ignore parse errors */
  }
  if (res.status >= 500) {
    return `${fallback}：请确认已启动 raccoon-cloud-iot-data（端口 8092），并已执行 db 建表 SQL`
  }
  return `${fallback} (HTTP ${res.status})`
}
