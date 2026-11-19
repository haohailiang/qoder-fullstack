/**
 * insuranceAgent SSE 客户端单元测试
 *
 * 覆盖：
 * - SSE 分帧（一次多帧 / 跨 chunk 半帧 / \r\n / 多行 data / 非法 JSON）
 * - consumeSseStream 读取 ReadableStream 并依序回调
 * - streamChat 携带 Bearer Token 且把事件透传给上层
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  createSseParser,
  consumeSseStream,
  streamChat,
  type SseEvent,
} from '../../../api/insuranceAgent'

describe('createSseParser', () => {
  it('应解析单个完整帧', () => {
    const events: SseEvent[] = []
    const parser = createSseParser((event) => events.push(event))

    parser.push('event: token\ndata: {"text":"你好"}\n\n')

    expect(events).toEqual([{ event: 'token', data: { text: '你好' } }])
  })

  it('应处理跨 chunk 的半帧', () => {
    const events: SseEvent[] = []
    const parser = createSseParser((event) => events.push(event))

    parser.push('event: tok')
    parser.push('en\ndata: {"text":"半')
    parser.push('帧"}\n\n')

    expect(events).toHaveLength(1)
    expect(events[0].event).toBe('token')
    expect(events[0].data.text).toBe('半帧')
  })

  it('应一次性解析多帧并兼容 CRLF', () => {
    const events: SseEvent[] = []
    const parser = createSseParser((event) => events.push(event))

    parser.push(
      'event: meta\r\ndata: {"sessionId":"s1"}\r\n\r\nevent: done\r\ndata: {"refused":true}\r\n\r\n',
    )

    expect(events.map((item) => item.event)).toEqual(['meta', 'done'])
    expect(events[1].data).toEqual({ refused: true })
  })

  it('应拼接多行 data 后再解析 JSON', () => {
    const events: SseEvent[] = []
    const parser = createSseParser((event) => events.push(event))

    parser.push('event: token\ndata: {"text":"第一行"}\ndata: \n\n')

    expect(events).toHaveLength(1)
    expect(events[0].data.text).toBe('第一行')
  })

  it('非法 JSON 帧应被忽略', () => {
    const events: SseEvent[] = []
    const parser = createSseParser((event) => events.push(event))

    parser.push('event: token\ndata: not-json\n\n')

    expect(events).toHaveLength(0)
  })

  it('flush 应处理没有结尾空行的残留帧', () => {
    const events: SseEvent[] = []
    const parser = createSseParser((event) => events.push(event))

    parser.push('event: token\ndata: {"text":"尾帧"}')
    parser.flush()

    expect(events).toHaveLength(1)
    expect(events[0].data.text).toBe('尾帧')
  })
})

describe('consumeSseStream', () => {
  it('应从字节流按序产出事件', async () => {
    const encoder = new TextEncoder()
    const chunks = [
      encoder.encode('event: meta\ndata: {"sessionId":"s9"}\n\n'),
      encoder.encode('event: token\ndata: {"text":"你"}\n\nevent: token\ndata: {"text":"好"}\n\n'),
    ]
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(chunk))
        controller.close()
      },
    })

    const events: SseEvent[] = []
    await consumeSseStream(stream, (event) => events.push(event))

    expect(events.map((item) => item.event)).toEqual(['meta', 'token', 'token'])
    expect(events.map((item) => item.data.text ?? item.data.sessionId)).toEqual([
      's9',
      '你',
      '好',
    ])
  })
})

describe('streamChat', () => {
  const originalFetch = global.fetch

  beforeEach(() => {
    localStorage.setItem('qoder_token', 'token-abc')
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('应携带 Bearer Token 并透传事件', async () => {
    const encoder = new TextEncoder()
    const fetchMock = vi.fn(async () => {
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(encoder.encode('event: token\ndata: {"text":"答案"}\n\n'))
          controller.close()
        },
      })
      return new Response(body, {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      })
    })
    global.fetch = fetchMock as unknown as typeof fetch

    const events: SseEvent[] = []
    await streamChat({
      sessionId: 'session-1',
      productId: 12,
      message: '等待期多久？',
      onEvent: (event) => events.push(event),
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('/api/ai/chat')
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer token-abc')
    expect(JSON.parse(init.body as string)).toEqual({
      sessionId: 'session-1',
      productId: 12,
      message: '等待期多久？',
    })
    expect(events).toHaveLength(1)
    expect(events[0].data.text).toBe('答案')
  })

  it('HTTP 失败时应抛出后端错误信息', async () => {
    global.fetch = vi.fn(async () =>
      new Response(JSON.stringify({ detail: '会话不存在' }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as unknown as typeof fetch

    await expect(
      streamChat({ sessionId: 'x', productId: 1, message: 'hi', onEvent: () => {} }),
    ).rejects.toThrow('会话不存在')
  })
})