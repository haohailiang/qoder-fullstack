<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts'
import { getFlightFrequency, type CityFrequency, type FrequencyData } from '@/api/flight'

const { t } = useI18n()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const loading = ref(false)
const allFrequency = ref<CityFrequency[]>([])

// 筛选器
const departureCity = ref<string>('')
const arrivalCity = ref<string>('')

// 从数据中提取所有城市列表
const cityOptions = computed(() => {
  const cities = new Set<string>()
  allFrequency.value.forEach((f) => {
    cities.add(f.from)
    cities.add(f.to)
  })
  return Array.from(cities).sort()
})

// 筛选后的数据
const filteredData = computed(() => {
  let data = allFrequency.value
  if (departureCity.value) {
    data = data.filter((f) => f.from === departureCity.value)
  }
  if (arrivalCity.value) {
    data = data.filter((f) => f.to === arrivalCity.value)
  }
  return data
})

// 频率最大值（用于归一化飞线粗细）
const maxFrequency = computed(() => {
  if (filteredData.value.length === 0) return 1
  return Math.max(...filteredData.value.map((f) => f.frequency))
})

async function loadData() {
  loading.value = true
  try {
    const resp = await getFlightFrequency()
    allFrequency.value = (resp as unknown as FrequencyData).cityFrequency || []
    await nextTick()
    updateChart()
  } catch {
    ElMessage.error(t('flightMap.loadFailed'))
  } finally {
    loading.value = false
  }
}

// 中国主要城市坐标（内置，避免额外请求）
const cityCoords: Record<string, [number, number]> = {
  '北京': [116.4074, 39.9042],
  '上海': [121.4737, 31.2304],
  '广州': [113.2644, 23.1291],
  '深圳': [114.0579, 22.5431],
  '成都': [104.0668, 30.5728],
  '武汉': [114.3054, 30.5931],
  '重庆': [106.5516, 29.5630],
  '济南': [117.0009, 36.6758],
  '厦门': [118.0894, 24.4798],
  '长沙': [112.9388, 28.2282],
  '杭州': [120.1551, 30.2741],
  '南京': [118.7969, 32.0603],
  '西安': [108.9402, 34.2614],
  '昆明': [102.8329, 25.0389],
  '哈尔滨': [126.6424, 45.7570],
  '乌鲁木齐': [87.6177, 43.7928],
  '拉萨': [91.1322, 29.6604],
  '沈阳': [123.4315, 41.8057],
  '大连': [121.6147, 38.9140],
  '青岛': [120.3826, 36.0671],
  '郑州': [113.6254, 34.7466],
  '福州': [119.2965, 26.0745],
  '贵阳': [106.7135, 26.5783],
  '南宁': [108.3200, 22.8240],
  '兰州': [103.8343, 36.0611],
  '太原': [112.5489, 37.8706],
  '合肥': [117.2272, 31.8206],
  '天津': [117.1901, 39.1256],
  '长春': [125.3235, 43.8171],
  '石家庄': [114.5149, 38.0428],
  '呼和浩特': [111.7490, 40.8426],
  '银川': [106.2782, 38.4664],
  '西宁': [101.7782, 36.6171],
}

// 解析途经城市坐标
function parseRouteCoordinates(json: string): Array<{ name: string; lng: number; lat: number }> {
  try {
    return JSON.parse(json)
  } catch {
    return []
  }
}

function buildChartOption(data: CityFrequency[]): echarts.EChartsOption {
  // 构建飞线数据
  const linesData = data.map((item) => {
    const fromCoord: [number, number] = [Number(item.fromLng), Number(item.fromLat)]
    const toCoord: [number, number] = [Number(item.toLng), Number(item.toLat)]
    const ratio = item.frequency / maxFrequency.value
    return {
      fromName: item.from,
      toName: item.to,
      coords: [fromCoord, toCoord],
      frequency: item.frequency,
      lineStyle: {
        width: Math.max(1, ratio * 6),
        opacity: 0.4 + ratio * 0.5,
        curveness: 0.3,
      },
    }
  })

  // 构建粒子动画飞线数据
  const effectData = data.map((item) => {
    const fromCoord: [number, number] = [Number(item.fromLng), Number(item.fromLat)]
    const toCoord: [number, number] = [Number(item.toLng), Number(item.toLat)]
    return {
      fromName: item.from,
      toName: item.to,
      coords: [fromCoord, toCoord],
    }
  })

  // 构建城市节点数据（去重）
  const citySet = new Set<string>()
  data.forEach((d) => { citySet.add(d.from); citySet.add(d.to) })
  // 也加入途经城市
  data.forEach((d) => {
    const route = parseRouteCoordinates(d.routeCoordinates)
    route.forEach((r) => citySet.add(r.name))
  })

  const scatterData = Array.from(citySet)
    .filter((name) => cityCoords[name])
    .map((name) => ({
      name,
      value: [...cityCoords[name], citySet.size],
    }))

  // 途经城市散点数据
  const routePoints: Array<{ name: string; value: [number, number, number] }> = []
  data.forEach((d) => {
    const route = parseRouteCoordinates(d.routeCoordinates)
    route.forEach((r) => {
      if (!citySet.has(r.name) || routePoints.some((p) => p.name === r.name)) return
      routePoints.push({ name: r.name, value: [r.lng, r.lat, 1] })
    })
  })

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesType === 'effectScatter' || params.seriesType === 'scatter') {
          return `<strong>${params.name}</strong>`
        }
        if (params.seriesType === 'lines') {
          const d = params.data
          return `${d.fromName} → ${d.toName}<br/>${t('flightMap.flightCount')}: <strong>${d.frequency}</strong> ${t('flightMap.perWeek')}`
        }
        return ''
      },
    },
    geo: {
      map: 'china',
      roam: true,
      zoom: 1.2,
      center: [104, 36],
      label: { show: false },
      itemStyle: {
        areaColor: 'transparent',
        borderColor: '#555',
        borderWidth: 1,
      },
      emphasis: {
        itemStyle: { areaColor: 'rgba(67,142,230,0.15)' },
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
        data: scatterData,
        symbolSize: 10,
        showEffectOn: 'render',
        rippleEffect: { brushType: 'stroke', scale: 3, period: 4 },
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          fontSize: 11,
          color: '#ddd',
        },
        itemStyle: { color: '#438ede', shadowBlur: 10, shadowColor: '#438ede' },
        zlevel: 2,
      },
      // 途经城市节点（普通散点）
      {
        name: '途经城市',
        type: 'scatter',
        coordinateSystem: 'geo',
        data: routePoints,
        symbolSize: 6,
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          fontSize: 10,
          color: '#aaa',
        },
        itemStyle: { color: '#f5a623' },
        zlevel: 1,
      },
      // 飞线（粒子动画）
      {
        name: '航线',
        type: 'lines',
        coordinateSystem: 'geo',
        effect: {
          show: true,
          period: 5,
          trailLength: 0.4,
          symbol: 'arrow',
          symbolSize: 6,
          color: '#ff6600',
        },
        lineStyle: {
          color: (params: any) => {
            const ratio = params.data.frequency / maxFrequency.value
            const r = Math.round(70 + ratio * 185)
            const g = Math.round(174 - ratio * 72)
            const b = Math.round(226 - ratio * 226)
            return `rgb(${r},${g},${b})`
          },
          width: 1,
          opacity: 0.3,
          curveness: 0.3,
        },
        data: effectData,
        zlevel: 3,
      },
      // 静态飞线（粗线，反映频率）
      {
        name: '航线频率',
        type: 'lines',
        coordinateSystem: 'geo',
        lineStyle: {
          curveness: 0.3,
        },
        data: linesData,
        zlevel: 2,
      },
    ],
  }
}

function updateChart() {
  if (!chart) return
  const option = buildChartOption(filteredData.value)
  chart.setOption(option, true)
}

// 筛选变化时更新图表
watch([filteredData, maxFrequency], () => {
  updateChart()
}, { deep: true })

function handleReset() {
  departureCity.value = ''
  arrivalCity.value = ''
}

onMounted(async () => {
  // 加载中国地图 GeoJSON
  try {
    const resp = await fetch('https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json')
    const geoJson = await resp.json()
    echarts.registerMap('china', geoJson)
  } catch {
    ElMessage.error(t('flightMap.mapLoadFailed'))
    return
  }

  // 初始化 ECharts
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    // 窗口 resize 自适应
    window.addEventListener('resize', () => chart?.resize())
  }

  await loadData()
})

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
  window.removeEventListener('resize', () => {})
})
</script>

<template>
  <div class="flight-map-page">
    <el-card shadow="never" v-loading="loading">
      <div class="toolbar">
        <div class="filters">
          <el-select
            v-model="departureCity"
            :placeholder="t('flightMap.departureCity')"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="city in cityOptions"
              :key="'from-' + city"
              :label="city"
              :value="city"
            />
          </el-select>
          <span class="arrow">→</span>
          <el-select
            v-model="arrivalCity"
            :placeholder="t('flightMap.arrivalCity')"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="city in cityOptions"
              :key="'to-' + city"
              :label="city"
              :value="city"
            />
          </el-select>
          <el-button @click="handleReset">{{ t('flightMap.reset') }}</el-button>
        </div>
        <div class="stats">
          <el-tag type="info">{{ t('flightMap.routeCount') }}: {{ filteredData.length }}</el-tag>
          <el-tag type="warning">{{ t('flightMap.totalFrequency') }}: {{ filteredData.reduce((s, f) => s + f.frequency, 0) }}</el-tag>
        </div>
      </div>

      <div ref="chartRef" class="chart-container"></div>

      <!-- 航线列表 -->
      <el-table :data="filteredData" border stripe style="margin-top: 16px" max-height="300">
        <el-table-column prop="flightNumber" :label="t('flightMap.flightNo')" width="120" />
        <el-table-column prop="from" :label="t('flightMap.departure')" width="100" />
        <el-table-column prop="to" :label="t('flightMap.arrival')" width="100" />
        <el-table-column prop="duration" :label="t('flightMap.duration')" width="120">
          <template #default="{ row }">
            {{ Math.floor(row.duration / 60) }}h{{ row.duration % 60 }}m
          </template>
        </el-table-column>
        <el-table-column prop="frequency" :label="t('flightMap.frequency')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.frequency >= 21 ? 'danger' : row.frequency >= 14 ? 'warning' : 'info'">
              {{ row.frequency }}{{ t('flightMap.perWeek') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productName" :label="t('flightMap.productName')" min-width="200" />
        <el-table-column :label="t('flightMap.price')" width="120">
          <template #default="{ row }">
            <span style="color: var(--el-color-error); font-weight: 600">¥{{ row.productPrice }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.flight-map-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.arrow {
  font-size: 18px;
  color: var(--el-color-text-secondary, #999);
  font-weight: bold;
}

.stats {
  display: flex;
  gap: 8px;
}

.chart-container {
  width: 100%;
  height: 600px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
}
</style>
