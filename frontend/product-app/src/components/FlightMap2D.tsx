import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

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
}

// 中国主要城市坐标库
const CITY_COORDS: Record<string, [number, number]> = {
  '北京': [116.4074, 39.9042], '上海': [121.4737, 31.2304],
  '广州': [113.2644, 23.1291], '深圳': [114.0579, 22.5431],
  '成都': [104.0668, 30.5728], '武汉': [114.3054, 30.5931],
  '重庆': [106.5516, 29.5630], '济南': [117.0009, 36.6758],
  '厦门': [118.0894, 24.4798], '长沙': [112.9388, 28.2282],
  '杭州': [120.1551, 30.2741], '南京': [118.7969, 32.0603],
  '西安': [108.9402, 34.2614], '昆明': [102.8329, 25.0389],
  '哈尔滨': [126.6424, 45.7570], '郑州': [113.6254, 34.7466],
  '青岛': [120.3826, 36.0671], '大连': [121.6147, 38.9140],
  '福州': [119.2965, 26.0745], '贵阳': [106.7135, 26.5783],
  '南宁': [108.3200, 22.8240], '兰州': [103.8343, 36.0611],
  '太原': [112.5489, 37.8706], '合肥': [117.2272, 31.8206],
  '天津': [117.1901, 39.1256], '沈阳': [123.4315, 41.8057],
  '长春': [125.3235, 43.8171], '石家庄': [114.5149, 38.0428],
}

function FlightMap2D({ flight, routeCities }: Props) {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstance = useRef<echarts.ECharts | null>(null)
  const mapRegistered = useRef(false)

  useEffect(() => {
    if (!chartRef.current) return

    chartInstance.current = echarts.init(chartRef.current)

    const initMap = async () => {
      if (!mapRegistered.current) {
        try {
          const resp = await fetch('https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json')
          const geoJson = await resp.json()
          echarts.registerMap('china', geoJson)
          mapRegistered.current = true
        } catch {
          // 地图加载失败时使用空背景
        }
      }
      updateChart()
    }

    initMap()

    const handleResize = () => chartInstance.current?.resize()
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
      chartInstance.current?.dispose()
    }
  }, [flight, routeCities])

  const updateChart = () => {
    if (!chartInstance.current) return

    const fromCoord: [number, number] = [flight.departureLng, flight.departureLat]
    const toCoord: [number, number] = [flight.arrivalLng, flight.arrivalLat]

    // 构建所有城市节点
    const allCities = [
      { name: flight.departureCity, coord: fromCoord },
      { name: flight.arrivalCity, coord: toCoord },
      ...routeCities.map((c) => ({ name: c.name, coord: [c.lng, c.lat] as [number, number] })),
    ]

    // 出发/到达城市散点
    const scatterData = allCities.map((city, idx) => ({
      name: city.name,
      value: [...city.coord, idx === 0 ? 40 : idx === 1 ? 40 : 20],
      itemStyle: {
        color: idx === 0 ? '#438ede' : idx === 1 ? '#52c41a' : '#f5a623',
      },
    }))

    // 飞线数据
    const linesData = [{ coords: [fromCoord, toCoord] }]

    // 分段飞线（经过途经城市）
    const segmentLines: Array<{ coords: [number, number][] }> = []
    if (routeCities.length > 0) {
      const path = [fromCoord, ...routeCities.map((c) => [c.lng, c.lat] as [number, number]), toCoord]
      for (let i = 0; i < path.length - 1; i++) {
        segmentLines.push({ coords: [path[i], path[i + 1]] })
      }
    }

    const isDark = (localStorage.getItem('qoder_theme') || 'dark') === 'dark'

    const option: echarts.EChartsOption = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        formatter: (params: any) => {
          if (params.seriesType === 'effectScatter' || params.seriesType === 'scatter') {
            const isDeparture = params.name === flight.departureCity
            const isArrival = params.name === flight.arrivalCity
            let label = `<strong>${params.name}</strong>`
            if (isDeparture) label += `<br/>🛫 ${'出发城市'}`
            if (isArrival) label += `<br/>🛬 ${'落地城市'}`
            return label
          }
          return ''
        },
      },
      geo: {
        map: 'china',
        roam: true,
        zoom: 1.3,
        center: [
          (fromCoord[0] + toCoord[0]) / 2,
          (fromCoord[1] + toCoord[1]) / 2,
        ],
        label: { show: false },
        itemStyle: {
          areaColor: isDark ? '#1a1a2e' : '#e8ecf1',
          borderColor: isDark ? '#444' : '#aaa',
          borderWidth: 1,
        },
        emphasis: {
          itemStyle: { areaColor: isDark ? '#2a2a4e' : '#d4dff0' },
          label: { show: false },
        },
        select: { disabled: true },
      },
      series: [
        // 城市节点（涟漪效果）
        {
          name: '城市',
          type: 'effectScatter',
          coordinateSystem: 'geo',
          data: scatterData.slice(0, 2), // 只有出发和到达城市
          symbolSize: 14,
          showEffectOn: 'render',
          rippleEffect: { brushType: 'stroke', scale: 4, period: 3 },
          label: {
            show: true,
            position: 'right',
            formatter: '{b}',
            fontSize: 13,
            fontWeight: 'bold',
            color: isDark ? '#eee' : '#333',
          },
          zlevel: 2,
        },
        // 途经城市节点
        {
          name: '途经城市',
          type: 'scatter',
          coordinateSystem: 'geo',
          data: scatterData.slice(2),
          symbolSize: 8,
          label: {
            show: true,
            position: 'right',
            formatter: '{b}',
            fontSize: 11,
            color: isDark ? '#bbb' : '#666',
          },
          itemStyle: { color: '#f5a623' },
          zlevel: 1,
        },
        // 粒子动画飞线
        {
          name: '航线',
          type: 'lines',
          coordinateSystem: 'geo',
          effect: {
            show: true,
            period: 4,
            trailLength: 0.5,
            symbol: 'arrow',
            symbolSize: 8,
            color: '#ff6600',
          },
          lineStyle: {
            color: '#438ede',
            width: 2,
            opacity: 0.4,
            curveness: 0.3,
          },
          data: linesData,
          zlevel: 3,
        },
        // 分段航线（途经城市）
        ...(segmentLines.length > 0
          ? [
              {
                name: '分段航线',
                type: 'lines' as const,
                coordinateSystem: 'geo' as const,
                effect: {
                  show: true,
                  period: 4,
                  trailLength: 0.3,
                  symbol: 'circle',
                  symbolSize: 4,
                  color: '#f5a623',
                },
                lineStyle: {
                  color: '#f5a623',
                  width: 1.5,
                  opacity: 0.3,
                  curveness: 0.2,
                },
                data: segmentLines,
                zlevel: 2,
              },
            ]
          : []),
      ],
    }

    chartInstance.current.setOption(option, true)
  }

  return <div ref={chartRef} style={{ width: '100%', height: '100%' }} />
}

export default FlightMap2D
