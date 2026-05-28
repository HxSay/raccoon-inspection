import request from '@/utils/request'
import type { UavRouteDispatchPayload } from '@/api/drone'

export interface DispatchTaskRequest {
  requestId?: string
  userInput?: string
  taskType?: string
  priority?: string
  mapId?: number
  areaName?: string
  deviceNames?: string[]
  deviceIds?: number[]
  enableSimulation?: boolean
  autoDispatch?: boolean
}

export interface DispatchTaskResponse {
  taskId?: string
  requestId?: string
  taskTypeCode?: string
  priority?: string
  status?: string
  assigned?: boolean
  assignedTerminalId?: number
  assignedTerminalName?: string
  bidPrice?: number
  totalElapsedMs?: number
  message?: string
  workOrder?: {
    taskId?: string
    planId?: number
    terminalId?: number
    terminalType?: string
    dispatchResult?: string
    payload?: UavRouteDispatchPayload
  }
}

/** 调度中枢：解析 → 状态感知 → 拍卖分配 → 路径规划 → 下发 */
export const dispatchTaskGenerate = (data: DispatchTaskRequest) =>
  request<DispatchTaskResponse>({
    url: '/drone/dispatch/task/generate',
    method: 'post',
    data: {
      enableSimulation: true,
      autoDispatch: true,
      ...data
    }
  })
