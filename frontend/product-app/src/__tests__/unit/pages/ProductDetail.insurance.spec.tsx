/**
 * ProductDetail 保险分支测试
 *
 * 覆盖：
 * - category='insurance' 时展示保险 Tag、保障说明与「保险咨询机器人」入口
 * - 点击入口打开抽屉（会话列表为空时不报错）
 * - 非保险商品不渲染机器人入口
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import axios from 'axios'

// 保持 t 引用稳定：每次渲染返回新函数会触发依赖 t 的 effect 反复执行
const { tMock } = vi.hoisted(() => ({ tMock: (key: string) => key }))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<Record<string, unknown>>('react-router-dom')
  return {
    ...actual,
    useParams: () => ({ id: '5' }),
    useNavigate: () => vi.fn(),
  }
})

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: tMock }),
}))

vi.mock('axios')
const mockedGet = vi.mocked(axios.get)

import ProductDetail from '@/pages/ProductDetail'

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

const insuranceProduct = {
  id: 5,
  name: '安心百万医疗险',
  description: '住院医疗费用报销',
  price: 299,
  stock: 999,
  category: 'insurance',
  imageUrl: '',
  status: 1,
  isSeckill: 0,
  seckillPrice: 0,
  seckillStartTime: '',
  seckillEndTime: '',
}

const normalProduct = { ...insuranceProduct, id: 6, name: '普通商品', category: 'electronics' }

const originalFetch = global.fetch

describe('ProductDetail - 保险咨询机器人入口', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    cleanup()
  })

  afterEach(() => {
    global.fetch = originalFetch
  })

  it('保险商品应展示保险标识与机器人入口', async () => {
    mockedGet.mockResolvedValue({ data: { code: 200, data: insuranceProduct } } as never)

    render(<ProductDetail />)

    await waitFor(() => {
      expect(screen.getByText('product.openBot')).toBeInTheDocument()
    })
    expect(screen.getByText('product.insuranceDesc')).toBeInTheDocument()
    expect(screen.getAllByText('product.insuranceTag').length).toBeGreaterThan(0)
  })

  it('非保险商品不应展示机器人入口', async () => {
    mockedGet.mockResolvedValue({ data: { code: 200, data: normalProduct } } as never)

    render(<ProductDetail />)

    await waitFor(() => {
      expect(screen.getByText('普通商品')).toBeInTheDocument()
    })
    expect(screen.queryByText('product.openBot')).toBeNull()
  })

  it('点击入口应打开咨询抽屉', async () => {
    mockedGet.mockResolvedValue({ data: { code: 200, data: insuranceProduct } } as never)
    // 抽屉打开后会拉取会话列表，返回空列表即可
    global.fetch = vi.fn(async () =>
      new Response(JSON.stringify([]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as unknown as typeof fetch

    render(<ProductDetail />)

    await waitFor(() => {
      expect(screen.getByText('product.openBot')).toBeInTheDocument()
    })
    fireEvent.click(screen.getByText('product.openBot'))

    await waitFor(() => {
      expect(screen.getByText(/insurance\.drawerTitle/)).toBeInTheDocument()
    })
    expect(screen.getByText('insurance.scopeHint')).toBeInTheDocument()
    expect(screen.getByText('insurance.tabChat')).toBeInTheDocument()
  })
})