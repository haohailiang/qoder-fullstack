import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Popconfirm, Space, Table, Tag, Upload, message } from 'antd'
import { InboxOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons'
import type { UploadProps } from 'antd'
import { useTranslation } from 'react-i18next'
import {
  AgentDocument,
  deleteDocument,
  listDocuments,
  reindexDocument,
  uploadDocument,
} from '../../api/insuranceAgent'

interface Props {
  productId: number
}

const MAX_PDF_MB = 20

function formatSize(bytes: number): string {
  if (!bytes) return '0 KB'
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(bytes / 1024))} KB`
}

/**
 * 知识库面板（仅管理员）：上传保险 PDF → MinerU 解析 → 切分向量化入库。
 * 解析在后台执行，这里轮询文档状态直到 ready/failed。
 */
function KnowledgePanel({ productId }: Props) {
  const { t } = useTranslation()
  const [documents, setDocuments] = useState<AgentDocument[]>([])
  const [loading, setLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [actionId, setActionId] = useState<string>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const list = await listDocuments(productId)
      setDocuments(list)
    } catch (error: any) {
      message.error(error.message || t('insurance.loadDocsFailed'))
    } finally {
      setLoading(false)
    }
  }, [productId, t])

  useEffect(() => {
    load()
  }, [load])

  // 有文档正在解析时轮询状态
  const hasProcessing = documents.some((doc) => doc.status === 'processing')
  useEffect(() => {
    if (!hasProcessing) return
    const timer = window.setInterval(load, 4000)
    return () => window.clearInterval(timer)
  }, [hasProcessing, load])

  const customRequest: UploadProps['customRequest'] = async (options: any) => {
    const file = options.file as File
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      message.error(t('insurance.onlyPdf'))
      options.onError?.(new Error('invalid type'))
      return
    }
    if (file.size > MAX_PDF_MB * 1024 * 1024) {
      message.error(t('insurance.tooLarge'))
      options.onError?.(new Error('too large'))
      return
    }

    setUploading(true)
    try {
      await uploadDocument(productId, file)
      options.onSuccess?.({})
      message.success(t('insurance.uploadSuccess'))
      await load()
    } catch (error: any) {
      options.onError?.(error)
      message.error(`${t('insurance.uploadFailed')}: ${error.message}`)
    } finally {
      setUploading(false)
    }
  }

  const handleReindex = async (docId: string) => {
    setActionId(docId)
    try {
      await reindexDocument(docId)
      message.success(t('insurance.reindexStarted'))
      await load()
    } catch (error: any) {
      message.error(error.message || t('insurance.reindexFailed'))
    } finally {
      setActionId(undefined)
    }
  }

  const handleDelete = async (docId: string) => {
    setActionId(docId)
    try {
      await deleteDocument(docId)
      message.success(t('insurance.docDeleted'))
      await load()
    } catch (error: any) {
      message.error(error.message || t('insurance.deleteFailed'))
    } finally {
      setActionId(undefined)
    }
  }

  const statusTag = (doc: AgentDocument) => {
    if (doc.status === 'ready') return <Tag color="green">{t('insurance.statusReady')}</Tag>
    if (doc.status === 'failed') return <Tag color="red">{t('insurance.statusFailed')}</Tag>
    return (
      <Tag color="processing" icon={<ReloadOutlined spin />}>
        {t('insurance.statusProcessing')}
      </Tag>
    )
  }

  return (
    <div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message={t('insurance.knowledgeHint')}
      />

      <Upload.Dragger
        accept=".pdf,application/pdf"
        multiple={false}
        showUploadList={false}
        customRequest={customRequest}
        disabled={uploading}
        style={{ marginBottom: 12 }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">{t('insurance.upload')}</p>
        <p className="ant-upload-hint">{t('insurance.uploadHint')}</p>
      </Upload.Dragger>

      <Table<AgentDocument>
        size="small"
        rowKey="id"
        loading={loading}
        dataSource={documents}
        pagination={false}
        locale={{ emptyText: t('insurance.noDocs') }}
        columns={[
          {
            title: t('insurance.filename'),
            dataIndex: 'filename',
            ellipsis: true,
          },
          {
            title: t('insurance.status'),
            width: 120,
            render: (_, doc) => statusTag(doc),
          },
          {
            title: t('insurance.chunkCount'),
            dataIndex: 'chunkCount',
            width: 90,
          },
          {
            title: t('insurance.fileSize'),
            dataIndex: 'fileSize',
            width: 100,
            render: (value: number) => formatSize(value),
          },
          {
            title: t('insurance.actions'),
            width: 160,
            render: (_, doc) => (
              <Space>
                <Button
                  size="small"
                  type="link"
                  loading={actionId === doc.id}
                  onClick={() => handleReindex(doc.id)}
                >
                  {t('insurance.reindex')}
                </Button>
                <Popconfirm
                  title={t('insurance.confirmDelete')}
                  onConfirm={() => handleDelete(doc.id)}
                >
                  <Button size="small" type="link" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              </Space>
            ),
          },
        ]}
        expandable={{
          expandedRowRender: (doc) =>
            doc.errorMessage ? (
              <div style={{ color: '#cf1322', whiteSpace: 'pre-wrap' }}>{doc.errorMessage}</div>
            ) : (
              <div style={{ opacity: 0.65 }}>{t('insurance.noError')}</div>
            ),
          rowExpandable: (doc) => doc.status === 'failed',
        }}
      />
    </div>
  )
}

export default KnowledgePanel