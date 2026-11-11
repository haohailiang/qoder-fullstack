import { useEffect, useState, useCallback } from 'react'
import { Card, Button, Tag, Space, message, Divider, Segmented } from 'antd'
import {
  ArrowLeftOutlined,
  GlobalOutlined,
  RocketOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import { useParams, useNavigate } from 'react-router-dom'
import FlightMap2D from '../components/FlightMap2D'
import FlightMap3D from '../components/FlightMap3D'
import FlightInfoPanel from '../components/FlightInfoPanel'
import FlightAnimationController from '../components/FlightAnimationController'

interface RouteCity {
  name: string
  lng: number
  lat: number
}

interface FlightData {
  id: number
  productId: number
  departureCity: string
  arrivalCity: string
  departureLng: number
  departureLat: number
  arrivalLng: number
  arrivalLat: number
  flightNumber: string
  duration: number
  frequency: number
  routeCoordinates: string
  productName: string
  productDescription: string
  productPrice: number
  productStock: number
  productStatus: number
}

type MapMode = '2D' | '3D'
type ViewMode = 'overview' | 'planeView'

function FlightDetail() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [flight, setFlight] = useState<FlightData | null>(null)
  const [mapMode, setMapMode] = useState<MapMode>('2D')
  const [viewMode, setViewMode] = useState<ViewMode>('overview')
  const [animProgress, setAnimProgress] = useState(0)
  const [isPlaying, setIsPlaying] = useState(false)

  const isDark = (localStorage.getItem('qoder_theme') || 'dark') === 'dark'
  const pageBg = isDark ? '#141414' : '#f5f5f5'

  useEffect(() => {
    fetchFlight()
  }, [id])

  const fetchFlight = async () => {
    try {
      const token = localStorage.getItem('qoder_token')
      const response = await axios.get(`/api/product/flights/${id}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (response.data.code === 200) {
        setFlight(response.data.data)
      }
    } catch {
      message.error(t('flight.fetchFailed'))
    }
  }

  const parseRouteCities = useCallback((): RouteCity[] => {
    if (!flight?.routeCoordinates) return []
    try {
      return JSON.parse(flight.routeCoordinates)
    } catch {
      return []
    }
  }, [flight?.routeCoordinates])

  const handleProgressChange = useCallback((value: number) => {
    setAnimProgress(value)
  }, [])

  const handlePlayToggle = useCallback(() => {
    setIsPlaying((prev) => !prev)
  }, [])

  if (!flight) {
    return (
      <div style={{ minHeight: '100vh', padding: 24, background: pageBg }}>
        <div style={{ maxWidth: 1200, margin: '0 auto' }}>
          <Card loading />
        </div>
      </div>
    )
  }

  const durationHours = Math.floor(flight.duration / 60)
  const durationMins = flight.duration % 60
  const durationStr = `${durationHours}h${durationMins > 0 ? ` ${durationMins}m` : ''}`

  return (
    <div style={{ minHeight: '100vh', padding: 24, background: pageBg }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        {/* 航班信息卡片 */}
        <Card style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <Space size="large" align="center">
                <span style={{ fontSize: 14, color: 'var(--ant-color-text-tertiary)' }}>
                  {flight.flightNumber}
                </span>
                <Tag color="blue">
                  <RocketOutlined /> {t('flight.departureCity')}: {flight.departureCity}
                </Tag>
                <span style={{ fontSize: 20, color: 'var(--ant-color-text-secondary)' }}>→</span>
                <Tag color="green">
                  <RocketOutlined /> {t('flight.arrivalCity')}: {flight.arrivalCity}
                </Tag>
              </Space>
              <div style={{ marginTop: 12, color: 'var(--ant-color-text-secondary)' }}>
                {flight.productDescription}
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ color: 'var(--ant-color-error)', fontSize: 28, fontWeight: 'bold' }}>
                ¥{flight.productPrice}
              </div>
              <Space style={{ marginTop: 8 }}>
                <Tag>{t('flight.duration')}: {durationStr}</Tag>
                <Tag color="orange">{t('flight.frequency')}: {flight.frequency}{t('flight.perWeek')}</Tag>
                <Tag color="green">{t('flight.stock')}: {flight.productStock}</Tag>
              </Space>
            </div>
          </div>
        </Card>

        {/* 地图模式切换 */}
        <Card
          style={{ marginBottom: 0, borderRadius: '8px 8px 0 0' }}
          styles={{ body: { padding: '12px 16px' } }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Segmented
              value={mapMode}
              onChange={(val) => setMapMode(val as MapMode)}
              options={[
                { label: <Space><GlobalOutlined />{t('flight.map2D')}</Space>, value: '2D' },
                { label: <Space><RocketOutlined />{t('flight.map3D')}</Space>, value: '3D' },
              ]}
            />
            {mapMode === '3D' && (
              <Segmented
                value={viewMode}
                onChange={(val) => setViewMode(val as ViewMode)}
                options={[
                  { label: t('flight.overview'), value: 'overview' },
                  { label: <Space><RocketOutlined />{t('flight.planeView')}</Space>, value: 'planeView' },
                ]}
              />
            )}
          </div>
        </Card>

        {/* 地图区域 */}
        <div style={{
          height: 550,
          borderRadius: '0 0 8px 8px',
          overflow: 'hidden',
          position: 'relative',
          border: '1px solid var(--ant-color-border)',
          borderTop: 'none',
        }}>
          {mapMode === '2D' ? (
            <FlightMap2D flight={flight} routeCities={parseRouteCities()} />
          ) : (
            <FlightMap3D
              flight={flight}
              routeCities={parseRouteCities()}
              viewMode={viewMode}
              animProgress={animProgress}
              isPlaying={isPlaying}
              onProgressChange={handleProgressChange}
              onPlayToggle={handlePlayToggle}
            />
          )}

          {/* 飞行信息面板（3D 飞机视角时显示） */}
          {mapMode === '3D' && viewMode === 'planeView' && (
            <FlightInfoPanel
              flight={flight}
              routeCities={parseRouteCities()}
              progress={animProgress}
            />
          )}
        </div>

        {/* 动画控制器（3D 模式下显示） */}
        {mapMode === '3D' && (
          <FlightAnimationController
            progress={animProgress}
            isPlaying={isPlaying}
            duration={flight.duration}
            onProgressChange={handleProgressChange}
            onPlayToggle={handlePlayToggle}
          />
        )}

        {/* 底部操作 */}
        <Divider />
        <Space>
          <Button
            type="primary"
            size="large"
            icon={<RocketOutlined />}
            onClick={() => {
              message.info(t('flight.comingSoon'))
            }}
          >
            {t('flight.buyTicket')}
          </Button>
          <Button size="large" icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
            {t('flight.back')}
          </Button>
        </Space>
      </div>
    </div>
  )
}

export default FlightDetail
