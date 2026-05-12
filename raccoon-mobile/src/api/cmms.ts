import request from '@/utils/request'

/** 与 PC 端 raccoon-ui 共用 raccoon-cloud-system 表结构与路径 */

export function devicePage(data: Record<string, unknown>) {
  return request({ url: '/cmms/device/info/page', method: 'post', data })
}

export function deviceGet(id: number) {
  return request({ url: '/cmms/device/info/get', method: 'get', params: { id } })
}

export function deviceByCode(deviceCode: string) {
  return request({ url: '/cmms/device/info/byCode', method: 'get', params: { deviceCode } })
}

export function pointList(deviceId: number) {
  return request({ url: '/cmms/device/point/list', method: 'get', params: { deviceId } })
}

export function taskPage(data: Record<string, unknown>) {
  return request({ url: '/cmms/inspection/task/page', method: 'post', data })
}

export function taskGet(id: number) {
  return request({ url: '/cmms/inspection/task/get', method: 'get', params: { id } })
}

export function taskCheckIn(data: { taskId: number; longitude: number; latitude: number; scannedDeviceCode?: string }) {
  return request({ url: '/cmms/inspection/task/checkIn', method: 'post', data })
}

export function taskStart(taskId: number) {
  /** query 写入 URL，便于离线队列重放（仅序列化 url + data） */
  return request({ url: `/cmms/inspection/task/start?taskId=${taskId}`, method: 'post' })
}

export function taskComplete(data: {
  taskId: number
  records: { pointId: number; checkValue?: string; isNormal: number; imageUrls?: string }[]
}) {
  return request({ url: '/cmms/inspection/task/complete', method: 'post', data })
}

export function taskReportAbnormal(data: { taskId: number; faultDescription: string; faultImageUrls?: string }) {
  return request({ url: '/cmms/inspection/task/reportAbnormal', method: 'post', data })
}

export function recordList(taskId: number) {
  return request({ url: '/cmms/inspection/record/list', method: 'get', params: { taskId } })
}

export function woPage(data: Record<string, unknown>) {
  return request({ url: '/cmms/workOrder/page', method: 'post', data })
}

export function woGet(id: number) {
  return request({ url: '/cmms/workOrder/get', method: 'get', params: { id } })
}

export function woSave(data: Record<string, unknown>) {
  return request({ url: '/cmms/workOrder/save', method: 'post', data })
}

export function woStatus(id: number, status: number) {
  return request({ url: `/cmms/workOrder/status?id=${id}&status=${status}`, method: 'post' })
}
