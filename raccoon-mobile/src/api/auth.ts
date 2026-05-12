import request from '@/utils/request'

export async function login(username: string, password: string) {
  return request({
    url: '/auth/login',
    method: 'post',
    data: { username, password }
  }) as Promise<{
    code: number
    msg: string
    data: { token: string; refreshToken: string; user: { id: number; username: string; nickname?: string; userType?: number } }
  }>
}
