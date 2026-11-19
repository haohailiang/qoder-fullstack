/**
 * ChatPanel 消息渲染测试
 *
 * 覆盖：
 * - 机器人消息按 Markdown 富文本渲染（标题 / 列表 / 表格）
 * - 用户消息、错误提示保持纯文本，不解析 Markdown
 * - 流式追加 token 时内容累加，结束后光标消失且格式正确
 * - 来源片段折叠面板仍正常展示
 */
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

import ChatPanel from '@/components/insurance/ChatPanel'
import type { ChatMessageItem } from '@/api/insuranceAgent'

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }),
})

const baseProps = {
  sessions: [],
  activeSessionId: undefined,
  sending: false,
  onSend: vi.fn(),
  onStop: vi.fn(),
  onSelectSession: vi.fn(),
  onNewSession: vi.fn(),
  onDeleteSession: vi.fn(),
}

const assistantContent = `## 等待期

本产品等待期为 **30 天**：

- 首次投保：30 天
- 续保：无等待期

| 项目 | 说明 |
| --- | --- |
| 免赔额 | 1 万元 |
`

describe('ChatPanel - Markdown 消息渲染', () => {
  it('机器人消息应渲染为富文本，用户消息保持纯文本', () => {
    const messages: ChatMessageItem[] = [
      { id: 'u1', role: 'user', content: '**加粗** 的用户提问' },
      { id: 'a1', role: 'assistant', content: assistantContent },
    ]

    const { container } = render(<ChatPanel {...baseProps} messages={messages} />)

    // 用户消息原样展示，不解析 Markdown
    expect(screen.getByText('**加粗** 的用户提问')).toBeInTheDocument()

    // 机器人消息渲染为富文本结构
    expect(container.querySelector('h2')?.textContent).toBe('等待期')
    expect(container.querySelectorAll('li')).toHaveLength(2)
    expect(container.querySelectorAll('table tbody tr')).toHaveLength(1)
    expect(container.textContent).not.toContain('## 等待期')
    expect(container.textContent).not.toContain('| 项目 |')
  })

  it('错误提示应作为纯文本展示，不解析 Markdown', () => {
    const messages: ChatMessageItem[] = [
      { id: 'a1', role: 'assistant', content: '*请求失败*', error: true },
    ]

    const { container } = render(<ChatPanel {...baseProps} messages={messages} />)

    expect(screen.getByText('*请求失败*')).toBeInTheDocument()
    expect(container.querySelector('em')).toBeNull()
  })

  it('流式追加时内容累加、格式正确，结束后光标消失', () => {
    const initial: ChatMessageItem[] = [
      { id: 'a1', role: 'assistant', content: '## 等待期', streaming: true },
    ]

    const { container, rerender } = render(<ChatPanel {...baseProps} messages={initial} />)
    expect(container.textContent).toContain('▍')

    rerender(
      <ChatPanel
        {...baseProps}
        messages={[
          {
            id: 'a1',
            role: 'assistant',
            content: '## 等待期\n\n- 首次投保：30 天',
            streaming: true,
          },
        ]}
      />,
    )
    expect(container.querySelectorAll('li')).toHaveLength(1)

    rerender(
      <ChatPanel
        {...baseProps}
        messages={[
          {
            id: 'a1',
            role: 'assistant',
            content: assistantContent,
            streaming: false,
            sources: [
              {
                documentId: 'doc-1',
                filename: '安心百万医疗险条款.pdf',
                chunkIndex: 0,
                score: 0.9,
                snippet: '等待期为 30 天',
              },
            ],
          },
        ]}
      />,
    )

    expect(container.querySelector('h2')?.textContent).toBe('等待期')
    expect(container.querySelectorAll('li')).toHaveLength(2)
    expect(container.querySelectorAll('table tbody tr')).toHaveLength(1)
    expect(container.textContent).not.toContain('▍')
    // 来源列表仍按原逻辑展示（折叠未展开时只渲染标题）
    expect(screen.getByText(/insurance\.sources \(1\)/)).toBeInTheDocument()
  })
})
