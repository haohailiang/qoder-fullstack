import { useEffect, useState } from 'react'
import { Card, Button, Tag, Space, InputNumber, message, Divider } from 'antd'
import { ShoppingCartOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import { useParams, useNavigate } from 'react-router-dom'
import { useDelayedLoading } from '../hooks/useDelayedLoading'
import { DetailSkeleton, EmptyState } from '../components/PageSkeleton'

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
  seckillStartTime: string
  seckillEndTime: string
}

function ProductDetail() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [product, setProduct] = useState<Product | null>(null)
  const [quantity, setQuantity] = useState(1)
  // 提交按钮自身的 loading（立即态），与页面级骨架互不影响
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(false)
  const { visible: loadingVisible, wrap: withLoading } = useDelayedLoading()

  const isDark = (localStorage.getItem('qoder_theme') || 'dark') === 'dark'
  const pageBg = isDark ? '#141414' : '#f5f5f5'

  useEffect(() => {
    fetchProduct()
  }, [id])

  const fetchProduct = async () => {
    setError(false)
    try {
      await withLoading(async () => {
        const token = localStorage.getItem('qoder_token')
        const response = await axios.get(`/api/product/products/${id}`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        })
        if (response.data.code === 200) {
          setProduct(response.data.data)
        }
      })
    } catch (error) {
      // 失败后退出骨架态，避免无限加载
      setError(true)
      message.error(t('product.detailFailed'))
    }
  }

  const handleBuy = async () => {
    // 防重复提交：loading 期间忽略重复点击
    if (!product || submitting) return
    setSubmitting(true)
    try {
      const token = localStorage.getItem('qoder_token')
      const response = await axios.post(
        '/api/order/orders',
        {
          productId: product.id,
          productName: product.name,
          quantity,
          totalAmount: product.price * quantity,
        },
        {
          headers: { Authorization: `Bearer ${token}` },
        }
      )
      if (response.data.code !== 200) {
        message.error(response.data.message || t('product.buyFailed'))
        return
      }

      const orderNo: string = response.data.data?.orderNo
      if (!orderNo) {
        message.error(t('product.buyFailed'))
        return
      }

      // 订单创建成功后再发起支付宝支付，拿到收银台地址后整页跳转
      message.loading({ content: t('product.payCreating'), key: 'alipay-pay', duration: 0 })
      const payResponse = await axios.post(
        `/api/order/payments/alipay/${orderNo}`,
        {},
        {
          headers: { Authorization: `Bearer ${token}` },
        }
      )
      const payResult = payResponse.data
      if (payResult.code === 200 && payResult.data?.payUrl) {
        message.loading({ content: t('product.redirectingToAlipay'), key: 'alipay-pay', duration: 0 })
        window.location.assign(payResult.data.payUrl)
        return
      }

      // 支付未配置（503）或下单失败：订单已生成，引导到订单详情页再次发起支付
      message.destroy('alipay-pay')
      if (payResult.code === 503) {
        message.warning(t('product.payUnavailable'))
      } else {
        message.error(payResult.message || t('product.payFailed'))
      }
      window.location.assign(`/order/detail/${orderNo}`)
    } catch (error: any) {
      message.destroy('alipay-pay')
      const data = error?.response?.data
      if (data?.code === 503) {
        message.warning(t('product.payUnavailable'))
      } else {
        message.error(data?.message || t('product.buyFailed'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  const handleSeckill = async () => {
    if (!product) return
    setSubmitting(true)
    try {
      const token = localStorage.getItem('qoder_token')
      const response = await axios.post(
        `/api/product/products/seckill/${product.id}?quantity=${quantity}`,
        {},
        {
          headers: { Authorization: `Bearer ${token}` },
        }
      )
      if (response.data.code === 200) {
        const result = response.data.data
        message.success(result.message || t('product.seckillSuccess'))
        navigate(`/order/detail/${result.orderNo}`)
      }
    } catch (error: any) {
      message.error(error?.response?.data?.message || t('product.seckillFailed'))
    } finally {
      setSubmitting(false)
    }
  }

  // 首屏：骨架屏（延迟展示）或失败兜底，两者互斥，避免白屏与无限骨架
  if (!product) {
    return (
      <div style={{ minHeight: '100vh', padding: 24, background: pageBg }}>
        <div style={{ maxWidth: 1000, margin: '0 auto' }}>
          {error ? (
            <EmptyState
              description={t('product.detailFailed')}
              onRetry={fetchProduct}
              onBack={() => navigate(-1)}
            />
          ) : (
            loadingVisible && <DetailSkeleton />
          )}
        </div>
      </div>
    )
  }

  const isSeckillActive =
    product.isSeckill === 1 &&
    product.seckillStartTime &&
    product.seckillEndTime &&
    new Date() >= new Date(product.seckillStartTime) &&
    new Date() <= new Date(product.seckillEndTime)

  return (
    <div style={{ minHeight: '100vh', padding: 24, background: pageBg }}>
      <div style={{ maxWidth: 1000, margin: '0 auto' }}>
        <Card>
        <div style={{ display: 'flex', gap: 24 }}>
          <div
            style={{
              position: 'relative',
              width: 400,
              height: 400,
              background: 'var(--ant-color-fill-tertiary, #f0f2f5)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 8,
              flexShrink: 0,
              overflow: 'hidden',
            }}
          >
            <ShoppingCartOutlined style={{ fontSize: 80, color: 'var(--ant-color-text-quaternary, #bfbfbf)' }} />
            {/* 有图片时覆盖图标；加载失败自动隐藏，回退到图标兜底 */}
            {product.imageUrl && (
              <img
                src={product.imageUrl}
                alt={product.name}
                style={{
                  position: 'absolute',
                  inset: 0,
                  width: '100%',
                  height: '100%',
                  objectFit: 'contain',
                }}
                onError={(e) => {
                  e.currentTarget.style.display = 'none'
                }}
              />
            )}
          </div>
          <div style={{ flex: 1 }}>
            <h1 style={{ fontSize: 24, marginBottom: 12 }}>
              {product.name}
              {product.isSeckill === 1 && <Tag color="red" style={{ marginLeft: 8 }}>{t('product.seckill')}</Tag>}
            </h1>
            <p style={{ color: 'var(--ant-color-text-secondary, #666)', marginBottom: 16 }}>{product.description}</p>
            <Divider />
            <div style={{ marginBottom: 16 }}>
              <span style={{ color: 'var(--ant-color-text-tertiary, #999)', marginRight: 8 }}>{t('product.price')}:</span>
              {isSeckillActive ? (
                <Space>
                  <span style={{ color: 'var(--ant-color-error, #f5222d)', fontSize: 28, fontWeight: 'bold' }}>
                    ¥{product.seckillPrice}
                  </span>
                  <span style={{ color: 'var(--ant-color-text-quaternary, #999)', textDecoration: 'line-through' }}>
                    ¥{product.price}
                  </span>
                </Space>
              ) : (
                <span style={{ color: 'var(--ant-color-error, #f5222d)', fontSize: 28, fontWeight: 'bold' }}>
                  ¥{product.price}
                </span>
              )}
            </div>
            <div style={{ marginBottom: 16 }}>
              <span style={{ color: 'var(--ant-color-text-tertiary, #999)', marginRight: 8 }}>{t('product.stock')}:</span>
              <span>{product.stock}</span>
            </div>
            <div style={{ marginBottom: 16 }}>
              <span style={{ color: 'var(--ant-color-text-tertiary, #999)', marginRight: 8 }}>{t('product.category')}:</span>
              <Tag>{product.category || t('product.uncategorized')}</Tag>
            </div>
            <Divider />
            <Space>
              <span>{t('product.quantity')}:</span>
              <InputNumber min={1} max={product.stock} value={quantity} onChange={(v) => setQuantity(v || 1)} />
            </Space>
            <div style={{ marginTop: 24 }}>
              {isSeckillActive ? (
                <Button
                  type="primary"
                  danger
                  size="large"
                  icon={<ThunderboltOutlined />}
                  onClick={handleSeckill}
                  loading={submitting}
                  disabled={submitting}
                >
                  {t('product.seckillNow')}
                </Button>
              ) : (
                <Button
                  type="primary"
                  size="large"
                  icon={<ShoppingCartOutlined />}
                  onClick={handleBuy}
                  loading={submitting}
                  disabled={submitting}
                >
                  {t('product.buyNow')}
                </Button>
              )}
              <Button size="large" onClick={() => navigate(-1)} style={{ marginLeft: 12 }}>
                {t('product.back')}
              </Button>
            </div>
          </div>
        </div>
      </Card>
      </div>
    </div>
  )
}

export default ProductDetail
