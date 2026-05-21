import request from '@/utils/request'

export interface OllamaHealth {
  reachable: boolean
  baseUrl: string
  defaultModel: string
  version?: string
  message: string
}

export interface OllamaModel {
  name: string
  size?: number
  modifiedAt?: string
}

export interface OllamaChatRequest {
  message: string
  model?: string
  temperature?: number
}

export interface OllamaChatResponse {
  model: string
  content: string
  elapsedMs: number
}

export const agentOllamaHealth = () =>
  request({ url: '/agent/ollama/health', method: 'get' })

export const agentOllamaModels = () =>
  request({ url: '/agent/ollama/models', method: 'get' })

export const agentOllamaChat = (data: OllamaChatRequest) =>
  request({ url: '/agent/ollama/chat', method: 'post', data })

export interface Neo4jHealth {
  reachable: boolean
  uri: string
  browserUrl: string
  version?: string
  edition?: string
  message: string
}

export interface Neo4jStats {
  nodeCount: number
  relationshipCount: number
  labels: string[]
  relationshipTypes: string[]
}

export const agentNeo4jHealth = () =>
  request({ url: '/agent/neo4j/health', method: 'get' })

export const agentNeo4jStats = () =>
  request({ url: '/agent/neo4j/stats', method: 'get' })

export const agentNeo4jReadCypher = (cypher: string) =>
  request({ url: '/agent/neo4j/cypher/read', method: 'post', data: { cypher } })

export interface MinioHealth {
  reachable: boolean
  endpoint: string
  bucketName: string
  bucketExists: boolean
  urlExpireDays: number
  message: string
}

export interface MinioObject {
  objectKey: string
  size: number
  lastModified?: string
  etag?: string
  presignedUrl?: string
}

export interface MinioUploadResult {
  objectKey: string
  bucketName: string
  size: number
  contentType?: string
  presignedUrl: string
}

export const agentMinioHealth = () =>
  request({ url: '/agent/minio/health', method: 'get' })

export const agentMinioListObjects = (prefix?: string, limit = 50) =>
  request({
    url: '/agent/minio/objects',
    method: 'get',
    params: { prefix, limit }
  })

export const agentMinioPresign = (objectKey: string) =>
  request({ url: '/agent/minio/presign', method: 'get', params: { objectKey } })

export const agentMinioDelete = (objectKey: string) =>
  request({ url: '/agent/minio/object', method: 'delete', params: { objectKey } })

export const agentMinioUpload = (file: File, prefix?: string) => {
  const form = new FormData()
  form.append('file', file)
  if (prefix) form.append('prefix', prefix)
  return request({
    url: '/agent/minio/upload',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}
