import request from '@/utils/request'

export interface RoleRequest {
  id?: number
  roleName: string
  roleCode: string
  description?: string
  sort?: number
  status?: number
}

export interface RoleResponse {
  id: number
  roleName: string
  roleCode: string
  description: string
  sort: number
  status: number
  createTime: string
}

export function getRoleList() {
  return request({
    url: '/system/role/list',
    method: 'get'
  })
}

export function addRole(data: RoleRequest) {
  return request({
    url: '/system/role/add',
    method: 'post',
    data
  })
}

export function updateRole(data: RoleRequest) {
  return request({
    url: '/system/role/update',
    method: 'post',
    data
  })
}

export function assignRoles(userId: number, roleIds: number[]) {
  return request({
    url: '/system/role/assign',
    method: 'post',
    params: { userId },
    data: roleIds
  })
}

export function getUserRoles(userId: number) {
  return request({
    url: `/system/role/user/${userId}`,
    method: 'get'
  })
}