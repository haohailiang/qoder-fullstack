import axios from 'axios'
import { t } from './i18n'

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
  private loading = false
  private theme: 'dark' | 'light' = 'dark'

  constructor(container: HTMLElement, props: any = {}) {
    this.container = container
    this.props = props
    this.theme = (localStorage.getItem('demo_theme') as 'dark' | 'light') || 'dark'
  }

  async render() {
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
      <div style="min-height: 100vh; padding: 24px; background: ${pageBg};">
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
          <div id="order-content" style="background: ${bgColor}; border-radius: 8px; padding: 24px; border: 1px solid ${borderColor}; box-shadow: 0 2px 8px ${isDark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.08)'};">
            <div style="text-align: center; color: ${secondaryText}; padding: 40px;">${t('order.loading')}</div>
          </div>
        </div>
      </div>
    `

    this.bindEvents()
    await this.loadOrders()
  }

  private bindEvents() {
    const isDark = this.theme === 'dark'
    const btnPrimaryBg = '#1890ff'
    const btnDefaultBg = isDark ? '#333' : '#fff'
    const btnDefaultText = isDark ? '#e5eaf3' : '#333'

    const btnMy = this.container.querySelector('#btn-my-orders') as HTMLButtonElement
    const btnAll = this.container.querySelector('#btn-all-orders') as HTMLButtonElement

    btnMy?.addEventListener('click', () => {
      this.loadOrders()
      btnMy.style.background = btnPrimaryBg
      btnMy.style.color = '#fff'
      if (btnAll) {
        btnAll.style.background = btnDefaultBg
        btnAll.style.color = btnDefaultText
      }
    })

    btnAll?.addEventListener('click', () => {
      this.loadAllOrders()
      btnAll.style.background = btnPrimaryBg
      btnAll.style.color = '#fff'
      if (btnMy) {
        btnMy.style.background = btnDefaultBg
        btnMy.style.color = btnDefaultText
      }
    })
  }

  private async loadOrders() {
    this.loading = true
    try {
      const token = this.getToken()
      const response = await axios.get('/api/order/orders/my', {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (response.data.code === 200) {
        this.orders = response.data.data || []
        this.renderOrderList()
      }
    } catch (error) {
      this.renderError(t('order.fetchFailed'))
    } finally {
      this.loading = false
    }
  }

  private async loadAllOrders() {
    this.loading = true
    try {
      const token = this.getToken()
      const response = await axios.get('/api/order/orders', {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (response.data.code === 200) {
        this.orders = response.data.data || []
        this.renderOrderList()
      }
    } catch (error) {
      this.renderError(t('order.fetchFailed'))
    } finally {
      this.loading = false
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
        this.cancelOrder(id)
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
  }

  private async cancelOrder(id: number) {
    if (!confirm(t('order.confirmCancel'))) return

    try {
      const token = this.getToken()
      const response = await axios.post(`/api/order/orders/${id}/cancel`, {}, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (response.data.code === 200) {
        alert(t('order.cancelSuccess'))
        await this.loadOrders()
      }
    } catch (error) {
      alert(t('order.cancelFailed'))
    }
  }

  private renderError(message: string) {
    const content = this.container.querySelector('#order-content')
    if (content) {
      const isDark = this.theme === 'dark'
      content.innerHTML = `<div style="text-align: center; color: #ff4d4f; padding: 40px;">${message}</div>`
    }
  }

  private getToken(): string | null {
    // 优先从 props 获取（qiankun 传入）
    if (this.props?.getToken) {
      return this.props.getToken()
    }
    // 否则从 localStorage 获取
    return localStorage.getItem('demo_token')
  }

  destroy() {
    this.container.innerHTML = ''
  }
}
