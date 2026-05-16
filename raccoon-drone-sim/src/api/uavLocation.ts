/**
 * 边缘端上报无人机轨迹至 raccoon-cloud-iot-data
 */

export interface UavLocationPoint {
  uavId: number
  taskId?: number
  mapId?: number
  longitude: number
  latitude: number
  height: number
  speed?: number
  battery?: number
  locationMode: number
  flightStatus?: string
  /** ISO-8601 本地时间字符串，可选 */
  createTime?: string
}

interface HxResult<T> {
  code: number
  msg?: string
  data?: T
}

const IOT_API_BASE = import.meta.env.VITE_IOT_API_BASE ?? '/api/iot-data'

export async function postUavLocationBatch(points: UavLocationPoint[]): Promise<void> {
  if (!points.length) return
  const res = await fetch(`${IOT_API_BASE}/uav-location/batch`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ points })
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`)
  }
  const body = (await res.json()) as HxResult<void>
  if (body.code !== 200) {
    throw new Error(body.msg || '轨迹上报失败')
  }
}
