/**
 * E2E 测试数据工厂
 *
 * 提供可复用的 mock 数据生成函数，确保测试数据一致性。
 */

// ---------- 用户数据 ----------
export function mockUser(overrides: Partial<MockUser> = {}): MockUser {
  return {
    id: 1,
    username: 'testuser',
    name: '测试用户',
    email: 'test@example.com',
    phone: '13800138000',
    age: 25,
    themePreference: 'dark',
    languagePreference: 'zh',
    ...overrides,
  }
}

export function mockAdminUser(): MockUser {
  return mockUser({
    id: 0,
    username: 'admin',
    name: '管理员',
    email: 'admin@example.com',
  })
}

export interface MockUser {
  id: number
  username: string
  name: string
  email: string
  phone: string
  age: number
  themePreference?: string
  languagePreference?: string
}

// ---------- 登录响应 ----------
export function mockLoginResult(user?: MockUser): MockLoginResult {
  return {
    token: 'mock-jwt-token-e2e-test',
    expiresIn: 86400,
    user: user ?? mockUser(),
  }
}

export interface MockLoginResult {
  token: string
  expiresIn: number
  user: MockUser
}

// ---------- 验证码 ----------
export function mockCaptcha(): MockCaptcha {
  return {
    captchaKey: 'mock-captcha-key-123',
    captchaImage: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  }
}

export interface MockCaptcha {
  captchaKey: string
  captchaImage: string
}

// ---------- 商品数据 ----------
export function mockProduct(overrides: Partial<MockProduct> = {}): MockProduct {
  return {
    id: 1,
    name: '测试商品',
    description: '这是一个用于 E2E 测试的商品描述',
    price: 99.99,
    stock: 100,
    category: 'electronics',
    imageUrl: '',
    status: 1,
    isSeckill: 0,
    seckillPrice: 0,
    seckillStartTime: '',
    seckillEndTime: '',
    ...overrides,
  }
}

export function mockSeckillProduct(): MockProduct {
  return mockProduct({
    id: 2,
    name: '秒杀商品',
    price: 199.99,
    isSeckill: 1,
    seckillPrice: 99.99,
    seckillStartTime: new Date(Date.now() - 3600_000).toISOString(),
    seckillEndTime: new Date(Date.now() + 3600_000).toISOString(),
  })
}

export interface MockProduct {
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

export function mockProductList(count = 4): MockProduct[] {
  const categories = ['electronics', 'clothing', 'food', 'books']
  return Array.from({ length: count }, (_, i) =>
    mockProduct({
      id: i + 1,
      name: `商品 ${i + 1}`,
      category: categories[i % categories.length],
      price: (i + 1) * 29.99,
      stock: (i + 1) * 10,
    }),
  )
}

// ---------- 订单数据 ----------
export function mockOrder(overrides: Partial<MockOrder> = {}): MockOrder {
  return {
    id: 1,
    orderNo: 'ORD-2024-001',
    userId: 1,
    productId: 1,
    productName: '测试商品',
    quantity: 1,
    totalAmount: 99.99,
    status: 0, // 0=待支付, 1=已支付, 2=已发货, 3=已完成, 4=已取消
    address: '测试地址',
    remark: '测试备注',
    createdDate: new Date().toISOString(),
    ...overrides,
  }
}

export interface MockOrder {
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

export function mockOrderList(count = 3): MockOrder[] {
  const statuses = [0, 1, 2, 3, 4]
  return Array.from({ length: count }, (_, i) =>
    mockOrder({
      id: i + 1,
      orderNo: `ORD-2024-${String(i + 1).padStart(3, '0')}`,
      productName: `商品 ${i + 1}`,
      quantity: i + 1,
      totalAmount: (i + 1) * 99.99,
      status: statuses[i % statuses.length],
    }),
  )
}

// ---------- 统一 API 响应包装 ----------
export function apiSuccess<T>(data: T): ApiResponse<T> {
  return { code: 200, message: 'ok', data }
}

export function apiError(message: string, code = 500): ApiResponse<null> {
  return { code, message, data: null }
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
