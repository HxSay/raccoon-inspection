import request from '@/utils/request'
import type { WorkOrderAuditDetailDTO } from './workOrderAuditTypes'

export const listPendingAuditOrders = (params?: {
  inspectorId?: number
  page?: number
  size?: number
}) =>
  request({
    url: '/system/cmms/workOrderAudit/pending',
    method: 'get',
    params
  })

export const getAuditOrderDetail = (workOrderId: number) =>
  request({
    url: '/system/cmms/workOrderAudit/detail',
    method: 'get',
    params: { workOrderId }
  })

export const approveWorkOrder = (data: {
  workOrderId: number
  auditorId?: number
  auditorName?: string
}) =>
  request({
    url: '/system/cmms/workOrderAudit/approve',
    method: 'post',
    data
  })

export const rejectWorkOrder = (data: {
  workOrderId: number
  rejectReason: string
  auditorId?: number
  auditorName?: string
}) =>
  request({
    url: '/system/cmms/workOrderAudit/reject',
    method: 'post',
    data
  })

export type { WorkOrderAuditDetailDTO }
