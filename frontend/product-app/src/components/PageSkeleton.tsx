import { Button, Card, Empty, List, Skeleton, Space } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'

/**
 * 页面级加载占位组件。
 *
 * 统一使用 antd 的 Card/Skeleton 骨架，颜色与背景由 ConfigProvider 的主题算法决定，
 * dark/light 自动适配；同一区域只渲染骨架或 Spin 中的一种，避免叠加。
 */

/** 列表页骨架：与商品卡片网格同构，避免首屏白屏 */
export function ListSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div data-testid="page-skeleton">
      <List
        grid={{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 4, xxl: 4 }}
        dataSource={Array.from({ length: count }, (_, index) => index)}
        renderItem={(item) => (
          <List.Item key={item}>
            <Card loading />
          </List.Item>
        )}
      />
    </div>
  )
}

/** 详情页骨架：标题、描述、价格区一次性占位 */
export function DetailSkeleton() {
  return (
    <div data-testid="page-skeleton">
      <Card>
        <Skeleton active avatar paragraph={{ rows: 6 }} />
      </Card>
    </div>
  )
}

interface EmptyStateProps {
  description: string
  onRetry?: () => void
  onBack?: () => void
}

/** 请求失败/无数据时的兜底视图，用于退出骨架态，避免无限加载 */
export function EmptyState({ description, onRetry, onBack }: EmptyStateProps) {
  const { t } = useTranslation()
  return (
    <Card>
      <Empty description={description}>
        <Space>
          {onRetry && (
            <Button type="primary" onClick={onRetry}>
              {t('common.retry')}
            </Button>
          )}
          {onBack && (
            <Button icon={<ArrowLeftOutlined />} onClick={onBack}>
              {t('product.back')}
            </Button>
          )}
        </Space>
      </Empty>
    </Card>
  )
}
