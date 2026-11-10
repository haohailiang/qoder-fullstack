/**
 * 航班 API 模块单元测试
 *
 * 覆盖：flight.ts 各请求函数使用的接口路径与查询参数是否正确，
 * 确保前端调用与后端 /product/flights 系列接口契约一致。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// ---------- mock request 封装 ----------
const getMock = vi.fn()
vi.mock('@/api/request', () => ({
  default: {
    get: (...args: unknown[]) => getMock(...args),
  },
}))

import { getFlightFrequency, searchFlights, listFlights } from '@/api/flight'

describe('api/flight', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getMock.mockResolvedValue({})
  })

  it('getFlightFrequency 应请求 /product/flights/frequency', async () => {
    await getFlightFrequency()

    expect(getMock).toHaveBeenCalledTimes(1)
    expect(getMock.mock.calls[0][0]).toBe('/product/flights/frequency')
  })

  it('listFlights 应请求 /product/flights', async () => {
    await listFlights()

    expect(getMock).toHaveBeenCalledTimes(1)
    expect(getMock.mock.calls[0][0]).toBe('/product/flights')
  })

  it('searchFlights 应将城市筛选条件作为 params 透传', async () => {
    await searchFlights({ departureCity: '北京', arrivalCity: '上海' })

    expect(getMock).toHaveBeenCalledWith('/product/flights/search', {
      params: { departureCity: '北京', arrivalCity: '上海' },
    })
  })

  it('searchFlights 传入空对象时应保留空 params 结构', async () => {
    await searchFlights({})

    expect(getMock).toHaveBeenCalledWith('/product/flights/search', { params: {} })
  })

  it('接口异常时应将 Promise 向上传递（由调用方处理）', async () => {
    getMock.mockRejectedValueOnce(new Error('network down'))

    await expect(listFlights()).rejects.toThrow('network down')
  })
})
