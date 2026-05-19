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
