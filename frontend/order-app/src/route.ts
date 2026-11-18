/**
 * order-app 轻量路由解析。
 *
 * order-app 不是 SPA 框架应用，挂载时根据浏览器地址决定首屏：
 * - /order/pay-result?orderNo=xxx   支付宝同步回跳的支付结果页
 * - /order/detail/{orderNo}         订单详情（商品详情页「立即购买」跳转过来）
 * - 其他                            订单列表
 */
import { getPayResultOrderNo, isPayResultPath } from './payment'

export type OrderView = 'payResult' | 'detail' | 'list'

export interface OrderRoute {
  view: OrderView
  orderNo?: string
}

const DETAIL_PATTERN = /\/order\/detail\/([^/?#]+)/

export function parseOrderRoute(pathname: string, search: string): OrderRoute {
  if (isPayResultPath(pathname)) {
    const orderNo = getPayResultOrderNo(search)
    // 缺少订单号时无法查询，退回列表页，避免空白结果页
    return orderNo ? { view: 'payResult', orderNo } : { view: 'list' }
  }

  const detailMatch = DETAIL_PATTERN.exec(pathname)
  if (detailMatch) {
    return { view: 'detail', orderNo: decodeURIComponent(detailMatch[1]) }
  }

  return { view: 'list' }
}