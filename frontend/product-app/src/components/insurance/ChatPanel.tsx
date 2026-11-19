import { useEffect, useRef, useState } from 'react'
import { Button, Collapse, Empty, Input, Popconfirm, Select, Space, Tag, Tooltip, theme } from 'antd'
import {
  DeleteOutlined,
  PlusOutlined,
  SendOutlined,
  StopOutlined,
  RobotOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { AgentSession, ChatMessageItem } from '../../api/insuranceAgent'
import MarkdownMessage from './MarkdownMessage'

interface Props {
  sessions: AgentSession[]
  activeSessionId?: string
  messages: ChatMessageItem[]
  sending: boolean
  onSend: (text: string) => void
  onStop: () => void
  onSelectSession: (id: string) => void
  onNewSession: () => void
  onDeleteSession: (id: string) => void
}

/**
 * 对话面板：会话切换 + 流式消息渲染 + 引用片段展示。
 * 流式内容由上层维护，本组件只负责展示与输入。
 */
function ChatPanel({
  sessions,
  activeSessionId,
  messages,
  sending,
  onSend,
  onStop,
  onSelectSession,
  onNewSession,
  onDeleteSession,
}: Props) {
  const { t } = useTranslation()
  const { token } = theme.useToken()
  const [draft, setDraft] = useState('')
  const listRef = useRef<HTMLDivElement>(null)

  // 新消息到达时滚动到底部
  useEffect(() => {
    const el = listRef.current
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages])

  const submit = () => {
    const text = draft.trim()
    if (!text || sending) return
    setDraft('')
    onSend(text)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 210px)' }}>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 260 }}
          placeholder={t('insurance.noSession')}
          value={activeSessionId}
          onChange={onSelectSession}
          options={sessions.map((session) => ({ label: session.title, value: session.id }))}
          notFoundContent={t('insurance.noSession')}
        />
        <Button icon={<PlusOutlined />} onClick={onNewSession}>
          {t('insurance.newSession')}
        </Button>
        <Tooltip title={t('insurance.deleteSession')}>
          <Popconfirm
            title={t('insurance.sessionDeleteConfirm')}
            onConfirm={() => activeSessionId && onDeleteSession(activeSessionId)}
            disabled={!activeSessionId}
          >
            <Button icon={<DeleteOutlined />} danger disabled={!activeSessionId} />
          </Popconfirm>
        </Tooltip>
      </Space>

      <div
        ref={listRef}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '8px 4px',
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: 8,
          background: token.colorBgContainer,
        }}
      >
        {messages.length === 0 ? (
          <Empty
            image={<RobotOutlined style={{ fontSize: 48, color: token.colorTextQuaternary }} />}
            description={t('insurance.empty')}
            style={{ marginTop: 48 }}
          />
        ) : (
          messages.map((item, index) => {
            const isUser = item.role === 'user'
            // 用户消息与错误提示保持纯文本；机器人回答按 Markdown 富文本渲染
            const plainText = isUser || item.error
            return (
              <div
                key={item.id ?? `${item.role}-${index}`}
                style={{
                  display: 'flex',
                  justifyContent: isUser ? 'flex-end' : 'flex-start',
                  marginBottom: 14,
                }}
              >
                <div
                  style={{
                    maxWidth: '84%',
                    padding: '10px 12px',
                    borderRadius: 8,
                    background: isUser ? token.colorPrimaryBg : token.colorFillSecondary,
                    color: item.error ? token.colorError : token.colorText,
                    minWidth: 0,
                    whiteSpace: plainText ? 'pre-wrap' : 'normal',
                    wordBreak: 'break-word',
                  }}
                >
                  <div style={{ fontSize: 12, opacity: 0.65, marginBottom: 4 }}>
                    {isUser ? t('insurance.you') : t('insurance.bot')}
                  </div>
                  {plainText ? (
                    item.content
                  ) : (
                    <MarkdownMessage content={item.content} streaming={item.streaming} />
                  )}
                  {!isUser && !!item.sources?.length && (
                    <Collapse
                      ghost
                      size="small"
                      style={{ marginTop: 8 }}
                      items={[
                        {
                          key: 'sources',
                          label: `${t('insurance.sources')} (${item.sources.length})`,
                          children: (
                            <Space direction="vertical" size={8} style={{ width: '100%' }}>
                              {item.sources.map((source, sourceIndex) => (
                                <div
                                  key={`${source.documentId}-${source.chunkIndex}-${sourceIndex}`}
                                  style={{ fontSize: 12, lineHeight: 1.6 }}
                                >
                                  <Tag color="blue">{source.filename}</Tag>
                                  <Tag>{t('insurance.sourceScore')} {source.score.toFixed(3)}</Tag>
                                  <div style={{ opacity: 0.75 }}>{source.snippet}</div>
                                </div>
                              ))}
                            </Space>
                          ),
                        },
                      ]}
                    />
                  )}
                </div>
              </div>
            )
          })
        )}
      </div>

      <div style={{ marginTop: 12 }}>
        <Input.TextArea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder={t('insurance.inputPlaceholder')}
          autoSize={{ minRows: 1, maxRows: 4 }}
          onPressEnter={(event) => {
            if (!event.shiftKey) {
              event.preventDefault()
              submit()
            }
          }}
          disabled={sending}
        />
        <div style={{ marginTop: 8, textAlign: 'right' }}>
          <Space>
            {sending ? (
              <Button icon={<StopOutlined />} onClick={onStop}>
                {t('insurance.stop')}
              </Button>
            ) : (
              <Button
                type="primary"
                icon={<SendOutlined />}
                onClick={submit}
                disabled={!draft.trim()}
              >
                {t('insurance.send')}
              </Button>
            )}
          </Space>
        </div>
      </div>
    </div>
  )
}

export default ChatPanel