import request from '@/utils/request'

export type InspectionRobotType = 'UAV' | 'ROBOT_DOG' | 'GROUND_ROBOT' | 'WHEELED'

export interface InspectionRobotVO {
  id?: number
  uavName: string
  uavCode?: string
  robotType: InspectionRobotType | string
  robotTypeLabel?: string
  mapId: number
  mapName?: string
  sceneType?: string
  markerLabel: string
  markerColor?: string
  sceneX?: number
  sceneY?: number
  sceneZ?: number
  status?: number
  remark?: string
}

export interface InspectionSceneVO {
  id: number
  mapName: string
  sceneType?: string
  remark?: string
  robotCount: number
  robots: InspectionRobotVO[]
}

export interface InspectionRobotSaveRequest {
  id?: number
  uavName: string
  uavCode?: string
  robotType: string
  mapId: number
  markerLabel: string
  markerColor?: string
  sceneX?: number
  sceneY?: number
  sceneZ?: number
  status?: number
  remark?: string
}

export const ROBOT_TYPE_OPTIONS = [
  { label: '无人机', value: 'UAV' },
  { label: '机器狗', value: 'ROBOT_DOG' },
  { label: '地面巡检机器人', value: 'GROUND_ROBOT' },
  { label: '轮式巡检机器人', value: 'WHEELED' }
]

export const SCENE_TYPE_LABEL: Record<string, string> = {
  patrol: '输电巡检',
  substation: '变电站',
  thermal: '火电站'
}

export const inspectionRobotScenes = () =>
  request<InspectionSceneVO[]>({ url: '/drone/inspection-robot/scenes', method: 'get' })

export const inspectionRobotPage = (params: {
  current?: number
  size?: number
  mapId?: number
  robotType?: string
  keyword?: string
}) => request({ url: '/drone/inspection-robot/page', method: 'get', params })

export const inspectionRobotByMap = (mapId: number) =>
  request<InspectionRobotVO[]>({ url: `/drone/inspection-robot/by-map/${mapId}`, method: 'get' })

export const inspectionRobotBySceneType = (sceneType: string) =>
  request<InspectionRobotVO[]>({
    url: '/drone/inspection-robot/by-scene-type',
    method: 'get',
    params: { sceneType }
  })

export const inspectionRobotCreate = (data: InspectionRobotSaveRequest) =>
  request({ url: '/drone/inspection-robot', method: 'post', data })

export const inspectionRobotUpdate = (id: number, data: InspectionRobotSaveRequest) =>
  request({ url: `/drone/inspection-robot/${id}`, method: 'put', data })

export const inspectionRobotDelete = (id: number) =>
  request({ url: `/drone/inspection-robot/${id}`, method: 'delete' })
