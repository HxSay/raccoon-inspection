import request from '@/utils/request'

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface DeviceCategory {
  id?: number
  parentId?: number
  categoryName: string
  description?: string
  sort?: number
}

export interface Supplier {
  id?: number
  supplierName: string
  contactPerson?: string
  contactPhone?: string
  address?: string
}

export interface InspectionDept {
  id?: number
  parentId?: number
  deptName: string
  leaderUserId?: number
}

export interface InspectionWarehouse {
  id?: number
  warehouseName: string
  location?: string
  managerUserId?: number
}

export interface DeviceInfo {
  id?: number
  deviceCode: string
  deviceName: string
  model?: string
  serialNumber?: string
  categoryId?: number
  deptId?: number
  managerUserId?: number
  supplierId?: number
  location?: string
  longitude?: number
  latitude?: number
  qrcodeUrl?: string
  purchaseDate?: string
  activateDate?: string
  warrantyEndDate?: string
  status?: number
  inspectionCycle?: number
  maintenanceCycle?: number
  remark?: string
}

export interface InspectionPoint {
  id?: number
  deviceId: number
  pointName: string
  pointType: number
  unit?: string
  minThreshold?: number
  maxThreshold?: number
  standardValue?: number
  sort?: number
}

export interface InspectionPlan {
  id?: number
  planName: string
  deviceIds?: string
  cycleType: number
  cycleValue: number
  execUserId: number
  startTime: string
  endTime?: string
  status?: number
}

export interface InspectionTask {
  id?: number
  taskCode?: string
  planId?: number
  workOrderId?: number
  deviceId: number
  taskName: string
  execUserId: number
  planExecuteTime: string
  actualExecuteTime?: string
  status?: number
  isAbnormal?: number
  remark?: string
}

export interface InspectionRecord {
  id?: number
  taskId: number
  deviceId: number
  pointId: number
  checkValue?: string
  isNormal?: number
  imageUrls?: string
}

export interface WorkOrder {
  id?: number
  orderCode?: string
  deviceId: number
  source: number
  faultDescription?: string
  faultImageUrls?: string
  orderType: number
  level?: number
  assignUserId?: number
  reportUserId?: number
  planFinishTime?: string
  status?: number
  repairContent?: string
  repairCost?: number
}

// --- 设备 ---
export const cmmsCategoryList = () =>
  request({ url: '/cmms/device/category/list', method: 'post' })

export const cmmsCategorySave = (data: DeviceCategory) =>
  request({ url: '/cmms/device/category/save', method: 'post', data })

export const cmmsCategoryDelete = (id: number) =>
  request({ url: '/cmms/device/category/delete', method: 'post', params: { id } })

export const cmmsSupplierPage = (data: { page?: number; size?: number; supplierName?: string }) =>
  request({ url: '/cmms/device/supplier/page', method: 'post', data })

export const cmmsSupplierSave = (data: Supplier) =>
  request({ url: '/cmms/device/supplier/save', method: 'post', data })

export const cmmsSupplierDelete = (id: number) =>
  request({ url: '/cmms/device/supplier/delete', method: 'post', params: { id } })

export const cmmsDeptPage = (data: { page?: number; size?: number; deptName?: string }) =>
  request({ url: '/cmms/device/dept/page', method: 'post', data })

export const cmmsDeptSave = (data: InspectionDept) =>
  request({ url: '/cmms/device/dept/save', method: 'post', data })

export const cmmsDeptDelete = (id: number) =>
  request({ url: '/cmms/device/dept/delete', method: 'post', params: { id } })

export const cmmsWarehousePage = (data: { page?: number; size?: number }) =>
  request({ url: '/cmms/device/warehouse/page', method: 'post', data })

export const cmmsWarehouseSave = (data: InspectionWarehouse) =>
  request({ url: '/cmms/device/warehouse/save', method: 'post', data })

export const cmmsWarehouseDelete = (id: number) =>
  request({ url: '/cmms/device/warehouse/delete', method: 'post', params: { id } })

export const cmmsDevicePage = (data: {
  page?: number
  size?: number
  deviceName?: string
  deviceCode?: string
  status?: number
  categoryId?: number
}) => request({ url: '/cmms/device/info/page', method: 'post', data })

export const cmmsDeviceGet = (id: number) =>
  request({ url: '/cmms/device/info/get', method: 'get', params: { id } })

export const cmmsDeviceSave = (data: DeviceInfo) =>
  request({ url: '/cmms/device/info/save', method: 'post', data })

export const cmmsDeviceDelete = (id: number) =>
  request({ url: '/cmms/device/info/delete', method: 'post', params: { id } })

export const cmmsPointList = (deviceId: number) =>
  request({ url: '/cmms/device/point/list', method: 'get', params: { deviceId } })

export const cmmsPointSave = (data: InspectionPoint) =>
  request({ url: '/cmms/device/point/save', method: 'post', data })

export const cmmsPointDelete = (id: number) =>
  request({ url: '/cmms/device/point/delete', method: 'post', params: { id } })

// --- 巡检 ---
export const cmmsPlanPage = (data: { page?: number; size?: number; planName?: string; status?: number }) =>
  request({ url: '/cmms/inspection/plan/page', method: 'post', data })

export const cmmsPlanSave = (data: InspectionPlan) =>
  request({ url: '/cmms/inspection/plan/save', method: 'post', data })

export const cmmsPlanDelete = (id: number) =>
  request({ url: '/cmms/inspection/plan/delete', method: 'post', params: { id } })

export const cmmsPlanGenerateTasks = (planId: number) =>
  request({ url: '/cmms/inspection/plan/generateTasks', method: 'post', params: { planId } })

/** 按计划向待执行任务派发巡检工单（每任务一单并自动下发） */
export const cmmsDispatchWorkOrdersFromPlan = (planId: number) =>
  request({ url: '/cmms/inspection/workOrder/dispatchFromPlan', method: 'post', data: { planId } })

export const cmmsTaskPage = (data: {
  page?: number
  size?: number
  taskCode?: string
  status?: number
  deviceId?: number
  execUserId?: number
  planId?: number
}) => request({ url: '/cmms/inspection/task/page', method: 'post', data })

export const cmmsTaskSave = (data: InspectionTask) =>
  request({ url: '/cmms/inspection/task/save', method: 'post', data })

export const cmmsTaskDelete = (id: number) =>
  request({ url: '/cmms/inspection/task/delete', method: 'post', params: { id } })

export const cmmsTaskComplete = (data: {
  taskId: number
  records: { pointId: number; checkValue?: string; isNormal: number; imageUrls?: string }[]
}) => request({ url: '/cmms/inspection/task/complete', method: 'post', data })

export const cmmsRecordList = (taskId: number) =>
  request({ url: '/cmms/inspection/record/list', method: 'get', params: { taskId } })

// --- 工单 ---
export const cmmsWoPage = (data: { page?: number; size?: number; orderCode?: string; status?: number; deviceId?: number }) =>
  request({ url: '/cmms/workOrder/page', method: 'post', data })

export const cmmsWoSave = (data: WorkOrder) =>
  request({ url: '/cmms/workOrder/save', method: 'post', data })

export const cmmsWoDelete = (id: number) =>
  request({ url: '/cmms/workOrder/delete', method: 'post', params: { id } })

export const cmmsWoStatus = (id: number, status: number) =>
  request({ url: '/cmms/workOrder/status', method: 'post', params: { id, status } })
