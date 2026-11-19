import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Empty, List, Space, Tag, message } from 'antd'
import { HistoryOutlined, UndoOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import {
  AgentCheckpoint,
  ChatMessageItem,
  listCheckpoints,
  restoreCheckpoint,
} from '../../api/insuranceAgent'

interface Props {
  sessionId?: string
  onRestored: (messages: ChatMessageItem[]) => void
}

/**
 * 检查点面板（时空旅行）：列出 LangGraph 为该会话保存的历史状态，
 * 可恢复到任意一轮，恢复后后续对话从该状态继续。
 */
function CheckpointPanel({ sessionId, onRestored }: Props) {
  const { t } = useTranslation()
  const [checkpoints, setCheckpoints] = useState<AgentCheckpoint[]>([])
  const [loading, setLoading] = useState(false)
  const [restoring, setRestoring] = useState<string>()

  const load = useCallback(async () => {
    if (!sessionId) {
      setCheckpoints([])
      return
    }
    setLoading(true)
    try {
      setCheckpoints(await listCheckpoints(sessionId))
    } catch (error: any) {
      message.error(error.message || t('insurance.checkpointLoadFailed'))
    } finally {
      setLoading(false)
    }
  }, [sessionId, t])

  useEffect(() => {
    load()
  }, [load])

  const handleRestore = async (checkpointId: string) => {
    if (!sessionId) return
    setRestoring(checkpointId)
    try {
      const result = await restoreCheckpoint(sessionId, checkpointId)
      onRestored(result.messages || [])
      message.success(t('insurance.restoreSuccess'))
    } catch (error: any) {
      message.error(error.message || t('insurance.restoreFailed'))
    } finally {
      setRestoring(undefined)
    }
  }

  return (
    <div>
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 12 }}
        message={t('insurance.historyHint')}
      />
      <Space style={{ marginBottom: 12 }}>
        <Button icon={<HistoryOutlined />} onClick={load} loading={loading}>
          {t('insurance.refresh')}
        </Button>
      </Space>

      {checkpoints.length === 0 ? (
        <Empty description={t('insurance.noCheckpoints')} />
      ) : (
        <List
          size="small"
          dataSource={checkpoints}
          renderItem={(item) => (
            <List.Item
              actions={[
                <Button
                  key="restore"
                  size="small"
                  type="link"
                  icon={<UndoOutlined />}
                  loading={restoring === item.checkpointId}
                  onClick={() => handleRestore(item.checkpointId)}
                >
                  {t('insurance.restore')}
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <Tag>step {item.step ?? '-'}</Tag>
                    <span style={{ fontSize: 12, opacity: 0.65 }}>{item.createdAt}</span>
                    <Tag color="blue">{item.messageCount} {t('insurance.messages')}</Tag>
                  </Space>
                }
                description={<span style={{ fontSize: 12 }}>{item.preview || '-'}</span>}
              />
            </List.Item>
          )}
        />
      )}
    </div>
  )
}

export default CheckpointPanel