/**
 * OrderApp 类单元测试
 *
 * 覆盖：render / loadOrders / cancelOrder / getToken（qiankun props）
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { OrderApp } from '@/app'

// mock axios
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

// mock i18n
vi.mock('@/i18n', () => ({
  t: (key: string) => key,
  getCurrentLang: () => 'zh',
}))

describe('OrderApp', () => {
  let container: HTMLElement
  let app: OrderApp

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    container = document.createElement('div')
    document.body.appendChild(container)
    app = new OrderApp(container, {})
  })

  // ---------- render ----------
  describe('render', () => {
    it('应在容器中渲染页面标题', async () => {
      // mock axios.get 返回空列表
      vi.mocked(axios.get).mockResolvedValueOnce({
        data: { code: 200, data: [] },
      })

      await app.render()

      const h2 = container.querySelector('h2')
      expect(h2?.textContent).toBe('order.title')
    })

    it('应渲染"我的订单"和"全部订单"按钮', async () => {
      vi.mocked(axios.get).mockResolvedValueOnce({
        data: { code: 200, data: [] },
      })

      await app.render()

      const btnMy = container.querySelector('#btn-my-orders')
      const btnAll = container.querySelector('#btn-all-orders')
      expect(btnMy?.textContent?.trim()).toBe('order.myOrders')
      expect(btnAll?.textContent?.trim()).toBe('order.allOrders')
    })

    it('无订单时应显示空状态提示', async () => {
      vi.mocked(axios.get).mockResolvedValueOnce({
        data: { code: 200, data: [] },
      })

      await app.render()

      // loadOrders 是 async，等待 DOM 更新
      await vi.waitFor(() => {
        const content = container.querySelector('#order-content')
        expect(content?.textContent).toContain('order.empty')
      })
    })
  })

  // ---------- loadOrders ----------
  describe('loadOrders', () => {
    it('应调用 /api/order/orders/my 获取订单', async () => {
      vi.mocked(axios.get).mockResolvedValueOnce({
        data: { code: 200, data: [] },
      })

      await app.render()

      expect(axios.get).toHaveBeenCalledWith('/api/order/orders/my', expect.any(Object))
    })

    it('应从 props 获取 token 并设置 Authorization 头', async () => {
      const propsApp = new OrderApp(container, {
        getToken: () => 'props-token',
      })

      vi.mocked(axios.get).mockResolvedValueOnce({
        data: { code: 200, data: [] },
      })

      await propsApp.render()

      expect(axios.get).toHaveBeenCalledWith(
        '/api/order/orders/my',
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: 'Bearer props-token',
          }),
        }),
      )
    })

    it('API 失败时应渲染错误提示', async () => {
      vi.mocked(axios.get).mockRejectedValueOnce(new Error('Network Error'))

      await app.render()

      await vi.waitFor(() => {
        const content = container.querySelector('#order-content')
        expect(content?.textContent).toContain('order.fetchFailed')
      })
    })
  })

  // ---------- getToken ----------
  describe('getToken（qiankun props 优先级）', () => {
    it('props 有 getToken 时应优先使用', () => {
      const propsApp = new OrderApp(container, {
        getToken: () => 'from-props',
      })
      localStorage.setItem('qoder_token', 'from-storage')

      // 通过 render 间接验证
      // props 中的 getToken 应被调用
      expect(propsApp['getToken']()).toBe('from-props')
    })

    it('props 无 getToken 时应从 localStorage 获取', () => {
      localStorage.setItem('qoder_token', 'from-storage')
      expect(app['getToken']()).toBe('from-storage')
    })

    it('两者都无值时应返回 null', () => {
      expect(app['getToken']()).toBeNull()
    })
  })

  // ---------- destroy ----------
  describe('destroy', () => {
    it('应清空容器内容', () => {
      container.innerHTML = '<div>content</div>'
      app.destroy()
      expect(container.innerHTML).toBe('')
    })

    it('应清理支付结果页轮询定时器', () => {
      const timer = window.setInterval(() => {}, 1000)
      app['pollTimer'] = timer

      app.destroy()

      expect(app['pollTimer']).toBeNull()
    })
  })

  // ---------- 支付 ----------
  describe('支付入口', () => {
    const pendingOrder = {
      id: 1,
      orderNo: 'ORD-1',
      userId: 1,
      productId: 10,
      productName: '测试商品',
      quantity: 1,
      totalAmount: 99.99,
      status: 0,
      address: '北京市朝阳区',
      remark: '',
      createdDate: '2025-01-01T12:00:00',
    }

    it('待支付订单应渲染「去支付」按钮', async () => {
      vi.mocked(axios.get).mockResolvedValueOnce({
        data: { code: 200, data: [pendingOrder] },
      })

      await app.render()

      await vi.waitFor(() => {
        const btn = container.querySelector('.btn-pay') as HTMLButtonElement | null
        expect(btn?.textContent?.trim()).toBe('order.pay')
      })
    })

    it('已支付订单不应渲染「去支付」按钮', async () => {
      vi.mocked(axios.get).mockResolvedValueOnce({
        data: { code: 200, data: [{ ...pendingOrder, status: 1 }] },
      })

      await app.render()

      await vi.waitFor(() => {
        expect(container.querySelector('#order-content')?.textContent).toContain('order.statusPaid')
      })
      expect(container.querySelector('.btn-pay')).toBeNull()
    })

    it('创建支付成功时应整页跳转到收银台', async () => {
      // jsdom 的 Location.assign 不可重新定义，改为替换组件内的跳转方法
      const navSpy = vi.spyOn(app as any, 'navigateTo').mockImplementation(() => {})

      vi.mocked(axios.post).mockResolvedValueOnce({
        data: { code: 200, data: { payUrl: 'https://openapi-sandbox.dl.alipaydev.com/gateway.do?x=1' } },
      })

      await app['payOrder']('ORD-1')

      expect(navSpy).toHaveBeenCalledWith('https://openapi-sandbox.dl.alipaydev.com/gateway.do?x=1')
    })

    it('未配置支付宝（503）时应提示且不跳转', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
      vi.mocked(axios.post).mockResolvedValueOnce({
        data: { code: 503, message: '支付宝支付未配置，请联系管理员' },
      })

      await app['payOrder']('ORD-1')

      expect(alertSpy).toHaveBeenCalledWith('order.payUnavailable')
    })

    it('支付结果页：命中终态后应停止轮询并渲染成功文案', async () => {
      window.history.pushState({}, '', '/order/pay-result?orderNo=ORD-1')
      vi.mocked(axios.get).mockResolvedValue({
        data: { code: 200, data: { orderNo: 'ORD-1', orderStatus: 1, payStatus: 'PAID' } },
      })

      await app.render()

      await vi.waitFor(() => {
        expect(container.querySelector('#pay-status')?.textContent).toContain('order.paySuccess')
      })
      expect(app['pollTimer']).toBeNull()

      window.history.pushState({}, '', '/')
    })
  })

  // ---------- 主题适配 ----------
  describe('主题适配', () => {
    it('默认应使用 dark 主题', () => {
      expect(app['theme']).toBe('dark')
    })

    it('应从 localStorage 读取主题', () => {
      localStorage.setItem('qoder_theme', 'light')
      const lightApp = new OrderApp(container, {})
      expect(lightApp['theme']).toBe('light')
    })
  })
})
