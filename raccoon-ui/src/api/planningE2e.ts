import request from '@/utils/request'

export interface PlanningEndToEndRequest {
  requestId?: string
  userInput?: string
  mapId?: number
  areaName?: string
  taskType?: string
  priority?: string
  deviceNames?: string[]
  inspectorId?: number
  inspectorName?: string
  remark?: string
  enableSimulation?: boolean
}

export interface PlanningEndToEndResponse {
  success?: boolean
  message?: string
  needFollowUp?: boolean
  followUpQuestion?: string
  dispatchTaskId?: string
  assignedTerminalId?: number
  assignedTerminalName?: string
  assignReason?: string
  planId?: number
  taskId?: number
  workOrderId?: number
  orderNo?: string
  workOrderStatus?: number
  auditDeadline?: string
}

/** 任务规划 Agent：NLP → 调度 → CMMS 待审核工单 */
export const planningEndToEnd = (data: PlanningEndToEndRequest) =>
  request({
    url: '/drone/planning/end-to-end',
    method: 'post',
    data,
    timeout: 120000
  })
