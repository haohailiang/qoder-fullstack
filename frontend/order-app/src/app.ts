import axios from 'axios'

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

  constructor(container: HTMLElement, props: any = {}) {
    this.container = container
    this.props = props
  }

  async render() {
    this.container.innerHTML = `
      <div style="padding: 24px; max-width: 1200px; margin: 0 auto;">
        <div style="background: #fff; border-radius: 8px; padding: 24px; margin-bottom: 16px;">
          <h2 style="margin: 0 0 16px 0; color: #333;">订单管理</h2>
          <div style="display: flex; gap: 12px; margin-bottom: 16px;">
            <button id="btn-my-orders" style="padding: 8px 16px; background: #1890ff; color: #fff; border: none; border-radius: 4px; cursor: pointer;">
              我的订单
            </button>
            <button id="btn-all-orders" style="padding: 8px 16px; background: #fff; color: #333; border: 1px solid #d9d9d9; border-radius: 4px; cursor: pointer;">
              全部订单
            </button>
          </div>
        </div>
        <div id="order-content" style="background: #fff; border-radius: 8px; padding: 24px;">
          <div style="text-align: center; color: #999; padding: 40px;">加载中...</div>
        </div>
      </div>
    `

    this.bindEvents()
    await this.loadOrders()
  }

  private bindEvents() {
    const btnMy = this.container.querySelector('#btn-my-orders') as HTMLButtonElement
    const btnAll = this.container.querySelector('#btn-all-orders') as HTMLButtonElement

    btnMy?.addEventListener('click', () => {
      this.loadOrders()
      btnMy.style.background = '#1890ff'
      btnMy.style.color = '#fff'
      if (btnAll) {
        btnAll.style.background = '#fff'
        btnAll.style.color = '#333'
      }
    })

    btnAll?.addEventListener('click', () => {
      this.loadAllOrders()
      btnAll.style.background = '#1890ff'
      btnAll.style.color = '#fff'
      if (btnMy) {
        btnMy.style.background = '#fff'
        btnMy.style.color = '#333'
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
      this.renderError('获取订单列表失败')
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
      this.renderError('获取订单列表失败')
    } finally {
      this.loading = false
    }
  }

  private renderOrderList() {
    const content = this.container.querySelector('#order-content')
    if (!content) return

    if (this.orders.length === 0) {
      content.innerHTML = '<div style="text-align: center; color: #999; padding: 40px;">暂无订单数据</div>'
      return
    }

    const statusMap: Record<number, { text: string; color: string }> = {
      0: { text: '待支付', color: '#faad14' },
      1: { text: '已支付', color: '#52c41a' },
      2: { text: '已发货', color: '#1890ff' },
      3: { text: '已完成', color: '#52c41a' },
      4: { text: '已取消', color: '#999' },
    }

    content.innerHTML = `
      <table style="width: 100%; border-collapse: collapse;">
        <thead>
          <tr style="background: #fafafa; border-bottom: 1px solid #f0f0f0;">
            <th style="padding: 12px; text-align: left;">订单号</th>
            <th style="padding: 12px; text-align: left;">商品</th>
            <th style="padding: 12px; text-align: center;">数量</th>
            <th style="padding: 12px; text-align: right;">金额</th>
            <th style="padding: 12px; text-align: center;">状态</th>
            <th style="padding: 12px; text-align: center;">操作</th>
          </tr>
        </thead>
        <tbody>
          ${this.orders.map(order => {
            const status = statusMap[order.status] || { text: '未知', color: '#999' }
            return `
              <tr style="border-bottom: 1px solid #f0f0f0;">
                <td style="padding: 12px;">${order.orderNo}</td>
                <td style="padding: 12px;">${order.productName || '-'}</td>
                <td style="padding: 12px; text-align: center;">${order.quantity}</td>
                <td style="padding: 12px; text-align: right;">¥${order.totalAmount}</td>
                <td style="padding: 12px; text-align: center;">
                  <span style="color: ${status.color}; padding: 2px 8px; background: ${status.color}15; border-radius: 4px;">
                    ${status.text}
                  </span>
                </td>
                <td style="padding: 12px; text-align: center;">
                  <button class="btn-detail" data-id="${order.id}" style="color: #1890ff; background: none; border: none; cursor: pointer;">
                    详情
                  </button>
                  ${order.status === 0 ? `
                    <button class="btn-cancel" data-id="${order.id}" style="color: #ff4d4f; background: none; border: none; cursor: pointer; margin-left: 8px;">
                      取消
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

    const statusMap: Record<number, string> = {
      0: '待支付', 1: '已支付', 2: '已发货', 3: '已完成', 4: '已取消',
    }

    content.innerHTML = `
      <div>
        <button id="btn-back" style="margin-bottom: 16px; padding: 6px 12px; background: #fff; border: 1px solid #d9d9d9; border-radius: 4px; cursor: pointer;">
          ← 返回列表
        </button>
        <h3 style="margin-bottom: 16px;">订单详情</h3>
        <div style="display: grid; grid-template-columns: 120px 1fr; gap: 12px;">
          <span style="color: #999;">订单号:</span>
          <span>${order.orderNo}</span>
          <span style="color: #999;">商品名称:</span>
          <span>${order.productName || '-'}</span>
          <span style="color: #999;">购买数量:</span>
          <span>${order.quantity}</span>
          <span style="color: #999;">订单金额:</span>
          <span style="color: #f5222d; font-weight: bold;">¥${order.totalAmount}</span>
          <span style="color: #999;">订单状态:</span>
          <span>${statusMap[order.status] || '未知'}</span>
          <span style="color: #999;">收货地址:</span>
          <span>${order.address || '-'}</span>
          <span style="color: #999;">备注:</span>
          <span>${order.remark || '-'}</span>
          <span style="color: #999;">创建时间:</span>
          <span>${order.createdDate ? new Date(order.createdDate).toLocaleString('zh-CN') : '-'}</span>
        </div>
      </div>
    `

    const btnBack = content.querySelector('#btn-back')
    btnBack?.addEventListener('click', () => this.renderOrderList())
  }

  private async cancelOrder(id: number) {
    if (!confirm('确定要取消此订单吗？')) return

    try {
      const token = this.getToken()
      const response = await axios.post(`/api/order/orders/${id}/cancel`, {}, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (response.data.code === 200) {
        alert('订单已取消')
        await this.loadOrders()
      }
    } catch (error) {
      alert('取消订单失败')
    }
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
    return localStorage.getItem('demo_token')
  }

  destroy() {
    this.container.innerHTML = ''
  }
}
