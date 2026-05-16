import { postUavLocationBatch, type UavLocationPoint } from '@/api/uavLocation'
import { sceneToGps } from './aiDetect'
import type { TelemetryPayload } from './types'

/** 边缘上报云端时的任务上下文 */
export interface EdgeTelemetryContext {
  uavId: number
  taskId?: number
  mapId?: number
}

/**
 * 边缘端遥测→云端 uav_location_history（1Hz，由 StateReportService 定时触发）。
 */
export class EdgeCloudTelemetryReporter {
  private ctx: EdgeTelemetryContext
  private online = true
  private inflight = false
  private pending: UavLocationPoint[] = []

  constructor(ctx: EdgeTelemetryContext) {
    this.ctx = { ...ctx }
  }

  updateContext(ctx: Partial<EdgeTelemetryContext>): void {
    this.ctx = { ...this.ctx, ...ctx }
  }

  setOnline(online: boolean): void {
    const was = this.online
    this.online = online
    if (!was && online) {
      void this.flushPending()
    }
  }

  /** StateReportService 云端 sink 回调 */
  handleTelemetry(p: TelemetryPayload): void {
    const gps = sceneToGps(p.position.x, p.position.y, p.position.z)
    const point: UavLocationPoint = {
      uavId: this.ctx.uavId,
      taskId: this.ctx.taskId,
      mapId: this.ctx.mapId,
      longitude: round6(gps.longitude),
      latitude: round6(gps.latitude),
      height: Math.round(gps.altitudeM * 100) / 100,
      speed: Math.round(p.speedMps * 100) / 100,
      battery: Math.round(p.batteryPercent * 100) / 100,
      locationMode: p.rtkMode,
      flightStatus: p.phase,
      createTime: formatLocalDateTime(p.t)
    }
    if (!this.online) {
      this.pending.push(point)
      return
    }
    void this.sendOne(point)
  }

  private async sendOne(point: UavLocationPoint): Promise<void> {
    if (this.inflight) {
      this.pending.push(point)
      return
    }
    this.inflight = true
    try {
      await postUavLocationBatch([point])
    } catch (e) {
      console.warn('[edge→cloud] uav_location_history', e)
      this.pending.push(point)
    } finally {
      this.inflight = false
      if (this.online && this.pending.length) {
        void this.flushPending()
      }
    }
  }

  private async flushPending(): Promise<void> {
    while (this.online && this.pending.length && !this.inflight) {
      const point = this.pending.shift()!
      await this.sendOne(point)
    }
  }
}

function round6(n: number) {
  return Math.round(n * 1_000_000) / 1_000_000
}

function formatLocalDateTime(ms: number): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
