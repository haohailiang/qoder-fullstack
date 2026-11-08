import { useEffect, useState } from 'react'
import { Card, Input, Select, Button, List, Tag, Space, InputNumber, message } from 'antd'
import { SearchOutlined, ShoppingCartOutlined } from '@ant-design/icons'
import axios from 'axios'
import { useNavigate } from 'react-router-dom'

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
  const [products, setProducts] = useState<Product[]>([])
  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState<string | undefined>(undefined)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const isDark = (localStorage.getItem('demo_theme') || 'dark') === 'dark'
  const pageBg = isDark ? '#141414' : '#f5f5f5'

  const fetchProducts = async () => {
    setLoading(true)
    try {
      const params: any = {}
      if (keyword) params.keyword = keyword
      if (category) params.category = category
      
      const token = localStorage.getItem('demo_token')
      const response = await axios.get('/api/product/products/search', {
        params,
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      
      if (response.data.code === 200) {
        setProducts(response.data.data || [])
      }
    } catch (error) {
      message.error('获取商品列表失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchProducts()
  }, [])

  const handleSearch = () => {
    fetchProducts()
  }

  const handleDetail = (id: number) => {
    navigate(`/detail/${id}`)
  }

  return (
    <div style={{ minHeight: '100vh', padding: 24, background: pageBg }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <Card title="商品中心" style={{ marginBottom: 16 }}>
        <Space wrap>
          <Input
            placeholder="搜索商品名称或描述"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 250 }}
            onPressEnter={handleSearch}
          />
          <Select
            placeholder="选择分类"
            value={category}
            onChange={setCategory}
            style={{ width: 150 }}
            allowClear
            options={[
              { label: '电子产品', value: 'electronics' },
              { label: '服装', value: 'clothing' },
              { label: '食品', value: 'food' },
              { label: '图书', value: 'books' },
            ]}
          />
          <Button type="primary" onClick={handleSearch}>
            搜索
          </Button>
        </Space>
      </Card>

      <List
        grid={{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 4, xxl: 4 }}
        dataSource={products}
        loading={loading}
        renderItem={(item) => (
          <List.Item>
            <Card
              hoverable
              cover={
                <div
                  style={{
                    height: 200,
                    background: 'var(--ant-color-fill-tertiary, #f0f2f5)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <ShoppingCartOutlined style={{ fontSize: 48, color: 'var(--ant-color-text-quaternary, #bfbfbf)' }} />
                </div>
              }
              actions={[
                <Button type="link" onClick={() => handleDetail(item.id)}>
                  查看详情
                </Button>,
              ]}
            >
              <Card.Meta
                title={
                  <Space>
                    {item.name}
                    {item.isSeckill === 1 && <Tag color="red">秒杀</Tag>}
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
                  库存: {item.stock}
                </span>
              </div>
            </Card>
          </List.Item>
        )}
      />
      </div>
    </div>
  )
}

export default ProductList
