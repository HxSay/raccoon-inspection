import type { UavRouteDispatchPayload } from '@/types/droneDispatch'

export const MSG_INSPECTION_DISPATCH = 'RACCOON_INSPECTION_DISPATCH'
export const MSG_INSPECTION_DISPATCH_ACK = 'RACCOON_INSPECTION_DISPATCH_ACK'
export const MSG_INSPECTION_STATUS = 'RACCOON_INSPECTION_STATUS'
export const MSG_INSPECTION_MISSION_COMPLETE = 'RACCOON_INSPECTION_MISSION_COMPLETE'
export const MSG_INSPECTION_MISSION_ERROR = 'RACCOON_INSPECTION_MISSION_ERROR'

export interface InspectionDispatchMessage {
  type: typeof MSG_INSPECTION_DISPATCH
  dispatch: UavRouteDispatchPayload
  autoStart?: boolean
  userInput?: string
  /** 父页面（LLM 决策）建议出动无人机数量，仿真侧按可用机数最终裁剪 */
  recommendedFleet?: number
}
