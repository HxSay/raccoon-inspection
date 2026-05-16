import { postUavInspectionMultimodal, type UavInspectionUploadBody } from '@/api/uavInspection'
import type { MissionReport } from './types'
import type { MultimodalMissionContext, MultimodalSample } from './multimodalTypes'

function formatLocalDateTime(ms: number): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function round6(n: number) {
  return Math.round(n * 1_000_000) / 1_000_000
}

/**
 * 任务结束后将边缘采集的多模态巡检结果批量上报 raccoon-cloud-iot-data。
 */
export async function uploadMultimodalMissionResult(
  report: MissionReport,
  ctx: MultimodalMissionContext
): Promise<{ sessionId: number; sampleCount: number }> {
  const samples = report.multimodalSamples ?? []
  if (!samples.length) {
    throw new Error('无多模态采样数据可上报')
  }

  const body: UavInspectionUploadBody = {
    session: {
      uavId: ctx.uavId,
      taskId: ctx.taskId,
      planId: ctx.planId,
      mapId: ctx.mapId,
      startedAt: formatLocalDateTime(report.startedAt),
      finishedAt: formatLocalDateTime(report.finishedAt),
      distanceM: Math.round(report.distanceM * 10) / 10
    },
    samples: samples.map((s) => toUploadSample(s))
  }

  return postUavInspectionMultimodal(body)
}

/** 可见光/热成像：将缩略图 data URL 一并入库，供管理端预览 */
function buildPayloadForCloud(s: MultimodalSample): Record<string, unknown> {
  const out = { ...(s.payload as Record<string, unknown>) }
  if (s.modalityType === 'VISIBLE' || s.modalityType === 'THERMAL') {
    const thumb =
      s.previewDataUrl ||
      (typeof out.thumbnail === 'string' ? (out.thumbnail as string) : undefined)
    if (thumb) {
      out.thumbnail = thumb
    }
  }
  return out
}

function toUploadSample(s: MultimodalSample) {
  return {
    waypointIndex: s.waypointIndex,
    modalityType: s.modalityType,
    capturedAt: formatLocalDateTime(s.capturedAt),
    longitude: round6(s.gps.longitude),
    latitude: round6(s.gps.latitude),
    height: Math.round(s.gps.altitudeM * 100) / 100,
    payload: buildPayloadForCloud(s)
  }
}
