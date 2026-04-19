import request from '@/utils/request'

export interface DictTypeQueryRequest {
  page?: number
  size?: number
  dictTypeCode?: string
  dictTypeName?: string
  status?: number
}

export interface DictTypeRequest {
  id?: number
  dictTypeCode: string
  dictTypeName: string
  status?: number
  remark?: string
  sort?: number
}

export interface DictTypeResponse {
  id: number
  dictTypeCode: string
  dictTypeName: string
  status: number
  remark: string
  sort: number
  tenantId: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

export function getDictTypePage(data: DictTypeQueryRequest) {
  return request({
    url: '/dict/type/page',
    method: 'post',
    data
  })
}

export function getDictTypeById(id: number) {
  return request({
    url: `/dict/type/${id}`,
    method: 'get'
  })
}

export function addDictType(data: DictTypeRequest) {
  return request({
    url: '/dict/type/add',
    method: 'post',
    data
  })
}

export function updateDictType(data: DictTypeRequest) {
  return request({
    url: '/dict/type/update',
    method: 'post',
    data
  })
}

export function deleteDictType(id: number) {
  return request({
    url: '/dict/type/delete',
    method: 'post',
    params: { id }
  })
}

export function getDictTypeList() {
  return request({
    url: '/dict/type/list',
    method: 'get'
  })
}
