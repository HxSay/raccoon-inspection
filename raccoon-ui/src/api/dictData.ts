import request from '@/utils/request'

export interface DictDataQueryRequest {
  page?: number
  size?: number
  dictTypeCode?: string
  dictLabel?: string
  status?: number
}

export interface DictDataRequest {
  id?: number
  dictTypeCode: string
  dictLabel: string
  dictValue: string
  dictExtend?: string
  status?: number
  sort?: number
  remark?: string
}

export interface DictDataResponse {
  id: number
  dictTypeCode: string
  dictLabel: string
  dictValue: string
  dictExtend: string
  status: number
  sort: number
  remark: string
  tenantId: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

export function getDictDataPage(data: DictDataQueryRequest) {
  return request({
    url: '/dict/data/page',
    method: 'post',
    data
  })
}

export function getDictDataById(id: number) {
  return request({
    url: `/dict/data/${id}`,
    method: 'get'
  })
}

export function addDictData(data: DictDataRequest) {
  return request({
    url: '/dict/data/add',
    method: 'post',
    data
  })
}

export function updateDictData(data: DictDataRequest) {
  return request({
    url: '/dict/data/update',
    method: 'post',
    data
  })
}

export function deleteDictData(id: number) {
  return request({
    url: '/dict/data/delete',
    method: 'post',
    params: { id }
  })
}

export function getDictDataByTypeCode(dictTypeCode: string) {
  return request({
    url: `/dict/data/${dictTypeCode}`,
    method: 'get'
  })
}
