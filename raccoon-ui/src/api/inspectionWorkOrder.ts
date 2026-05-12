import request from '@/utils/request'

export interface IwoStepDraft {
  stepOrder: number
  type: string
  target?: string
  description?: string
  deviceId?: number | null
  deviceName?: string
  checkItem?: string
  standardMin?: number
  standardMax?: number
  unit?: string
  detectionMethod?: string
}

export interface IwoCreateBody {
  area: string
  shiftType: number
  inspectorId: number
  inspectorName?: string
  planStartTime: string
  planEndTime: string
  remark?: string
  planId?: number
  taskId?: number
  steps: IwoStepDraft[]
}

export interface IwoRow {
  id: number
  orderNo: string
  area: string
  shiftType: number
  inspectorId: number
  inspectorName?: string
  planStartTime: string
  planEndTime: string
  actualStartTime?: string
  actualEndTime?: string
  status: number
  planId?: number
  taskId?: number
  remark?: string
}

export interface IwoFull {
  id: number
  orderNo: string
  area: string
  shiftType: number
  inspectorId: number
  inspectorName?: string
  planStartTime: string
  planEndTime: string
  actualStartTime?: string
  actualEndTime?: string
  status: number
  createBy?: number
  createByName?: string
  planId?: number
  taskId?: number
  remark?: string
  steps: IwoStepVO[]
}

export interface IwoStepVO {
  id: number
  orderId: number
  stepOrder: number
  type: string
  target?: string
  description?: string
  deviceId?: number
  deviceName?: string
  deviceCode?: string
  voltageLevel?: string
  checkItem?: string
  standardMin?: number
  standardMax?: number
  unit?: string
  detectionMethod?: string
  actualValue?: string
  isException?: number
  collectTime?: string
  photoUrl?: string
  remark?: string
}

export const iwoCreate = (data: IwoCreateBody) =>
  request({ url: '/cmms/inspectionWorkOrder/create', method: 'post', data })

export const iwoIssue = (id: number) =>
  request({ url: '/cmms/inspectionWorkOrder/issue', method: 'post', data: { id } })

export const iwoCancel = (id: number) =>
  request({ url: '/cmms/inspectionWorkOrder/cancel', method: 'post', data: { id } })

export const iwoPage = (data: {
  page?: number
  size?: number
  area?: string
  shiftType?: number
  status?: number
  planId?: number
  taskId?: number
  planStartFrom?: string
  planStartTo?: string
}) => request({ url: '/cmms/inspectionWorkOrder/page', method: 'post', data })

export const iwoMyPage = (data: { page?: number; size?: number; tab: string; inspectorId?: number }) =>
  request({ url: '/cmms/inspectionWorkOrder/myPage', method: 'post', data })

export const iwoDetail = (orderId: number) =>
  request({ url: '/cmms/inspectionWorkOrder/detail', method: 'get', params: { orderId } })

export const iwoStepExecute = (data: { detailId: number; remark?: string }) =>
  request({ url: '/cmms/inspectionWorkOrder/step/execute', method: 'post', data })

export const iwoStepCollect = (data: { detailId: number; actualValue: string; photoUrl?: string; remark?: string }) =>
  request({ url: '/cmms/inspectionWorkOrder/step/collect', method: 'post', data })

export const iwoScanDevice = (data: { detailId: number; deviceCode: string }) =>
  request({ url: '/cmms/inspectionWorkOrder/scanDevice', method: 'post', data })
