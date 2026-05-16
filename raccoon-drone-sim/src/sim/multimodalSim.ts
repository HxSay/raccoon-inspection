import type { PhotoCaptureMeta } from './types'
import type { MultimodalModalityType, MultimodalSample } from './multimodalTypes'

function randomBetween(a: number, b: number): number {
  return a + Math.random() * (b - a)
}

/** 缩小 JPEG 预览，控制上报体积 */
export function shrinkDataUrl(dataUrl: string, maxW = 320, quality = 0.55): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const scale = Math.min(1, maxW / img.width)
      const w = Math.max(1, Math.floor(img.width * scale))
      const h = Math.max(1, Math.floor(img.height * scale))
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')!
      ctx.drawImage(img, 0, 0, w, h)
      resolve(canvas.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => reject(new Error('缩略图生成失败'))
    img.src = dataUrl
  })
}

/** 由可见光图生成伪热成像预览 */
export async function synthesizeThermalPreview(visibleDataUrl: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const w = Math.min(320, img.width)
      const h = Math.floor((img.height / img.width) * w)
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')!
      ctx.drawImage(img, 0, 0, w, h)
      const data = ctx.getImageData(0, 0, w, h)
      for (let i = 0; i < data.data.length; i += 4) {
        const lum = (data.data[i]! * 0.299 + data.data[i + 1]! * 0.587 + data.data[i + 2]! * 0.114) / 255
        const t = Math.pow(lum, 0.85)
        const r = Math.min(255, Math.floor(255 * Math.max(0, (t - 0.45) * 2.2)))
        const g = Math.min(255, Math.floor(255 * Math.max(0, 1 - Math.abs(t - 0.55) * 2)))
        const b = Math.min(255, Math.floor(255 * (1 - t) * 1.1))
        data.data[i] = r
        data.data[i + 1] = g
        data.data[i + 2] = b
      }
      ctx.putImageData(data, 0, 0)
      resolve(canvas.toDataURL('image/jpeg', 0.6))
    }
    img.onerror = () => reject(new Error('热成像合成失败'))
    img.src = visibleDataUrl
  })
}

function buildAudioPayload(): Record<string, unknown> {
  const bands = Array.from({ length: 16 }, () => Math.round(randomBetween(28, 92)))
  return {
    unit: 'dB',
    durationMs: Math.round(randomBetween(800, 2200)),
    peakDb: Math.round(randomBetween(52, 78)),
    rmsDb: Math.round(randomBetween(38, 55)),
    spectrumBands: bands,
    anomalyScore: Math.round(randomBetween(0, 35))
  }
}

function buildVibrationPayload(): Record<string, unknown> {
  return {
    unit: 'mm/s',
    sampleRateHz: 512,
    axis: {
      x: { rms: round2(randomBetween(0.08, 0.42)), peak: round2(randomBetween(0.2, 0.95)) },
      y: { rms: round2(randomBetween(0.06, 0.38)), peak: round2(randomBetween(0.18, 0.88)) },
      z: { rms: round2(randomBetween(0.1, 0.48)), peak: round2(randomBetween(0.22, 1.05)) }
    },
    dominantFreqHz: round1(randomBetween(12, 48)),
    healthIndex: round2(randomBetween(0.72, 0.99))
  }
}

function buildTemperaturePayload(altitudeM: number): Record<string, unknown> {
  const ambient = round1(18 + altitudeM * 0.02 + randomBetween(-2, 2))
  const hotspot = round1(ambient + randomBetween(8, 28))
  return {
    unit: 'C',
    ambientC: ambient,
    targetC: round1((ambient + hotspot) / 2),
    maxC: hotspot,
    sensorId: 'FLIR-AUX-01'
  }
}

function round1(n: number) {
  return Math.round(n * 10) / 10
}
function round2(n: number) {
  return Math.round(n * 100) / 100
}

function baseSample(
  photo: PhotoCaptureMeta,
  modalityType: MultimodalModalityType,
  payload: Record<string, unknown>,
  previewDataUrl?: string
): MultimodalSample {
  return {
    id: `MM-${modalityType}-${photo.id}`,
    waypointIndex: photo.waypointIndex,
    modalityType,
    capturedAt: photo.timestamp,
    gps: { ...photo.gps },
    payload,
    previewDataUrl
  }
}

/**
 * 在拍照航点采集全套多模态数据（边缘侧模拟）。
 */
export async function collectMultimodalAtWaypoint(photo: PhotoCaptureMeta): Promise<MultimodalSample[]> {
  const samples: MultimodalSample[] = []
  const alt = photo.gps.altitudeM

  if (photo.imageDataUrl) {
    const thumb = await shrinkDataUrl(photo.imageDataUrl)
    samples.push(
      baseSample(photo, 'VISIBLE', {
        format: 'jpeg',
        width: 720,
        height: 480,
        encoding: 'base64',
        thumbnail: thumb
      }, thumb)
    )
    const thermalThumb = await synthesizeThermalPreview(photo.imageDataUrl)
    const minT = round1(18 + alt * 0.01)
    const maxT = round1(minT + randomBetween(12, 35))
    samples.push(
      baseSample(
        photo,
        'THERMAL',
        {
          format: 'jpeg',
          palette: 'ironbow',
          minC: minT,
          maxC: maxT,
          thumbnail: thermalThumb
        },
        thermalThumb
      )
    )
  } else {
    samples.push(
      baseSample(photo, 'VISIBLE', {
        format: 'none',
        note: '无可见光截图（仿真离屏渲染未就绪）'
      })
    )
  }

  samples.push(baseSample(photo, 'AUDIO', buildAudioPayload()))
  samples.push(baseSample(photo, 'VIBRATION', buildVibrationPayload()))
  samples.push(baseSample(photo, 'TEMPERATURE', buildTemperaturePayload(alt)))

  return samples
}
