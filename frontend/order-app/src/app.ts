import axios from 'axios'
import { t } from './i18n'
import {
  createLoadingOverlay,
  ensureLoadingStyles,
  tableSkeletonHtml,
  withButtonLoading,
  type LoadingOverlay,
} from './loading'
import {
  createAlipayPayment,
  isFinalPayState,
  mapPaymentResult,
  MAX_PAY_POLLS,
  PAY_POLL_INTERVAL,
  queryPaymentStatus,
  type PaymentStatusResult,
  type PaymentView,
} from './payment'
import { parseOrderRoute } from './route'

interface Order {
  id: number
  orderNo: string
  userId: number
  productId: number
  productName: string
  quantity: number
  totalAmount: number
  status: number
  address: string
  remark: string
  createdDate: string
}

export class OrderApp {
  private container: HTMLElement
  private props: any
  private currentView: 'list' | 'detail' = 'list'
  private orders: Order[] = []
  private selectedOrder: Order | null = null
  private theme: 'dark' | 'light' = 'dark'
  // 延迟展示的加载态（首屏骨架 / 刷新遮罩）
  private overlay: LoadingOverlay | null = null
  // 请求令牌：只接受最新一次请求的结果
  private requestSeq = 0
  // 支付结果页轮询定时器与次数
  private pollTimer: number | null = null
  private pollCount = 0

  constructor(container: HTMLElement, props: any = {}) {
    this.container = container
    this.props = props
    this.theme = (localStorage.getItem('qoder_theme') as 'dark' | 'light') || 'dark'
  }

  async render() {
    // 内容节点会被整体重建，旧遮罩需同步销毁
    this.overlay?.destroy()
    this.overlay = null
    this.stopPolling()

    const route = parseOrderRoute(window.location.pathname, window.location.search)
    // 支付宝同步回跳：直接渲染支付结果页
    if (route.view === 'payResult' && route.orderNo) {
      await this.renderPayResult(route.orderNo)
      return
    }

    const isDark = this.theme === 'dark'
    const pageBg = isDark ? '#141414' : '#f5f5f5'
    const bgColor = isDark ? '#1f1f1f' : '#fff'
    const textColor = isDark ? '#e5eaf3' : '#333'
    const secondaryText = isDark ? '#a6a6a6' : '#999'
    const borderColor = isDark ? '#424242' : '#f0f0f0'
    const headerBg = isDark ? '#2a2a2a' : '#fafafa'
    const btnPrimaryBg = '#1890ff'
    const btnDefaultBg = isDark ? '#333' : '#fff'
    const btnDefaultText = isDark ? '#e5eaf3' : '#333'
    const btnDefaultBorder = isDark ? '#555' : '#d9d9d9'

    this.container.innerHTML = `
      <div style="min-height: 100vh; padding: 24px; background: ${pageBg}; --oa-primary: #409eff; --oa-overlay-bg: ${isDark ? 'rgba(20,20,20,0.72)' : 'rgba(255,255,255,0.75)'}; --oa-text-secondary: ${secondaryText}; --oa-skeleton-bg: ${isDark ? '#2a2a2a' : '#f0f2f5'}; --oa-skeleton-highlight: ${isDark ? '#3a3a3a' : '#e4e7ed'};">
        <div style="max-width: 1200px; margin: 0 auto;">
          <div style="background: ${bgColor}; border-radius: 8px; padding: 24px; margin-bottom: 16px; border: 1px solid ${borderColor}; box-shadow: 0 2px 8px ${isDark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.08)'};">
            <h2 style="margin: 0 0 16px 0; color: ${textColor};">${t('order.title')}</h2>
            <div style="display: flex; gap: 12px; margin-bottom: 0;">
              <button id="btn-my-orders" style="padding: 8px 16px; background: ${btnPrimaryBg}; color: #fff; border: none; border-radius: 4px; cursor: pointer;">
                ${t('order.myOrders')}
              </button>
              <button id="btn-all-orders" style="padding: 8px 16px; background: ${btnDefaultBg}; color: ${btnDefaultText}; border: 1px solid ${btnDefaultBorder}; border-radius: 4px; cursor: pointer;">
                ${t('order.allOrders')}
              </button>
            </div>
          </div>
          <div id="order-content" style="position: relative; min-height: 320px; background: ${bgColor}; border-radius: 8px; padding: 24px; border: 1px solid ${borderColor}; box-shadow: 0 2px 8px ${isDark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.08)'};"></div>
        </div>
      </div>
    `

    this.bindEvents()
    await this.loadOrders()

    // 商品详情页「立即购买」失败后跳转过来：定位到对应订单详情
    if (route.view === 'detail' && route.orderNo) {
      this.showOrderDetailByNo(route.orderNo)
    }
  }

  private bindEvents() {
    const isDark = this.theme === 'dark'
    const btnPrimaryBg = '#1890ff'
    const btnDefaultBg = isDark ? '#333' : '#fff'
    const btnDefaultText = isDark ? '#e5eaf3' : '#333'

    const btnMy = this.container.querySelector('#btn-my-orders') as HTMLButtonElement
    const btnAll = this.container.querySelector('#btn-all-orders') as HTMLButtonElement

    btnMy?.addEventListener('click', () => {
      btnMy.style.background = btnPrimaryBg
      btnMy.style.color = '#fff'
      if (btnAll) {
        btnAll.style.background = btnDefaultBg
        btnAll.style.color = btnDefaultText
      }
      this.loadOrders(btnMy)
    })

    btnAll?.addEventListener('click', () => {
      btnAll.style.background = btnPrimaryBg
      btnAll.style.color = '#fff'
      if (btnMy) {
        btnMy.style.background = btnDefaultBg
        btnMy.style.color = btnDefaultText
      }
      this.loadAllOrders(btnAll)
    })
  }

  private getContentEl(): HTMLElement | null {
    return this.container.querySelector<HTMLElement>('#order-content')
  }

  /** 首屏用骨架、已有数据用遮罩；两者均延迟 200ms 出现 */
  private beginLoading() {
    const content = this.getContentEl()
    if (!content) return
    if (!this.overlay) {
      this.overlay = createLoadingOverlay(content, {
        text: t('order.loading'),
        skeletonHtml: tableSkeletonHtml(5),
      })
    }
    this.overlay.show(content.dataset.loaded === '1' ? 'overlay' : 'skeleton')
  }

  private markLoaded() {
    const content = this.getContentEl()
    if (content) content.dataset.loaded = '1'
  }

  private async loadOrders(button?: HTMLButtonElement | null) {
    const seq = ++this.requestSeq
    this.beginLoading()
    withButtonLoading(button ?? null, true)
    try {
      const token = this.getToken()
      const response = await axios.get('/api/order/orders/my', {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (seq !== this.requestSeq) return
      if (response.data.code === 200) {
        this.orders = response.data.data || []
        this.renderOrderList()
      } else {
        this.renderError(response.data.message || t('order.fetchFailed'))
      }
    } catch {
      if (seq === this.requestSeq) {
        this.renderError(t('order.fetchFailed'))
      }
    } finally {
      if (seq === this.requestSeq) {
        this.overlay?.hide()
        this.markLoaded()
      }
      withButtonLoading(button ?? null, false)
    }
  }

  /** 全部订单为后端管理接口（仅 ADMIN），普通用户会得到 403，需把后端提示展示出来 */
  private async loadAllOrders(button?: HTMLButtonElement | null) {
    const seq = ++this.requestSeq
    this.beginLoading()
    withButtonLoading(button ?? null, true)
    try {
      const token = this.getToken()
      const response = await axios.get('/api/order/orders', {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (seq !== this.requestSeq) return
      if (response.data.code === 200) {
        this.orders = response.data.data || []
        this.renderOrderList()
      } else {
        this.renderError(response.data.message || t('order.fetchFailed'))
      }
    } catch {
      if (seq === this.requestSeq) {
        this.renderError(t('order.fetchFailed'))
      }
    } finally {
      if (seq === this.requestSeq) {
        this.overlay?.hide()
        this.markLoaded()
      }
      withButtonLoading(button ?? null, false)
    }
  }

  private renderOrderList() {
    const content = this.container.querySelector('#order-content')
    if (!content) return

    const isDark = this.theme === 'dark'
    const textColor = isDark ? '#e5eaf3' : '#333'
    const secondaryText = isDark ? '#a6a6a6' : '#999'
    const borderColor = isDark ? '#424242' : '#f0f0f0'
    const headerBg = isDark ? '#2a2a2a' : '#fafafa'

    if (this.orders.length === 0) {
      content.innerHTML = `<div style="text-align: center; color: ${secondaryText}; padding: 40px;">${t('order.empty')}</div>`
      return
    }

    const statusMap: Record<number, { textKey: string; color: string }> = {
      0: { textKey: 'order.statusPending', color: '#faad14' },
      1: { textKey: 'order.statusPaid', color: '#52c41a' },
      2: { textKey: 'order.statusShipped', color: '#1890ff' },
      3: { textKey: 'order.statusCompleted', color: '#52c41a' },
      4: { textKey: 'order.statusCancelled', color: '#999' },
    }

    content.innerHTML = `
      <table style="width: 100%; border-collapse: collapse; color: ${textColor};">
        <thead>
          <tr style="background: ${headerBg}; border-bottom: 1px solid ${borderColor};">
            <th style="padding: 12px; text-align: left;">${t('order.colOrderNo')}</th>
            <th style="padding: 12px; text-align: left;">${t('order.colProduct')}</th>
            <th style="padding: 12px; text-align: center;">${t('order.colQuantity')}</th>
            <th style="padding: 12px; text-align: right;">${t('order.colAmount')}</th>
            <th style="padding: 12px; text-align: center;">${t('order.colStatus')}</th>
            <th style="padding: 12px; text-align: center;">${t('order.colActions')}</th>
          </tr>
        </thead>
        <tbody>
          ${this.orders.map(order => {
            const status = statusMap[order.status] || { textKey: 'order.statusUnknown', color: '#999' }
            return `
              <tr style="border-bottom: 1px solid ${borderColor};">
                <td style="padding: 12px;">${order.orderNo}</td>
                <td style="padding: 12px;">${order.productName || '-'}</td>
                <td style="padding: 12px; text-align: center;">${order.quantity}</td>
                <td style="padding: 12px; text-align: right;">¥${order.totalAmount}</td>
                <td style="padding: 12px; text-align: center;">
                  <span style="color: ${status.color}; padding: 2px 8px; background: ${status.color}15; border-radius: 4px;">
                    ${t(status.textKey)}
                  </span>
                </td>
                <td style="padding: 12px; text-align: center;">
                  <button class="btn-detail" data-id="${order.id}" style="color: #1890ff; background: none; border: none; cursor: pointer;">
                    ${t('order.detail')}
                  </button>
                  ${order.status === 0 ? `
                    <button class="btn-pay" data-no="${order.orderNo}" style="color: #52c41a; background: none; border: none; cursor: pointer; margin-left: 8px;">
                      ${t('order.pay')}
                    </button>
                  ` : ''}
                  ${order.status === 0 ? `
                    <button class="btn-cancel" data-id="${order.id}" style="color: #ff4d4f; background: none; border: none; cursor: pointer; margin-left: 8px;">
                      ${t('order.cancel')}
                    </button>
                  ` : ''}
                </td>
              </tr>
            `
          }).join('')}
        </tbody>
      </table>
    `

    // 绑定详情按钮事件
    content.querySelectorAll('.btn-detail').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = parseInt((e.target as HTMLElement).dataset.id || '0')
        this.showOrderDetail(id)
      })
    })

    // 绑定取消按钮事件
    content.querySelectorAll('.btn-cancel').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = parseInt((e.target as HTMLElement).dataset.id || '0')
        this.cancelOrder(id, btn as HTMLButtonElement)
      })
    })

    // 绑定去支付按钮事件
    content.querySelectorAll('.btn-pay').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const orderNo = (e.target as HTMLElement).dataset.no || ''
        this.payOrder(orderNo, btn as HTMLButtonElement)
      })
    })
  }

  private async showOrderDetail(id: number) {
    const order = this.orders.find(o => o.id === id)
    if (!order) return

    const content = this.container.querySelector('#order-content')
    if (!content) return

    const isDark = this.theme === 'dark'
    const textColor = isDark ? '#e5eaf3' : '#333'
    const secondaryText = isDark ? '#a6a6a6' : '#999'
    const btnDefaultBg = isDark ? '#333' : '#fff'
    const btnDefaultBorder = isDark ? '#555' : '#d9d9d9'

    const statusMap: Record<number, string> = {
      0: t('order.statusPending'),
      1: t('order.statusPaid'),
      2: t('order.statusShipped'),
      3: t('order.statusCompleted'),
      4: t('order.statusCancelled'),
    }

    content.innerHTML = `
      <div style="color: ${textColor};">
        <button id="btn-back" style="margin-bottom: 16px; padding: 6px 12px; background: ${btnDefaultBg}; color: ${textColor}; border: 1px solid ${btnDefaultBorder}; border-radius: 4px; cursor: pointer;">
          ${t('order.backToList')}
        </button>
        ${order.status === 0 ? `
          <button id="btn-detail-pay" style="margin-bottom: 16px; margin-left: 8px; padding: 6px 12px; background: #1890ff; color: #fff; border: none; border-radius: 4px; cursor: pointer;">
            ${t('order.pay')}
          </button>
        ` : ''}
        <h3 style="margin-bottom: 16px;">${t('order.orderDetail')}</h3>
        <div style="display: grid; grid-template-columns: 120px 1fr; gap: 12px;">
          <span style="color: ${secondaryText};">${t('order.detailOrderNo')}</span>
          <span>${order.orderNo}</span>
          <span style="color: ${secondaryText};">${t('order.detailProduct')}</span>
          <span>${order.productName || '-'}</span>
          <span style="color: ${secondaryText};">${t('order.detailQuantity')}</span>
          <span>${order.quantity}</span>
          <span style="color: ${secondaryText};">${t('order.detailAmount')}</span>
          <span style="color: #f5222d; font-weight: bold;">¥${order.totalAmount}</span>
          <span style="color: ${secondaryText};">${t('order.detailStatus')}</span>
          <span>${statusMap[order.status] || t('order.statusUnknown')}</span>
          <span style="color: ${secondaryText};">${t('order.detailAddress')}</span>
          <span>${order.address || '-'}</span>
          <span style="color: ${secondaryText};">${t('order.detailRemark')}</span>
          <span>${order.remark || '-'}</span>
          <span style="color: ${secondaryText};">${t('order.detailCreated')}</span>
          <span>${order.createdDate ? new Date(order.createdDate).toLocaleString() : '-'}</span>
        </div>
      </div>
    `

    const btnBack = content.querySelector('#btn-back')
    btnBack?.addEventListener('click', () => this.renderOrderList())

    const btnPay = content.querySelector('#btn-detail-pay') as HTMLButtonElement | null
    btnPay?.addEventListener('click', () => this.payOrder(order.orderNo, btnPay))
  }

  /** 按订单号展示详情（列表尚未加载完时兜底重新拉取） */
  private showOrderDetailByNo(orderNo: string) {
    const order = this.orders.find(o => o.orderNo === orderNo)
    if (!order) {
      this.renderError(t('order.notFound'))
      return
    }
    this.showOrderDetail(order.id)
  }

  private async cancelOrder(id: number, button?: HTMLButtonElement | null) {
    if (!confirm(t('order.confirmCancel'))) return

    // 提交类操作用按钮自身 loading，不叠加区域遮罩
    withButtonLoading(button ?? null, true)
    try {
      const token = this.getToken()
      const response = await axios.post(`/api/order/orders/${id}/cancel`, {}, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (response.data.code === 200) {
        alert(t('order.cancelSuccess'))
        await this.loadOrders()
      }
    } catch {
      alert(t('order.cancelFailed'))
    } finally {
      withButtonLoading(button ?? null, false)
    }
  }

  /** 发起支付宝支付并整页跳转到收银台 */
  private async payOrder(orderNo: string, button?: HTMLButtonElement | null) {
    if (!orderNo) return
    withButtonLoading(button ?? null, true)
    try {
      const response = await createAlipayPayment(orderNo, this.getToken())
      const body = response.data
      if (body?.code === 200 && body.data?.payUrl) {
        this.navigateTo(body.data.payUrl)
        return
      }
      alert(body?.code === 503 ? t('order.payUnavailable') : (body?.message || t('order.payFailed')))
    } catch (error: any) {
      const data = error?.response?.data
      alert(data?.code === 503 ? t('order.payUnavailable') : (data?.message || t('order.payFailed')))
    } finally {
      withButtonLoading(button ?? null, false)
    }
  }

  /** 整页跳转（抽成方法便于单元测试替换，qiankun 下需整页跳转换壳） */
  protected navigateTo(url: string) {
    window.location.assign(url)
  }

  /** 支付结果页：展示订单号 + 轮询确认结果 */
  private async renderPayResult(orderNo: string) {
    ensureLoadingStyles()
    const isDark = this.theme === 'dark'
    const pageBg = isDark ? '#141414' : '#f5f5f5'
    const bgColor = isDark ? '#1f1f1f' : '#fff'
    const textColor = isDark ? '#e5eaf3' : '#333'
    const secondaryText = isDark ? '#a6a6a6' : '#999'
    const borderColor = isDark ? '#424242' : '#f0f0f0'

    this.container.innerHTML = `
      <div style="min-height: 100vh; padding: 24px; background: ${pageBg}; --oa-text-secondary: ${secondaryText};">
        <div style="max-width: 720px; margin: 0 auto;">
          <div style="background: ${bgColor}; border-radius: 8px; padding: 32px; border: 1px solid ${borderColor}; box-shadow: 0 2px 8px ${isDark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.08)'};">
            <h2 style="margin: 0 0 8px 0; color: ${textColor};">${t('order.payResult')}</h2>
            <p style="margin: 0 0 24px 0; color: ${secondaryText}; font-size: 13px;">
              ${t('order.detailOrderNo')} <span style="color: ${textColor};">${this.escapeHtml(orderNo)}</span>
            </p>
            <div id="pay-status" style="min-height: 160px; display: flex; align-items: center; justify-content: center; color: ${secondaryText};">
              <span class="oa-spinner"></span>
            </div>
          </div>
        </div>
      </div>
    `

    await this.startPayResultPolling(orderNo)
  }

  /** 首次查询后启动轮询，终态或查询失败时停止（避免无意义请求） */
  private async startPayResultPolling(orderNo: string) {
    this.stopPolling()
    this.pollCount = 0
    const shouldStop = await this.checkPayResult(orderNo)
    if (shouldStop) return

    this.pollTimer = window.setInterval(() => {
      this.pollCount += 1
      if (this.pollCount >= MAX_PAY_POLLS) {
        this.stopPolling()
        this.renderPayResultPending(orderNo)
        return
      }
      void this.checkPayResult(orderNo)
    }, PAY_POLL_INTERVAL)
  }

  /**
   * 查询一次支付状态并渲染。
   *
   * @returns 是否应停止自动轮询（终态或查询失败）
   */
  private async checkPayResult(orderNo: string): Promise<boolean> {
    try {
      const response = await queryPaymentStatus(orderNo, this.getToken(), true)
      if (response.data?.code === 200) {
        const view = mapPaymentResult(response.data.data as PaymentStatusResult)
        this.renderPayStatus(orderNo, view)
        return isFinalPayState(view)
      }
      this.renderPayResultPending(orderNo, response.data?.message)
      return true
    } catch {
      this.renderPayResultPending(orderNo)
      return true
    }
  }

  /** 查询失败或轮询超时：展示待确认状态，由用户手动刷新 */
  private renderPayResultPending(orderNo: string, extra?: string) {
    this.renderPayStatus(orderNo, {
      state: 'PENDING',
      titleKey: 'order.payPending',
      descKey: extra ? 'order.payQueryFailed' : 'order.payPendingDesc',
      canRetry: false,
    }, extra)
  }

  private renderPayStatus(orderNo: string, view: PaymentView, extra?: string) {
    const statusEl = this.container.querySelector<HTMLElement>('#pay-status')
    if (!statusEl) return

    const isDark = this.theme === 'dark'
    const textColor = isDark ? '#e5eaf3' : '#333'
    const secondaryText = isDark ? '#a6a6a6' : '#999'
    const btnDefaultBg = isDark ? '#333' : '#fff'
    const btnDefaultBorder = isDark ? '#555' : '#d9d9d9'
    const stateColor: Record<string, string> = {
      SUCCESS: '#52c41a',
      PENDING: '#faad14',
      CLOSED: '#faad14',
      CANCELLED: '#999',
      FAILED: '#ff4d4f',
    }
    const color = stateColor[view.state] || secondaryText
    const outlineBtn = `padding: 8px 16px; background: ${btnDefaultBg}; color: ${textColor}; border: 1px solid ${btnDefaultBorder}; border-radius: 4px; cursor: pointer;`
    const primaryBtn = 'padding: 8px 16px; background: #1890ff; color: #fff; border: none; border-radius: 4px; cursor: pointer;'

    statusEl.innerHTML = `
      <div style="text-align: center; width: 100%;">
        ${view.state === 'PENDING' ? '<span class="oa-spinner"></span>' : ''}
        <div style="margin-top: 12px; font-size: 18px; font-weight: 600; color: ${color};">${t(view.titleKey)}</div>
        <div style="margin-top: 8px; color: ${secondaryText};">${t(view.descKey)}</div>
        ${extra ? `<div style="margin-top: 8px; color: #ff4d4f; font-size: 13px;">${this.escapeHtml(extra)}</div>` : ''}
        <div style="margin-top: 24px; display: flex; gap: 12px; justify-content: center; flex-wrap: wrap;">
          ${view.state === 'PENDING' ? `<button id="btn-pay-refresh" style="${outlineBtn}">${t('order.payRefresh')}</button>` : ''}
          ${view.canRetry ? `<button id="btn-pay-retry" style="${primaryBtn}">${t('order.payRetry')}</button>` : ''}
          ${view.state === 'PENDING' ? `<button id="btn-pay-trouble" style="${outlineBtn}">${t('order.payTrouble')}</button>` : ''}
          <button id="btn-pay-view-order" style="${view.state === 'SUCCESS' ? primaryBtn : outlineBtn}">${t('order.payViewOrder')}</button>
          <button id="btn-pay-back" style="${outlineBtn}">${t('order.payBackToList')}</button>
        </div>
      </div>
    `

    const detailUrl = `/order/detail/${encodeURIComponent(orderNo)}`
    statusEl.querySelector('#btn-pay-refresh')?.addEventListener('click', (e) => {
      const btn = e.currentTarget as HTMLButtonElement
      withButtonLoading(btn, true)
      void this.checkPayResult(orderNo).finally(() => withButtonLoading(btn, false))
    })
    statusEl.querySelector('#btn-pay-retry')?.addEventListener('click', (e) => {
      void this.payOrder(orderNo, e.currentTarget as HTMLButtonElement)
    })
    statusEl.querySelector('#btn-pay-trouble')?.addEventListener('click', () => {
      this.navigateTo(detailUrl)
    })
    statusEl.querySelector('#btn-pay-view-order')?.addEventListener('click', () => {
      this.navigateTo(detailUrl)
    })
    statusEl.querySelector('#btn-pay-back')?.addEventListener('click', () => {
      this.navigateTo('/order/my')
    })
  }

  private stopPolling() {
    if (this.pollTimer !== null) {
      window.clearInterval(this.pollTimer)
      this.pollTimer = null
    }
  }

  private escapeHtml(value: string): string {
    const entities: Record<string, string> = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }
    return value.replace(/[&<>"']/g, (char) => entities[char])
  }

  private renderError(message: string) {
    const content = this.container.querySelector('#order-content')
    if (content) {
      content.innerHTML = `<div style="text-align: center; color: #ff4d4f; padding: 40px;">${message}</div>`
    }
  }

  private getToken(): string | null {
    // 优先从 props 获取（qiankun 传入）
    if (this.props?.getToken) {
      return this.props.getToken()
    }
    // 否则从 localStorage 获取
    return localStorage.getItem('qoder_token')
  }

  destroy() {
    this.stopPolling()
    this.overlay?.destroy()
    this.overlay = null
    this.container.innerHTML = ''
  }
}
