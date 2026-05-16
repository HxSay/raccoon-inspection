import type { HxResult, UavRouteDispatchPayload } from '@/types/droneDispatch'

const DRONE_API_BASE = import.meta.env.VITE_DRONE_API_BASE ?? '/api/drone'

/**
 * 从 raccoon-cloud-drone 拉取智能巡检路径规划下发 JSON
 */
export async function fetchRouteDispatch(uavId: number, taskId: number): Promise<UavRouteDispatchPayload> {
  const qs = new URLSearchParams({
    uavId: String(uavId),
    taskId: String(taskId)
  })
  const res = await fetch(`${DRONE_API_BASE}/route-plan/dispatch?${qs}`)
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`)
  }
  const body = (await res.json()) as HxResult<UavRouteDispatchPayload>
  if (body.code !== 200 || !body.data) {
    throw new Error(body.msg || '拉取路径规划失败')
  }
  return body.data
}
