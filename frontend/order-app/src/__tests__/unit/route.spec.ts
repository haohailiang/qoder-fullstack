/**
 * order-app 轻量路由解析单元测试
 */
import { describe, it, expect } from 'vitest'
import { parseOrderRoute } from '@/route'

describe('parseOrderRoute', () => {
  it('支付结果页：解析出 orderNo', () => {
    expect(parseOrderRoute('/order/pay-result', '?orderNo=ORD-1')).toEqual({
      view: 'payResult',
      orderNo: 'ORD-1',
    })
  })

  it('支付结果页：兼容支付宝回传的 out_trade_no', () => {
    expect(parseOrderRoute('/order/pay-result', '?out_trade_no=ORD-2')).toEqual({
      view: 'payResult',
      orderNo: 'ORD-2',
    })
  })

  it('支付结果页缺少订单号：退回列表页', () => {
    expect(parseOrderRoute('/order/pay-result', '')).toEqual({ view: 'list' })
  })

  it('订单详情页：解析出 orderNo', () => {
    expect(parseOrderRoute('/order/detail/ORD-3', '')).toEqual({
      view: 'detail',
      orderNo: 'ORD-3',
    })
  })

  it('订单详情页：对编码后的订单号解码', () => {
    expect(parseOrderRoute('/order/detail/ORD%2F4', '')).toEqual({
      view: 'detail',
      orderNo: 'ORD/4',
    })
  })

  it('其他路径：渲染订单列表', () => {
    expect(parseOrderRoute('/order/my', '')).toEqual({ view: 'list' })
    expect(parseOrderRoute('/', '')).toEqual({ view: 'list' })
  })
})