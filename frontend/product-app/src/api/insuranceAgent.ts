/**
 * 保险咨询机器人 API 客户端（SSE 流式）。
 *
 * 所有请求经网关 /api/ai/** 转发到 Python ai-service，
 * 网关已校验 JWT 并注入 X-User-Id / X-User-Role，前端只需带 Bearer Token。
 */

export interface AgentDocument {
  id: string
  productId: number
  filename: string
  fileSize: number
  status: 'processing' | 'ready' | 'failed'
  chunkCount: number
  errorMessage?: string | null
  createdBy?: string | null
  createdAt: string
  updatedAt: string
}

export interface AgentSession {
  id: string
  productId: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface AgentSource {
  documentId: string
  filename: string
  chunkIndex: number
  score: number
  snippet: string
}

export interface ChatMessageItem {
  id?: number | string
  role: 'user' | 'assistant'
  content: string
  sources?: AgentSource[]
  streaming?: boolean
  error?: boolean
}

export interface AgentCheckpoint {
  checkpointId: string
  step?: number
  createdAt: string
  messageCount: number
  preview: string
}

const AI_BASE = '/api/ai'

function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const token = localStorage.getItem('qoder_token')
  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(extra || {}),
  }
}

/** 解析后端错误：ai-service 返回 { detail }，网关返回 { code, message } */
async function toError(response: Response): Promise<Error> {
  let message = `HTTP ${response.status}`
  try {
    const body = await response.json()
    message = body?.detail || body?.message || message
  } catch {
    // 非 JSON 响应，保留状态码提示
  }
  return new Error(message)
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${AI_BASE}${path}`, {
    ...init,
    headers: authHeaders(init?.headers as Record<string, string> | undefined),
  })
  if (!response.ok) {
    throw await toError(response)
  }
  return (await response.json()) as T
}

// ---------- 会话 ----------
export function listSessions(productId: number): Promise<AgentSession[]> {
  return request<AgentSession[]>(`/sessions?productId=${productId}`)
}

export function createSession(productId: number): Promise<AgentSession> {
  return request<AgentSession>('/sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ productId }),
  })
}

export function deleteSession(sessionId: string): Promise<{ id: string }> {
  return request<{ id: string }>(`/sessions/${sessionId}`, { method: 'DELETE' })
}

export function listMessages(sessionId: string): Promise<ChatMessageItem[]> {
  return request<ChatMessageItem[]>(`/sessions/${sessionId}/messages`)
}

export function listCheckpoints(sessionId: string): Promise<AgentCheckpoint[]> {
  return request<AgentCheckpoint[]>(`/sessions/${sessionId}/checkpoints`)
}

export function restoreCheckpoint(
  sessionId: string,
  checkpointId: string,
): Promise<{ sessionId: string; checkpointId: string; messages: ChatMessageItem[] }> {
  return request(`/sessions/${sessionId}/restore`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ checkpointId }),
  })
}

// ---------- 知识库文档 ----------
export function listDocuments(productId: number): Promise<AgentDocument[]> {
  return request<AgentDocument[]>(`/documents?productId=${productId}`)
}

export function uploadDocument(productId: number, file: File): Promise<AgentDocument> {
  const form = new FormData()
  form.append('productId', String(productId))
  form.append('file', file)
  return request<AgentDocument>('/documents/upload', { method: 'POST', body: form })
}

export function reindexDocument(docId: string): Promise<AgentDocument> {
  return request<AgentDocument>(`/documents/${docId}/reindex`, { method: 'POST' })
}

export function deleteDocument(docId: string): Promise<{ id: string }> {
  return request<{ id: string }>(`/documents/${docId}`, { method: 'DELETE' })
}

// ---------- SSE ----------
export interface SseEvent {
  event: string
  data: any
}

/** 增量 SSE 解析器：按空行分帧，兼容 \r\n 与多行 data */
export function createSseParser(onEvent: (event: SseEvent) => void) {
  let buffer = ''

  const parseBlock = (block: string): SseEvent | null => {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''))
      }
    }
    if (dataLines.length === 0) return null
    try {
      return { event, data: JSON.parse(dataLines.join('\n')) }
    } catch {
      return null
    }
  }

  const drain = () => {
    let index = buffer.indexOf('\n\n')
    while (index >= 0) {
      const block = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      const parsed = parseBlock(block)
      if (parsed) onEvent(parsed)
      index = buffer.indexOf('\n\n')
    }
  }

  return {
    push(chunk: string) {
      buffer += chunk.replace(/\r\n/g, '\n')
      drain()
    },
    flush() {
      if (buffer.trim()) {
        const parsed = parseBlock(buffer)
        if (parsed) onEvent(parsed)
      }
      buffer = ''
    },
  }
}

/** 读取 fetch 的流式响应并按 SSE 事件回调 */
export async function consumeSseStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: SseEvent) => void,
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  const parser = createSseParser(onEvent)
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      if (value) parser.push(decoder.decode(value, { stream: true }))
    }
    parser.push(decoder.decode())
    parser.flush()
  } finally {
    reader.releaseLock()
  }
}

export interface StreamChatOptions {
  sessionId: string
  productId: number
  message: string
  signal?: AbortSignal
  onEvent: (event: SseEvent) => void
}

/** 发起流式问答（POST + SSE） */
export async function streamChat({
  sessionId,
  productId,
  message,
  signal,
  onEvent,
}: StreamChatOptions): Promise<void> {
  const response = await fetch(`${AI_BASE}/chat`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ sessionId, productId, message }),
    signal,
  })
  if (!response.ok || !response.body) {
    throw await toError(response)
  }
  await consumeSseStream(response.body, onEvent)
}