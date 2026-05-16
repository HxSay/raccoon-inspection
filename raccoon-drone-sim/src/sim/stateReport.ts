import { TELEMETRY_INTERVAL_MS } from './constants'
import type { TelemetryPayload } from './types'

/**
 * 模拟边缘端 -> 云端遥测上报（默认 1Hz）；断网时写入缓存队列，恢复后按序补报。
 * 不上传照片原图，仅结构化遥测（位置/电量/状态等，由 sink 写入 uav_location_history）。
 */

type CloudSink = (p: TelemetryPayload) => void

export class StateReportService {
  private online = true
  private buffer: TelemetryPayload[] = []
  private timer: ReturnType<typeof setInterval> | null = null
  private lastSample: TelemetryPayload | null = null
  private sink: CloudSink = () => {}

  /** 注册“云端接收”回调（仿真里打印到内存列表即可） */
  setCloudSink(fn: CloudSink): void {
    this.sink = fn
  }

  /** 模拟链路是否连通 */
  setOnline(online: boolean): void {
    const was = this.online
    this.online = online
    if (!was && online) {
      this.flushBuffer()
    }
  }

  isOnline(): boolean {
    return this.online
  }

  /** 启动定时上报：每次取最后一次采样值发送（默认 1s 一条） */
  start(): void {
    if (this.timer) return
    this.timer = setInterval(() => {
      if (!this.lastSample) return
      this.enqueue(this.lastSample)
    }, TELEMETRY_INTERVAL_MS)
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  }

  /**
   * 飞控主循环调用：更新最新遥测采样（定时器按 TELEMETRY_INTERVAL_MS 读取并上报）。
   */
  sample(payload: TelemetryPayload): void {
    this.lastSample = { ...payload, t: Date.now() }
  }

  private enqueue(p: TelemetryPayload): void {
    if (this.online) {
      this.sink(p)
      this.flushBuffer()
    } else {
      this.buffer.push({ ...p, t: Date.now() })
    }
  }

  /** 网络恢复后发送积压报文 */
  private flushBuffer(): void {
    if (!this.online) return
    while (this.buffer.length) {
      const q = this.buffer.shift()
      if (q) this.sink(q)
    }
  }

  clearBuffer(): void {
    this.buffer = []
  }

  getBufferedCount(): number {
    return this.buffer.length
  }
}
