import request from '@/utils/request'

export interface WorkOrderAuditDetail {
  workOrderId?: number
  orderNo?: string
  area?: string
  status?: number
  priorityCode?: string
  dispatchTaskId?: string
  terminalId?: number
  terminalName?: string
  assignReason?: string
  auditDeadline?: string
  remark?: string
  steps?: Array<{
    stepOrder?: number
    type?: string
    deviceName?: string
    checkItem?: string
    standardRange?: string
    description?: string
  }>
}

export const listPendingAudit = (params?: { inspectorId?: number; page?: number; size?: number }) =>
  request({ url: '/cmms/workOrderAudit/pending', method: 'get', params })

export const getAuditDetail = (workOrderId: number) =>
  request({ url: '/cmms/workOrderAudit/detail', method: 'get', params: { workOrderId } })

export const approveAudit = (data: { workOrderId: number; auditorId?: number; auditorName?: string }) =>
  request({ url: '/cmms/workOrderAudit/approve', method: 'post', data })

export const rejectAudit = (data: {
  workOrderId: number
  rejectReason: string
  auditorId?: number
  auditorName?: string
}) => request({ url: '/cmms/workOrderAudit/reject', method: 'post', data })
