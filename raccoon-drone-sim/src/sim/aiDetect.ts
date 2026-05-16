import { SCENE_TO_LAT_LON } from './constants'
import type { AiDefectResult, PhotoCaptureMeta } from './types'

/**
 * 模拟边缘侧本地 AI 缺陷检测：不将原图上传云端，仅返回结构化结果。
 * 推理延迟用随机区间模拟 NPU 负载波动。
 */

function randomBetween(a: number, b: number): number {
  return a + Math.random() * (b - a)
}

/**
 * 场景坐标转伪 GPS（用于照片 EXIF/元数据展示，非真实测绘）。
 */
export function sceneToGps(x: number, y: number, z: number): { latitude: number; longitude: number; altitudeM: number } {
  const baseLat = 30.5 + x * SCENE_TO_LAT_LON.latPerMeter
  const baseLon = 104.0 + z * SCENE_TO_LAT_LON.lonPerMeter
  return { latitude: baseLat, longitude: baseLon, altitudeM: y }
}

/**
 * 生成拍照元数据（含云台角）；可选附带仿真离屏截图 data URL。
 */
export function buildPhotoMeta(args: {
  waypointIndex: number
  position: { x: number; y: number; z: number }
  gimbalPitchDeg: number
  gimbalYawDeg: number
  /** 仿真相机离屏渲染 JPEG data URL */
  imageDataUrl?: string
}): PhotoCaptureMeta {
  const gps = sceneToGps(args.position.x, args.position.y, args.position.z)
  return {
    id: `IMG-${Date.now()}-${args.waypointIndex}`,
    waypointIndex: args.waypointIndex,
    timestamp: Date.now(),
    gps,
    gimbal: {
      pitchDeg: args.gimbalPitchDeg,
      yawDeg: args.gimbalYawDeg,
      rollDeg: 0
    },
    ...(args.imageDataUrl ? { imageDataUrl: args.imageDataUrl } : {})
  }
}

/**
 * 本地推理（异步）：固定小延迟 + 随机缺陷标签。
 */
export async function runLocalAiDetect(photo: PhotoCaptureMeta): Promise<AiDefectResult> {
  const inferenceMs = randomBetween(35, 120)
  await new Promise((r) => setTimeout(r, inferenceMs))
  const hasDefect = Math.random() < 0.35
  const labels = ['绝缘子破损', '导线异物', '销钉脱落', '正常']
  const label = hasDefect ? labels[Math.floor(Math.random() * 3)] : labels[3]
  return {
    photoId: photo.id,
    hasDefect,
    label,
    confidence: randomBetween(0.72, 0.99),
    inferenceMs: Math.round(inferenceMs)
  }
}
