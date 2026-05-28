import { sceneToGps } from '@/sim/aiDetect'
import type { SceneEditor3D } from '@/editor/SceneEditor3D'
import type { HxResult } from '@/types/droneDispatch'

const DRONE_API_BASE = import.meta.env.VITE_DRONE_API_BASE ?? '/api/drone'

export interface FieldSceneDeviceSyncItem {
  sceneObjectId: string
  deviceName: string
  deviceType: string
  longitude?: number
  latitude?: number
  height?: number
  sceneX?: number
  sceneY?: number
  sceneZ?: number
  locationDesc?: string
}

const SCENE_MAP: Record<'patrol' | 'substation' | 'thermal', { mapId: number; sceneType: string }> = {
  patrol: { mapId: 1, sceneType: 'patrol' },
  substation: { mapId: 2, sceneType: 'substation' },
  thermal: { mapId: 3, sceneType: 'thermal' }
}

export async function syncFieldDevicesFromEditor(
  editor: SceneEditor3D | null | undefined,
  tab: 'patrol' | 'substation' | 'thermal'
): Promise<number> {
  if (!editor) return 0
  const cfg = SCENE_MAP[tab]
  const raw = editor.collectFieldDevicesForSync()
  const devices: FieldSceneDeviceSyncItem[] = raw.map((r) => {
    const gps = sceneToGps(r.sceneX, r.sceneY, r.sceneZ)
    return {
      ...r,
      longitude: Math.round(gps.longitude * 1_000_000) / 1_000_000,
      latitude: Math.round(gps.latitude * 1_000_000) / 1_000_000,
      height: Math.round(gps.altitudeM * 100) / 100,
      locationDesc: `仿真编辑器 · ${r.deviceName}`
    }
  })

  const res = await fetch(`${DRONE_API_BASE}/field-scene-device/sync-from-scene`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mapId: cfg.mapId, sceneType: cfg.sceneType, devices })
  })
  if (!res.ok) {
    throw new Error(`同步失败 HTTP ${res.status}`)
  }
  const body = (await res.json()) as HxResult<{ upserted: number }>
  if (body.code !== 200) {
    throw new Error(body.msg || '同步失败')
  }
  return body.data?.upserted ?? devices.length
}
