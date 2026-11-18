import { useEffect, useRef, useState, useCallback } from 'react'
import { Card, Input, Select, Button, List, Tag, Space, Spin, message } from 'antd'
import { SearchOutlined, ShoppingCartOutlined, RocketOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import { useNavigate } from 'react-router-dom'
import { trackPageView, trackItemExpose, trackClick } from '../utils/tracker'
import { useDelayedLoading } from '../hooks/useDelayedLoading'
import { ListSkeleton, EmptyState } from '../components/PageSkeleton'

interface Product {
  id: number
  name: string
  description: string
  price: number
  stock: number
  category: string
  imageUrl: string
  status: number
  isSeckill: number
  seckillPrice: number
}

function ProductList() {
  const { t } = useTranslation()
  const [products, setProducts] = useState<Product[]>([])
  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState<string | undefined>(undefined)
  // fetching 为立即态（驱动搜索按钮与首屏判定），loadingVisible 为防闪烁后的区域遮罩
  const [fetching, setFetching] = useState(false)
  const { visible: loadingVisible, wrap: withLoading } = useDelayedLoading()
  const navigate = useNavigate()
  const listRef = useRef<HTMLDivElement>(null)
  const exposedSet = useRef<Set<number>>(new Set())
  // 请求令牌：只接受最新一次搜索结果，避免连续搜索时的竞态
  const reqIdRef = useRef(0)

  const isDark = (localStorage.getItem('qoder_theme') || 'dark') === 'dark'
  const pageBg = isDark ? '#141414' : '#f5f5f5'

  const fetchProducts = async () => {
    const reqId = ++reqIdRef.current
    setFetching(true)
    try {
      await withLoading(async () => {
        const params: any = {}
        if (keyword) params.keyword = keyword
        if (category) params.category = category

        const token = localStorage.getItem('qoder_token')
        const response = await axios.get('/api/product/products/search', {
          params,
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        })

        // 过期响应直接丢弃，避免覆盖最新搜索结果
        if (reqId !== reqIdRef.current) return
        if (response.data.code === 200) {
          setProducts(response.data.data || [])
        }
      })
    } catch (error) {
      if (reqId === reqIdRef.current) {
        message.error(t('product.fetchFailed'))
      }
    } finally {
      if (reqId === reqIdRef.current) {
        setFetching(false)
      }
    }
  }

  // ---------- 商品曝光：IntersectionObserver ----------
  const observeExposure = useCallback(() => {
    if (!listRef.current) return
    const cards = listRef.current.querySelectorAll<HTMLElement>('[data-product-id]')
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return
          const el = entry.target as HTMLElement
          const id = Number(el.dataset.productId)
          if (exposedSet.current.has(id)) return
          exposedSet.current.add(id)
          try {
            trackItemExpose(
              id,
              el.dataset.productName || '',
              el.dataset.isSeckill === '1',
            )
          } catch {
            // 埋点异常静默忽略
          }
        })
      },
      { threshold: 0.5 },
    )
    cards.forEach((card) => observer.observe(card))
    // 组件卸载时断开观察
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    fetchProducts()
  }, [])

  // 商品列表更新后重新绑定曝光观察
  useEffect(() => {
    if (!fetching && products.length > 0) {
      const cleanup = observeExposure()
      return cleanup
    }
  }, [fetching, products, observeExposure])

  // ---------- 页面访问埋点 ----------
  useEffect(() => {
    if (!fetching && !loadingVisible) {
      try {
        trackPageView('ProductList', { productCount: products.length })
      } catch {
        // 静默
      }
    }
    // 仅在加载完成时触发
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetching, loadingVisible])

  const handleSearch = () => {
    fetchProducts()
  }

  const handleDetail = (id: number, category?: string) => {
    try {
      trackClick(id, 'viewDetail')
    } catch {
      // 埋点异常不影响业务
    }
    if (category === 'flight') {
      navigate(`/flight/${id}`)
    } else {
      navigate(`/detail/${id}`)
    }
  }

  return (
    <div ref={listRef} style={{ minHeight: '100vh', padding: 24, background: pageBg }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <Card title={t('product.title')} style={{ marginBottom: 16 }}>
        <Space wrap>
          <Input
            placeholder={t('product.searchPlaceholder')}
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 250 }}
            onPressEnter={handleSearch}
          />
          <Select
            placeholder={t('product.categoryPlaceholder')}
            value={category}
            onChange={setCategory}
            style={{ width: 150 }}
            allowClear
            options={[
              { label: t('product.categoryElectronics'), value: 'electronics' },
              { label: t('product.categoryClothing'), value: 'clothing' },
              { label: t('product.categoryFood'), value: 'food' },
              { label: t('product.categoryBooks'), value: 'books' },
              { label: t('product.categoryFlight'), value: 'flight' },
            ]}
          />
          <Button type="primary" onClick={handleSearch} loading={fetching}>
            {t('product.search')}
          </Button>
        </Space>
      </Card>

      {products.length === 0 && (fetching || loadingVisible) ? (
        <ListSkeleton />
      ) : products.length === 0 ? (
        <EmptyState description={t('product.empty')} onRetry={handleSearch} />
      ) : (
        <Spin spinning={loadingVisible} tip={t('common.loading')}>
          <List
            grid={{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 4, xxl: 4 }}
            dataSource={products}
            renderItem={(item) => (
              <List.Item>
                <Card
                  hoverable
                  data-product-id={item.id}
                  data-product-name={item.name}
                  data-is-seckill={item.isSeckill}
                  cover={
                    <div
                      style={{
                        position: 'relative',
                        height: 200,
                        background: 'var(--ant-color-fill-tertiary, #f0f2f5)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        overflow: 'hidden',
                      }}
                    >
                      {item.category === 'flight'
                        ? <RocketOutlined style={{ fontSize: 48, color: 'var(--ant-color-primary, #1677ff)' }} />
                        : <ShoppingCartOutlined style={{ fontSize: 48, color: 'var(--ant-color-text-quaternary, #bfbfbf)' }} />
                      }
                      {/* 有图片时覆盖图标；加载失败自动隐藏，回退到图标兜底 */}
                      {item.imageUrl && (
                        <img
                          src={item.imageUrl}
                          alt={item.name}
                          loading="lazy"
                          style={{
                            position: 'absolute',
                            inset: 0,
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover',
                          }}
                          onError={(e) => {
                            e.currentTarget.style.display = 'none'
                          }}
                        />
                      )}
                    </div>
                  }
                  actions={[
                    <Button type="link" onClick={() => handleDetail(item.id, item.category)}>
                      {t('product.viewDetail')}
                    </Button>,
                  ]}
                >
                  <Card.Meta
                    title={
                      <Space>
                        {item.name}
                        {item.isSeckill === 1 && <Tag color="red">{t('product.seckill')}</Tag>}
                      </Space>
                    }
                    description={item.description?.substring(0, 50) + (item.description?.length > 50 ? '...' : '')}
                  />
                  <div style={{ marginTop: 12 }}>
                    <span style={{ color: 'var(--ant-color-error, #f5222d)', fontSize: 20, fontWeight: 'bold' }}>
                      ¥{item.isSeckill === 1 ? item.seckillPrice : item.price}
                    </span>
                    {item.isSeckill === 1 && (
                      <span style={{ color: 'var(--ant-color-text-quaternary, #999)', textDecoration: 'line-through', marginLeft: 8 }}>
                        ¥{item.price}
                      </span>
                    )}
                    <span style={{ float: 'right', color: 'var(--ant-color-text-tertiary, #999)' }}>
                      {t('product.stock')}: {item.stock}
                    </span>
                  </div>
                </Card>
              </List.Item>
            )}
          />
        </Spin>
      )}
      </div>
    </div>
  )
}

export default ProductList
