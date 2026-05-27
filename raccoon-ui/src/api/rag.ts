import request from '@/utils/request'

/** 文档入库结果 */
export interface IngestResult {
  docId: string
  fileName: string
  uploadTime: number
  deviceId?: string
  docType?: string
  chunkCount: number
  minioObjectKey: string
  minioUrl: string
}

/** 文档列表项（一个文档可关联多个设备） */
export interface RagDocument {
  docId: string
  fileName: string
  docType?: string
  devices?: RagDevice[]
  uploadTime: number
  chunkCount?: number
  minioObjectKey: string
}

/** 设备 */
export interface RagDevice {
  deviceId: string
  name?: string
  type?: string
  station?: string
}

/** 引用 */
export interface RagReference {
  source: 'vector' | 'graph'
  docId?: string
  fileName?: string
  chunkIndex?: number
  score?: number
  content: string
  minioUrl?: string
  deviceId?: string
}

/** 设备上下文（用于折叠面板展示） */
export interface RagDeviceContext {
  device?: RagDevice
  faults?: Array<{
    faultCode?: string
    name?: string
    description?: string
    level?: number
  }>
  upstreamDevices?: string[]
  downstreamDevices?: string[]
}

export interface RagChatRequestBody {
  question: string
  deviceId?: string
  topK?: number
}

export interface RagChatResponse {
  answer: string
  references: RagReference[]
  deviceContext?: RagDeviceContext
  elapsedMs: number
  model: string
}

const BASE = '/agent/api/rag'

/** 上传 PDF 入库；onUploadProgress 用于显示真实文件上传进度 */
export const ragIngest = (
  file: File,
  deviceId: string,
  docType: string,
  onUploadProgress?: (percent: number) => void
) => {
  const form = new FormData()
  form.append('file', file)
  if (deviceId) form.append('deviceId', deviceId)
  if (docType) form.append('docType', docType)
  return request({
    url: `${BASE}/ingest`,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000,
    onUploadProgress: (evt: { loaded: number; total?: number }) => {
      if (!onUploadProgress) return
      const total = evt.total ?? file.size
      if (total > 0) {
        const percent = Math.round((evt.loaded / total) * 100)
        onUploadProgress(Math.min(percent, 100))
      }
    }
  } as any)
}

/** 混合检索 RAG 问答 */
export const ragChat = (data: RagChatRequestBody) =>
  request({
    url: `${BASE}/chat`,
    method: 'post',
    data,
    timeout: 120000
  })

/** 文档列表 */
export const ragListDocuments = (params: { keyword?: string; deviceId?: string }) =>
  request({
    url: `${BASE}/documents`,
    method: 'get',
    params
  })

/** 删除文档（同步清理 Milvus + Neo4j + MinIO） */
export const ragDeleteDocument = (docId: string) =>
  request({
    url: `${BASE}/documents/${encodeURIComponent(docId)}`,
    method: 'delete'
  })

/** 设备列表 */
export const ragListDevices = () =>
  request({
    url: `${BASE}/devices`,
    method: 'get'
  })

/** 重新生成 PDF 预签名 URL */
export const ragPreviewUrl = (docId: string) =>
  request({
    url: `${BASE}/documents/${encodeURIComponent(docId)}/preview-url`,
    method: 'get'
  })

/** 清理 Neo4j 中 docId / fileName / uploadTime 缺失的 Document 节点 */
export const ragCleanupOrphans = () =>
  request({
    url: `${BASE}/documents/cleanup`,
    method: 'post'
  })

/** 关联设备入参（来自 CMMS 设备管理，关联时同步写入 Neo4j） */
export interface RagDeviceAttachInput {
  deviceId: string
  name?: string
  type?: string
  station?: string
}

/** 给文档批量关联设备（追加，并 MERGE 设备主数据到 Neo4j） */
export const ragAttachDevices = (docId: string, devices: RagDeviceAttachInput[]) =>
  request({
    url: `${BASE}/documents/${encodeURIComponent(docId)}/devices`,
    method: 'post',
    data: { devices }
  })

/** 解除文档与某个设备的关联 */
export const ragDetachDevice = (docId: string, deviceId: string) =>
  request({
    url: `${BASE}/documents/${encodeURIComponent(docId)}/devices/${encodeURIComponent(deviceId)}`,
    method: 'delete'
  })

// ============ 存储查看（Neo4j / Milvus） ============

export interface Neo4jLabelStat { label: string; count: number }
export interface Neo4jRelTypeStat { type: string; count: number }
export interface Neo4jOverview {
  totalNodes: number
  totalRelationships: number
  labels: Neo4jLabelStat[]
  relationshipTypes: Neo4jRelTypeStat[]
}

export interface Neo4jNode {
  internalId: number
  labels: string[]
  properties: Record<string, any>
  relatedDevices?: Array<Record<string, any>>
  relatedDocuments?: Array<Record<string, any>>
}

export interface Neo4jRelationship {
  relInternalId: number
  type: string
  properties: Record<string, any>
  startLabel: string
  startNode: Record<string, any>
  endLabel: string
  endNode: Record<string, any>
}

export interface MilvusFieldInfo {
  name: string
  dataType: string
  primaryKey: boolean
  maxLength?: number
  dimension?: number
}

export interface MilvusCollectionStats {
  databaseName: string
  collectionName: string
  rowCount: number
  dimension: number
  indexType?: string
  metricType?: string
  fields: MilvusFieldInfo[]
}

export interface MilvusChunk {
  docPk: string
  docId?: string
  fileName?: string
  chunkIndex?: number
  content: string
  metadata: Record<string, any>
}

const STORAGE_BASE = '/agent/api/rag/storage'

export const ragNeo4jOverview = () =>
  request({ url: `${STORAGE_BASE}/neo4j/overview`, method: 'get' })

export const ragNeo4jSchema = () =>
  request({ url: `${STORAGE_BASE}/neo4j/schema`, method: 'get' })

export const ragNeo4jNodes = (label: string, limit = 100) =>
  request({ url: `${STORAGE_BASE}/neo4j/nodes`, method: 'get', params: { label, limit } })

export const ragNeo4jRelationships = (type: string, limit = 100) =>
  request({ url: `${STORAGE_BASE}/neo4j/relationships`, method: 'get', params: { type, limit } })

export const ragMilvusOverview = () =>
  request({ url: `${STORAGE_BASE}/milvus/overview`, method: 'get' })

export const ragMilvusChunks = (params: { docId?: string; limit?: number }) =>
  request({ url: `${STORAGE_BASE}/milvus/chunks`, method: 'get', params })

export const ragDeleteNeo4jNode = (internalId: number, label: string) =>
  request({
    url: `${STORAGE_BASE}/neo4j/nodes`,
    method: 'delete',
    params: { internalId, label }
  })

export const ragDeleteNeo4jRelationship = (relInternalId: number, type: string) =>
  request({
    url: `${STORAGE_BASE}/neo4j/relationships`,
    method: 'delete',
    params: { relInternalId, type }
  })

export const ragDeleteMilvusChunk = (docPk: string) =>
  request({
    url: `${STORAGE_BASE}/milvus/chunks`,
    method: 'delete',
    params: { docPk }
  })
