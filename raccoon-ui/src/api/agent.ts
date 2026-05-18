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
