import { Slider, Button, Space } from 'antd'
import { CaretRightOutlined, PauseOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'

interface Props {
  progress: number
  isPlaying: boolean
  duration: number
  onProgressChange: (value: number) => void
  onPlayToggle: () => void
}

function FlightAnimationController({ progress, isPlaying, duration, onProgressChange, onPlayToggle }: Props) {
  const { t } = useTranslation()

  const currentMin = Math.floor(progress * duration)
  const currentStr = `${String(Math.floor(currentMin / 60)).padStart(2, '0')}:${String(currentMin % 60).padStart(2, '0')}`
  const totalStr = `${String(Math.floor(duration / 60)).padStart(2, '0')}:${String(duration % 60).padStart(2, '0')}`

  return (
    <div style={{
      marginTop: 12,
      padding: '12px 20px',
      background: 'var(--ant-color-bg-container, #fff)',
      borderRadius: 8,
      border: '1px solid var(--ant-color-border, #d9d9d9)',
    }}>
      <Space style={{ width: '100%' }} size="middle">
        <Button
          type="primary"
          shape="circle"
          icon={isPlaying ? <PauseOutlined /> : <CaretRightOutlined />}
          onClick={onPlayToggle}
          size="middle"
        />
        <span style={{ fontSize: 13, color: 'var(--ant-color-text-secondary)', minWidth: 40 }}>
          {currentStr}
        </span>
        <Slider
          min={0}
          max={1}
          step={0.001}
          value={progress}
          onChange={onProgressChange}
          style={{ flex: 1, minWidth: 200 }}
          tooltip={{
            formatter: (val) => `${Math.round((val || 0) * 100)}%`,
          }}
        />
        <span style={{ fontSize: 13, color: 'var(--ant-color-text-secondary)', minWidth: 40 }}>
          {totalStr}
        </span>
      </Space>
    </div>
  )
}

export default FlightAnimationController
