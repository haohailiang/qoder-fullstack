import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

interface RouteCity {
  name: string
  lng: number
  lat: number
}

interface FlightData {
  departureCity: string
  arrivalCity: string
  departureLng: number
  departureLat: number
  arrivalLng: number
  arrivalLat: number
  flightNumber: string
  duration: number
}

interface Props {
  flight: FlightData
  routeCities: RouteCity[]
  progress: number
}

function FlightInfoPanel({ flight, routeCities, progress }: Props) {
  const { t } = useTranslation()

  const elapsed = Math.floor(progress * flight.duration)
  const remaining = flight.duration - elapsed
  const elapsedStr = `${String(Math.floor(elapsed / 60)).padStart(2, '0')}:${String(elapsed % 60).padStart(2, '0')}`
  const remainingStr = `${String(Math.floor(remaining / 60)).padStart(2, '0')}:${String(remaining % 60).padStart(2, '0')}`

  // 模拟高度：抛物线，巡航高度 10,000-12,000m
  const altitude = useMemo(() => {
    const cruiseAlt = 11000
    return Math.round(Math.sin(progress * Math.PI) * cruiseAlt)
  }, [progress])

  // 当前接近的途经城市
  const nearestCity = useMemo(() => {
    if (routeCities.length === 0) return null
    // 按进度均匀分布途经城市
    const segCount = routeCities.length + 1
    const segIdx = Math.floor(progress * segCount)
    if (segIdx >= 0 && segIdx < routeCities.length) {
      return routeCities[segIdx]
    }
    return null
  }, [progress, routeCities])

  return (
    <div style={{
      position: 'absolute',
      top: 16,
      left: 16,
      background: 'rgba(0, 0, 0, 0.75)',
      backdropFilter: 'blur(10px)',
      borderRadius: 12,
      padding: '16px 20px',
      color: '#fff',
      minWidth: 220,
      zIndex: 10,
      boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
    }}>
      <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 12, color: '#438ede' }}>
        ✈ {flight.flightNumber} | {flight.departureCity} → {flight.arrivalCity}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 16px', fontSize: 13 }}>
        <div>
          <span style={{ color: '#999' }}>{t('flight.elapsed')}</span>
          <div style={{ fontSize: 18, fontWeight: 'bold', color: '#52c41a' }}>{elapsedStr}</div>
        </div>
        <div>
          <span style={{ color: '#999' }}>{t('flight.remaining')}</span>
          <div style={{ fontSize: 18, fontWeight: 'bold', color: '#ff6600' }}>{remainingStr}</div>
        </div>
        <div>
          <span style={{ color: '#999' }}>{t('flight.altitude')}</span>
          <div style={{ fontSize: 16, fontWeight: 'bold' }}>{altitude.toLocaleString()} m</div>
        </div>
        <div>
          <span style={{ color: '#999' }}>{t('flight.progress')}</span>
          <div style={{ fontSize: 16, fontWeight: 'bold' }}>{Math.round(progress * 100)}%</div>
        </div>
      </div>

      {nearestCity && (
        <div style={{
          marginTop: 12,
          padding: '8px 12px',
          background: 'rgba(245, 166, 35, 0.2)',
          borderRadius: 8,
          border: '1px solid rgba(245, 166, 35, 0.4)',
          fontSize: 13,
        }}>
          <span style={{ color: '#f5a623' }}>📍 {t('flight.approaching')}</span>
          <div style={{ fontSize: 16, fontWeight: 'bold', marginTop: 2 }}>{nearestCity.name}</div>
        </div>
      )}
    </div>
  )
}

export default FlightInfoPanel
