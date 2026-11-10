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

// ---------- 航班数据（机票详情） ----------
export interface MockFlightDetail {
  id: number
  productId: number
  departureCity: string
  arrivalCity: string
  departureLng: number
  departureLat: number
  arrivalLng: number
  arrivalLat: number
  flightNumber: string
  duration: number
  frequency: number
  routeCoordinates: string
  productName: string
  productDescription: string
  productPrice: number
  productStock: number
  productStatus: number
}

export function mockFlightDetail(overrides: Partial<MockFlightDetail> = {}): MockFlightDetail {
  return {
    id: 1,
    productId: 7,
    departureCity: '北京',
    arrivalCity: '上海',
    departureLng: 116.4074,
    departureLat: 39.9042,
    arrivalLng: 121.4737,
    arrivalLat: 31.2304,
    flightNumber: 'CA1234',
    duration: 135,
    frequency: 28,
    routeCoordinates: JSON.stringify([{ name: '济南', lng: 117.0009, lat: 36.6758 }]),
    productName: '北京→上海 机票',
    productDescription: '首都机场至虹桥机场 CA1234',
    productPrice: 1280,
    productStock: 100,
    productStatus: 1,
    ...overrides,
  }
}

export interface MockCityFrequency {
  id: number
  from: string
  to: string
  fromLng: number
  fromLat: number
  toLng: number
  toLat: number
  frequency: number
  flightNumber: string
  duration: number
  routeCoordinates: string
  productId: number
  productName: string
  productPrice: number
}

export function mockCityFrequency(
  overrides: Partial<MockCityFrequency> = {},
): MockCityFrequency {
  return {
    id: 1,
    from: '北京',
    to: '上海',
    fromLng: 116.4074,
    fromLat: 39.9042,
    toLng: 121.4737,
    toLat: 31.2304,
    frequency: 28,
    flightNumber: 'CA1234',
    duration: 135,
    routeCoordinates: JSON.stringify([{ name: '济南', lng: 117.0009, lat: 36.6758 }]),
    productId: 7,
    productName: '北京→上海 机票',
    productPrice: 1280,
    ...overrides,
  }
}

export function mockFrequencyData(count = 3) {
  return Array.from({ length: count }, (_, i) =>
    mockCityFrequency({
      id: i + 1,
      flightNumber: `CA${1000 + i}`,
      frequency: 28 - i,
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
