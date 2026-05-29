import request from '@/utils/request'

export interface FieldSceneDeviceVO {
  id?: number
  mapId: number
  mapName?: string
  deviceName: string
  deviceType: string
  deviceTypeLabel?: string
  longitude?: number
  latitude?: number
  height?: number
  sceneX?: number
  sceneY?: number
  sceneZ?: number
  sceneType?: string
  sceneObjectId?: string
  locationDesc?: string
  syncSource?: string
  status?: number
  remark?: string
}

export interface FieldSceneDeviceSaveRequest {
  id?: number
  mapId: number
  deviceName: string
  deviceType: string
  longitude?: number
  latitude?: number
  height?: number
  sceneX?: number
  sceneY?: number
  sceneZ?: number
  sceneType?: string
  sceneObjectId?: string
  locationDesc?: string
  syncSource?: string
  status?: number
  remark?: string
}

export const DEVICE_TYPE_OPTIONS = [
  { label: '杆塔', value: 'TOWER' },
  { label: '变压器', value: 'TRANSFORMER' },
  { label: '断路器', value: 'BREAKER' },
  { label: '阀门', value: 'VALVE' },
  { label: '隔离开关', value: 'ISOLATOR' },
  { label: '站房', value: 'STATION' },
  { label: '自定义', value: 'CUSTOM' }
]

export const SYNC_SOURCE_LABEL: Record<string, string> = {
  BUILTIN: '内置',
  SCENE_SYNC: '仿真同步',
  MANUAL: '手工录入'
}

export const fieldSceneDevicePage = (params: {
  current?: number
  size?: number
  mapId?: number
  sceneType?: string
  keyword?: string
}) => request({ url: '/drone/field-scene-device/page', method: 'get', params })

export const fieldSceneDeviceByMap = (mapId: number) =>
  request<FieldSceneDeviceVO[]>({ url: `/drone/field-scene-device/by-map/${mapId}`, method: 'get' })

export const fieldSceneDeviceCreate = (data: FieldSceneDeviceSaveRequest) =>
  request({ url: '/drone/field-scene-device', method: 'post', data })

export const fieldSceneDeviceUpdate = (id: number, data: FieldSceneDeviceSaveRequest) =>
  request({ url: `/drone/field-scene-device/${id}`, method: 'put', data })

export const fieldSceneDeviceDelete = (id: number) =>
  request({ url: `/drone/field-scene-device/${id}`, method: 'delete' })

export const fieldSceneDeviceInitBuiltin = () =>
  request<{ initialized: number; backfilled?: number }>({
    url: '/drone/field-scene-device/init-builtin-towers',
    method: 'post'
  })

export const fieldSceneDeviceBackfill = (mapId?: number) =>
  request<{ backfilled: number }>({
    url: '/drone/field-scene-device/backfill-coordinates',
    method: 'post',
    params: mapId != null ? { mapId } : undefined
  })
