import type { HxResult } from '@/types/droneDispatch'

const DRONE_API_BASE = import.meta.env.VITE_DRONE_API_BASE ?? '/api/drone'

export interface InspectionRobotVO {
  id: number
  uavName: string
  uavCode?: string
  robotType: string
  robotTypeLabel?: string
  mapId: number
  mapName?: string
  sceneType?: string
  markerLabel: string
  markerColor?: string
  sceneX?: number
  sceneY?: number
  sceneZ?: number
  status?: number
}

export async function fetchRobotsBySceneType(sceneType: string): Promise<InspectionRobotVO[]> {
  const qs = new URLSearchParams({ sceneType })
  const res = await fetch(`${DRONE_API_BASE}/inspection-robot/by-scene-type?${qs}`)
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  const body = (await res.json()) as HxResult<InspectionRobotVO[]>
  if (body.code !== 200 || !body.data) {
    return []
  }
  return body.data.filter((r) => r.status !== 0)
}
