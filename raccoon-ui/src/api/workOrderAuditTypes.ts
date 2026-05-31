export interface WorkOrderAuditDetailDTO {
  workOrderId?: number
  orderNo?: string
  area?: string
  status?: number
  priorityCode?: string
  dispatchTaskId?: string
  terminalId?: number
  terminalName?: string
  assignReason?: string
  pathPlanJson?: string
  auditDeadline?: string
  remark?: string
  rejectReason?: string
  steps?: Array<{
    stepOrder?: number
    type?: string
    target?: string
    description?: string
    deviceName?: string
    checkItem?: string
    standardRange?: string
    unit?: string
  }>
}
