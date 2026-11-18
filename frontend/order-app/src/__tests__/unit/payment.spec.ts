/**
 * 支付逻辑单元测试
 *
 * 覆盖：结果页路径识别、订单号解析（orderNo / out_trade_no）、后端状态到页面展示状态的映射。
 */
import { describe, it, expect } from 'vitest'
import {
  getPayResultOrderNo,
  isFinalPayState,
  isPayResultPath,
  mapPaymentResult,
  MAX_PAY_POLLS,
  PAY_POLL_INTERVAL,
  type PaymentStatusResult,
} from '@/payment'

function status(overrides: Partial<PaymentStatusResult>): PaymentStatusResult {
  return {
    orderNo: 'ORD20250101120000ABCDEF',
    orderStatus: 0,
    payStatus: 'PENDING',
    ...overrides,
  }
}

describe('isPayResultPath', () => {
  it('应识别支付结果页（含子应用前缀）', () => {
    expect(isPayResultPath('/order/pay-result')).toBe(true)
    expect(isPayResultPath('/pay-result')).toBe(true)
  })

  it('应忽略结尾斜杠', () => {
    expect(isPayResultPath('/order/pay-result/')).toBe(true)
  })

  it('其他路径应返回 false', () => {
    expect(isPayResultPath('/order/my')).toBe(false)
    expect(isPayResultPath('/order/detail/ORD-1')).toBe(false)
    expect(isPayResultPath('/')).toBe(false)
  })
})

describe('getPayResultOrderNo', () => {
  it('应优先解析本站追加的 orderNo', () => {
    expect(getPayResultOrderNo('?orderNo=ORD-1&out_trade_no=ORD-2')).toBe('ORD-1')
  })

  it('应兼容支付宝回传的 out_trade_no', () => {
    expect(getPayResultOrderNo('?out_trade_no=ORD-2&trade_no=T1')).toBe('ORD-2')
  })

  it('缺少查询串时应返回空字符串', () => {
    expect(getPayResultOrderNo('')).toBe('')
    expect(getPayResultOrderNo('?foo=bar')).toBe('')
  })
})

describe('mapPaymentResult', () => {
  it('已支付：payStatus=PAID 映射为成功，不提供重试', () => {
    const view = mapPaymentResult(status({ payStatus: 'PAID', orderStatus: 1 }))

    expect(view.state).toBe('SUCCESS')
    expect(view.titleKey).toBe('order.paySuccess')
    expect(view.canRetry).toBe(false)
  })

  it('订单状态已支付但支付状态滞后：仍映射为成功', () => {
    expect(mapPaymentResult(status({ payStatus: 'PENDING', orderStatus: 2 })).state).toBe('SUCCESS')
  })

  it('已取消：映射为取消，不提供重试', () => {
    const view = mapPaymentResult(status({ payStatus: 'CANCELLED', orderStatus: 4 }))

    expect(view.state).toBe('CANCELLED')
    expect(view.titleKey).toBe('order.payCancelled')
    expect(view.canRetry).toBe(false)
  })

  it('交易关闭：映射为超时，可重新支付', () => {
    const view = mapPaymentResult(status({ payStatus: 'CLOSED' }))

    expect(view.state).toBe('CLOSED')
    expect(view.titleKey).toBe('order.payTimeout')
    expect(view.canRetry).toBe(true)
  })

  it('支付失败：映射为失败，可重新支付', () => {
    const view = mapPaymentResult(status({ payStatus: 'FAILED' }))

    expect(view.state).toBe('FAILED')
    expect(view.titleKey).toBe('order.payFailed')
    expect(view.canRetry).toBe(true)
  })

  it('待确认：映射为待确认，不提供重试', () => {
    const view = mapPaymentResult(status({ payStatus: 'PENDING' }))

    expect(view.state).toBe('PENDING')
    expect(view.titleKey).toBe('order.payPending')
    expect(view.canRetry).toBe(false)
  })

  it('接口无数据：按失败处理并允许重试', () => {
    const view = mapPaymentResult(null)

    expect(view.state).toBe('FAILED')
    expect(view.descKey).toBe('order.payQueryFailed')
    expect(view.canRetry).toBe(true)
  })
})

describe('isFinalPayState', () => {
  it('待确认不是终态，应继续轮询', () => {
    expect(isFinalPayState(mapPaymentResult(status({ payStatus: 'PENDING' })))).toBe(false)
  })

  it('成功/取消/关闭/失败都是终态，应停止轮询', () => {
    expect(isFinalPayState(mapPaymentResult(status({ payStatus: 'PAID' })))).toBe(true)
    expect(isFinalPayState(mapPaymentResult(status({ payStatus: 'CANCELLED' })))).toBe(true)
    expect(isFinalPayState(mapPaymentResult(status({ payStatus: 'CLOSED' })))).toBe(true)
    expect(isFinalPayState(mapPaymentResult(status({ payStatus: 'FAILED' })))).toBe(true)
  })
})

describe('轮询参数', () => {
  it('轮询间隔 2 秒、最多 10 次（约 20 秒后转手动刷新）', () => {
    expect(PAY_POLL_INTERVAL).toBe(2000)
    expect(MAX_PAY_POLLS).toBe(10)
  })
})