import { useEffect, useState } from 'react'
import { Card, Button, Tag, Space, InputNumber, message, Divider } from 'antd'
import { ShoppingCartOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import { useParams, useNavigate } from 'react-router-dom'

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
  const [loading, setLoading] = useState(false)

  const isDark = (localStorage.getItem('demo_theme') || 'dark') === 'dark'
  const pageBg = isDark ? '#141414' : '#f5f5f5'

  useEffect(() => {
    fetchProduct()
  }, [id])

  const fetchProduct = async () => {
    try {
      const token = localStorage.getItem('demo_token')
      const response = await axios.get(`/api/product/products/${id}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (response.data.code === 200) {
        setProduct(response.data.data)
      }
    } catch (error) {
      message.error(t('product.detailFailed'))
    }
  }

  const handleBuy = async () => {
    if (!product) return
    setLoading(true)
    try {
      const token = localStorage.getItem('demo_token')
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
      if (response.data.code === 200) {
        message.success(t('product.buySuccess'))
        navigate('/order/my')
      }
    } catch (error: any) {
      message.error(error?.response?.data?.message || t('product.buyFailed'))
    } finally {
      setLoading(false)
    }
  }

  const handleSeckill = async () => {
    if (!product) return
    setLoading(true)
    try {
      const token = localStorage.getItem('demo_token')
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
      setLoading(false)
    }
  }

  if (!product) {
    return (
      <div style={{ minHeight: '100vh', padding: 24, background: pageBg }}>
        <div style={{ maxWidth: 1000, margin: '0 auto' }}>
          <Card loading />
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
              width: 400,
              height: 400,
              background: 'var(--ant-color-fill-tertiary, #f0f2f5)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 8,
              flexShrink: 0,
            }}
          >
            <ShoppingCartOutlined style={{ fontSize: 80, color: 'var(--ant-color-text-quaternary, #bfbfbf)' }} />
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
                  loading={loading}
                >
                  {t('product.seckillNow')}
                </Button>
              ) : (
                <Button
                  type="primary"
                  size="large"
                  icon={<ShoppingCartOutlined />}
                  onClick={handleBuy}
                  loading={loading}
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
