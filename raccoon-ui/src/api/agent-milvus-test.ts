import request from '@/utils/request'

/** 故障知识录入 */
export interface MilvusTestInsertRequest {
  docId: string
  content: string
  deviceType: string
  faultType: string
  source?: string
}

export interface MilvusTestInsertResult {
  docId: string
  message: string
  metadata?: Record<string, unknown>
}

/** 向量检索 */
export interface MilvusTestSearchRequest {
  query: string
  topK?: number
}

export interface MilvusTestDocument {
  docId: string
  content: string
  metadata?: Record<string, unknown>
  score?: number
}

export interface MilvusTestGetByIdRequest {
  docId: string
}

export const agentMilvusTestInsert = (data: MilvusTestInsertRequest) =>
  request({
    url: '/agent/ai/milvus/test/insert',
    method: 'post',
    data
  })

export const agentMilvusTestSearch = (data: MilvusTestSearchRequest) =>
  request({
    url: '/agent/ai/milvus/test/search',
    method: 'post',
    data
  })

export const agentMilvusTestGetById = (data: MilvusTestGetByIdRequest) =>
  request({
    url: '/agent/ai/milvus/test/getById',
    method: 'post',
    data
  })
