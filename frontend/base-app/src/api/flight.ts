import request from './request'

/** 航班频率统计中的单条航线数据 */
export interface CityFrequency {
  id: number
  from: string
  to: string
  fromLng: number
  fromLat: number
  toLng: number
  toLat: number
  frequency: number
  flightNumber: string
  duration: number
  routeCoordinates: string
  productId: number
  productName: string
  productPrice: number
}

/** 航班频率统计响应 */
export interface FrequencyData {
  flights: unknown[]
  cityFrequency: CityFrequency[]
}

/** 获取航班频率统计数据（供飞线图使用） */
export function getFlightFrequency() {
  return request.get<FrequencyData>('/product/flights/frequency')
}

/** 按出发城市/落地城市筛选航班 */
export function searchFlights(params: { departureCity?: string; arrivalCity?: string }) {
  return request.get<CityFrequency[]>('/product/flights/search', { params })
}

/** 获取所有航班 */
export function listFlights() {
  return request.get<CityFrequency[]>('/product/flights')
}
