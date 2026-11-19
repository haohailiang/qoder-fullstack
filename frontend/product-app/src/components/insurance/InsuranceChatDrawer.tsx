import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Drawer, Tabs, message } from 'antd'
import { BookOutlined, HistoryOutlined, RobotOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import ChatPanel from './ChatPanel'
import CheckpointPanel from './CheckpointPanel'
import KnowledgePanel from './KnowledgePanel'
import {
  AgentSession,
  ChatMessageItem,
  createSession,
  deleteSession,
  listMessages,
  listSessions,
  streamChat,
} from '../../api/insuranceAgent'

interface Props {
  open: boolean
  productId: number
  productName: string
  onClose: () => void
}

/** 从本地登录态读取是否管理员（与 base-app 写入的 qoder_user 保持一致） */
function readIsAdmin(): boolean {
  try {
    const raw = localStorage.getItem('qoder_user')
    if (!raw) return false
    const user = JSON.parse(raw)
    const role = String(user?.role || '').toUpperCase()
    return role === 'ADMIN' || user?.username === 'admin'
  } catch {
    return false
  }
}

/**
 * 保险咨询机器人抽屉：编排会话、流式问答、知识库（管理员）与检查点回溯。
 * 流式内容由本组件持有，子面板只做展示与交互。
 */
function InsuranceChatDrawer({ open, productId, productName, onClose }: Props) {
  const { t } = useTranslation()
  const [isAdmin] = useState(readIsAdmin)
  const [sessions, setSessions] = useState<AgentSession[]>([])
  const [activeSessionId, setActiveSessionId] = useState<string>()
  const [messages, setMessages] = useState<ChatMessageItem[]>([])
  const [sending, setSending] = useState(false)
  const [tab, setTab] = useState('chat')
  const abortRef = useRef<AbortController>()

  const loadSessions = useCallback(async (): Promise<AgentSession[]> => {
    try {
      const list = await listSessions(productId)
      setSessions(list)
      return list
    } catch (error: any) {
      message.error(error.message || t('insurance.sessionLoadFailed'))
      return []
    }
  }, [productId, t])

  const loadMessages = useCallback(
    async (sessionId: string) => {
      try {
        setMessages(await listMessages(sessionId))
      } catch (error: any) {
        message.error(error.message || t('insurance.messageLoadFailed'))
      }
    },
    [t],
  )

  // 打开抽屉时加载会话；默认选中最近一个会话并回显历史。
  // 只依赖 open/productId：避免 i18n 的 t 之类引用变化导致重复拉取。
  useEffect(() => {
    if (!open) return
    let cancelled = false
    setMessages([])
    setActiveSessionId(undefined)
    ;(async () => {
      try {
        const list = await listSessions(productId)
        if (cancelled) return
        setSessions(list)
        const next = list[0]
        if (!next) return
        setActiveSessionId(next.id)
        const history = await listMessages(next.id)
        if (!cancelled) setMessages(history)
      } catch (error: any) {
        if (!cancelled) {
          message.error(error.message || t('insurance.sessionLoadFailed'))
        }
      }
    })()
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, productId])

  // 关闭抽屉时中断未完成的流式请求
  useEffect(() => {
    if (open) return
    abortRef.current?.abort()
  }, [open])

  const handleSelectSession = (id: string) => {
    if (id === activeSessionId) return
    abortRef.current?.abort()
    setActiveSessionId(id)
    setMessages([])
    loadMessages(id)
  }

  const handleNewSession = async () => {
    try {
      abortRef.current?.abort()
      const session = await createSession(productId)
      setSessions((prev) => [session, ...prev])
      setActiveSessionId(session.id)
      setMessages([])
      setTab('chat')
    } catch (error: any) {
      message.error(error.message || t('insurance.createSessionFailed'))
    }
  }

  const handleDeleteSession = async (id: string) => {
    try {
      abortRef.current?.abort()
      await deleteSession(id)
      const rest = sessions.filter((item) => item.id !== id)
      setSessions(rest)
      if (activeSessionId === id) {
        const next = rest[0]
        setActiveSessionId(next?.id)
        setMessages([])
        if (next) await loadMessages(next.id)
      }
    } catch (error: any) {
      message.error(error.message || t('insurance.deleteSessionFailed'))
    }
  }

  const handleSend = async (text: string) => {
    if (sending) return
    setSending(true)

    let sessionId = activeSessionId
    try {
      if (!sessionId) {
        const session = await createSession(productId)
        setSessions((prev) => [session, ...prev])
        sessionId = session.id
        setActiveSessionId(session.id)
      }

      const controller = new AbortController()
      abortRef.current = controller

      const stamp = Date.now()
      setMessages((prev) => [
        ...prev,
        { id: `u-${stamp}`, role: 'user', content: text },
        { id: `a-${stamp}`, role: 'assistant', content: '', streaming: true },
      ])

      await streamChat({
        sessionId,
        productId,
        message: text,
        signal: controller.signal,
        onEvent: ({ event, data }) => {
          if (event === 'token') {
            const piece = String(data?.text || '')
            setMessages((prev) => {
              const next = [...prev]
              const last = next[next.length - 1]
              if (last && last.role === 'assistant') {
                next[next.length - 1] = {
                  ...last,
                  content: last.content + piece,
                  streaming: true,
                }
              }
              return next
            })
          } else if (event === 'done') {
            setMessages((prev) => {
              const next = [...prev]
              const last = next[next.length - 1]
              if (last && last.role === 'assistant') {
                next[next.length - 1] = {
                  ...last,
                  sources: data?.sources || [],
                  streaming: false,
                }
              }
              return next
            })
            // 首轮问答后标题会更新，重新拉取会话列表
            loadSessions()
          } else if (event === 'error') {
            setMessages((prev) => {
              const next = [...prev]
              const last = next[next.length - 1]
              if (last && last.role === 'assistant') {
                next[next.length - 1] = {
                  ...last,
                  content: last.content || String(data?.message || t('insurance.chatFailed')),
                  streaming: false,
                  error: true,
                }
              }
              return next
            })
          }
        },
      })
    } catch (error: any) {
      if (error?.name === 'AbortError') {
        // 用户主动停止：保留已生成内容
        setMessages((prev) => {
          const next = [...prev]
          const last = next[next.length - 1]
          if (last && last.role === 'assistant') {
            next[next.length - 1] = { ...last, streaming: false }
          }
          return next
        })
      } else {
        message.error(error.message || t('insurance.chatFailed'))
        setMessages((prev) => {
          const next = [...prev]
          const last = next[next.length - 1]
          if (last && last.role === 'assistant' && !last.content) {
            next[next.length - 1] = {
              ...last,
              content: error.message || t('insurance.chatFailed'),
              streaming: false,
              error: true,
            }
          }
          return next
        })
      }
    } finally {
      abortRef.current = undefined
      setSending(false)
    }
  }

  const handleStop = () => {
    abortRef.current?.abort()
  }

  const handleRestored = (restored: ChatMessageItem[]) => {
    abortRef.current?.abort()
    setMessages(restored)
    setTab('chat')
    loadSessions()
  }

  const tabItems = [
    {
      key: 'chat',
      label: (
        <span>
          <RobotOutlined /> {t('insurance.tabChat')}
        </span>
      ),
      children: (
        <ChatPanel
          sessions={sessions}
          activeSessionId={activeSessionId}
          messages={messages}
          sending={sending}
          onSend={handleSend}
          onStop={handleStop}
          onSelectSession={handleSelectSession}
          onNewSession={handleNewSession}
          onDeleteSession={handleDeleteSession}
        />
      ),
    },
    {
      key: 'checkpoints',
      label: (
        <span>
          <HistoryOutlined /> {t('insurance.tabCheckpoints')}
        </span>
      ),
      children: (
        <CheckpointPanel sessionId={activeSessionId} onRestored={handleRestored} />
      ),
    },
  ]

  if (isAdmin) {
    tabItems.push({
      key: 'knowledge',
      label: (
        <span>
          <BookOutlined /> {t('insurance.tabKnowledge')}
        </span>
      ),
      children: <KnowledgePanel productId={productId} />,
    })
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={720}
      destroyOnClose={false}
      title={`${productName} · ${t('insurance.drawerTitle')}`}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message={t('insurance.scopeHint')}
      />
      <Tabs activeKey={tab} onChange={setTab} items={tabItems} />
    </Drawer>
  )
}

export default InsuranceChatDrawer