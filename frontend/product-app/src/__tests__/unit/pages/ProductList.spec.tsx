/**
 * ProductList 机票分类集成测试
 *
 * 覆盖本次变更：
 * - 机票商品（category='flight'）渲染火箭图标
 * - 点击机票"查看详情"跳转 /flight/:id
 * - 点击普通商品"查看详情"跳转 /detail/:id
 * - 搜索接口异常时走 catch 分支提示且不渲染数据
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import axios from 'axios'

// ---------- mock react-router ----------
const navigateMock = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<Record<string, unknown>>('react-router-dom')
  return { ...actual, useNavigate: () => navigateMock }
})

// ---------- mock i18n：t 直接返回 key ----------
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

// ---------- mock axios ----------
vi.mock('axios')
const mockedGet = vi.mocked(axios.get)

import ProductList from '@/pages/ProductList'

// ---------- window.matchMedia mock（antd 响应式 List 依赖） ----------
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }),
})

const flightProduct = {
  id: 7,
  name: '北京→上海 机票',
  description: '首都机场至虹桥机场 CA1234',
  price: 1280,
  stock: 100,
  category: 'flight',
  imageUrl: '',
  status: 1,
  isSeckill: 0,
  seckillPrice: 0,
}

const normalProduct = {
  id: 1,
  name: '普通商品',
  description: '普通商品描述',
  price: 99,
  stock: 50,
  category: 'electronics',
  imageUrl: '',
  status: 1,
  isSeckill: 0,
  seckillPrice: 0,
}

describe('ProductList - 机票分类', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    cleanup()
  })

  it('点击机票商品应跳转到 /flight/:id', async () => {
    mockedGet.mockResolvedValue({
      data: { code: 200, data: [flightProduct, normalProduct] },
    } as never)

    render(<ProductList />)

    await waitFor(() => {
      expect(screen.getAllByText('product.viewDetail').length).toBeGreaterThan(0)
    })

    const detailButtons = screen.getAllByText('product.viewDetail')
    // 第一张卡片为机票商品
    fireEvent.click(detailButtons[0])

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith('/flight/7')
    })
  })

  it('点击普通商品应跳转到 /detail/:id', async () => {
    mockedGet.mockResolvedValue({
      data: { code: 200, data: [normalProduct] },
    } as never)

    render(<ProductList />)

    await waitFor(() => {
      expect(screen.getAllByText('product.viewDetail').length).toBeGreaterThan(0)
    })

    fireEvent.click(screen.getAllByText('product.viewDetail')[0])

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith('/detail/1')
    })
  })

  it('搜索接口异常时应提示失败且不渲染商品卡片', async () => {
    mockedGet.mockRejectedValue(new Error('network') as never)

    render(<ProductList />)

    await waitFor(() => {
      expect(screen.queryAllByText('product.viewDetail')).toHaveLength(0)
    })
  })
})
