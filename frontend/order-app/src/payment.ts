/**
 * 支付宝支付相关的前端逻辑（纯函数 + axios 调用）。
 *
 * 拆出独立模块便于单元测试：路由解析、结果映射都是纯函数，
 * 只有 createAlipayPayment / queryPaymentStatus 依赖 axios。
 */
import axios from 'axios'

/** 后端返回的支付状态 */
export type PayStatus = 'PAID' | 'PENDING' | 'CLOSED' | 'CANCELLED' | 'FAILED'

/** 支付结果页展示状态 */
export type PayViewState = 'SUCCESS' | 'PENDING' | 'CLOSED' | 'CANCELLED' | 'FAILED'

export interface PaymentCreateResult {
  orderNo: string
  channel: string
  amount: number
  payUrl: string
  timeoutExpress: string
}

export interface PaymentStatusResult {
  orderNo: string
  orderStatus: number
  payStatus: PayStatus
  tradeNo?: string | null
  amount?: number
  paidTime?: string | null
  message?: string
}

export interface PaymentView {
  state: PayViewState
  titleKey: string
  descKey: string
  /** 订单仍可支付时提供「重新支付」入口 */
  canRetry: boolean
}

/** 轮询间隔与最大次数：约 20 秒后停止自动轮询，转为手动刷新 */
export const PAY_POLL_INTERVAL = 2000
export const MAX_PAY_POLLS = 10

const PAY_RESULT_SUFFIX = '/pay-result'

/** 是否处于支付结果页 */
export function isPayResultPath(pathname: string): boolean {
  return stripTrailingSlash(pathname).endsWith(PAY_RESULT_SUFFIX)
}

/**
 * 从查询串解析订单号。
 * 优先使用本站追加的 orderNo，兼容支付宝同步回跳携带的 out_trade_no。
 */
export function getPayResultOrderNo(search: string): string {
  const params = new URLSearchParams(search.startsWith('?') ? search : `?${search}`)
  return params.get('orderNo') || params.get('out_trade_no') || ''
}

/** 将后端支付状态映射为结果页展示模型 */
export function mapPaymentResult(result: PaymentStatusResult | null | undefined): PaymentView {
  if (!result) {
    return {
      state: 'FAILED',
      titleKey: 'order.payFailed',
      descKey: 'order.payQueryFailed',
      canRetry: true,
    }
  }

  const paid = result.payStatus === 'PAID' || [1, 2, 3].includes(result.orderStatus)
  if (paid) {
    return {
      state: 'SUCCESS',
      titleKey: 'order.paySuccess',
      descKey: 'order.paySuccessDesc',
      canRetry: false,
    }
  }

  if (result.payStatus === 'CANCELLED' || result.orderStatus === 4) {
    return {
      state: 'CANCELLED',
      titleKey: 'order.payCancelled',
      descKey: 'order.payCancelledDesc',
      canRetry: false,
    }
  }

  if (result.payStatus === 'CLOSED') {
    return {
      state: 'CLOSED',
      titleKey: 'order.payTimeout',
      descKey: 'order.payTimeoutDesc',
      canRetry: true,
    }
  }

  if (result.payStatus === 'FAILED') {
    return {
      state: 'FAILED',
      titleKey: 'order.payFailed',
      descKey: 'order.payFailedDesc',
      canRetry: true,
    }
  }

  return {
    state: 'PENDING',
    titleKey: 'order.payPending',
    descKey: 'order.payPendingDesc',
    canRetry: false,
  }
}

/** 是否已到终态，可用于停止轮询 */
export function isFinalPayState(view: PaymentView): boolean {
  return view.state !== 'PENDING'
}

/** 发起支付宝支付，返回收银台地址 */
export function createAlipayPayment(orderNo: string, token: string | null) {
  return axios.post(
    `/api/order/payments/alipay/${encodeURIComponent(orderNo)}`,
    {},
    { headers: token ? { Authorization: `Bearer ${token}` } : {} },
  )
}

/** 查询支付状态（sync=true 时后端会主动向支付宝确认） */
export function queryPaymentStatus(orderNo: string, token: string | null, sync = true) {
  return axios.get(`/api/order/payments/alipay/${encodeURIComponent(orderNo)}/status`, {
    params: { sync },
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
}

function stripTrailingSlash(pathname: string): string {
  if (pathname.length > 1 && pathname.endsWith('/')) {
    return pathname.slice(0, -1)
  }
  return pathname
}