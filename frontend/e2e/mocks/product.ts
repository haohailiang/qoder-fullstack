/**
 * 商品相关 API Mock Handlers
 */
import { type Page } from '@playwright/test'
import { mockProduct, mockProductList, mockSeckillProduct, apiSuccess } from '../fixtures/test-data'

/**
 * 注册商品相关的 API mock 路由拦截。
 */
export async function setupProductMocks(page: Page): Promise<void> {
  // 商品搜索/列表
  await page.route('**/api/product/products/search**', (route) => {
    const url = new URL(route.request().url())
    const keyword = url.searchParams.get('keyword') || ''
    const category = url.searchParams.get('category') || ''

    let products = mockProductList(8)

    // 按关键词过滤
    if (keyword) {
      products = products.filter((p) => p.name.includes(keyword) || p.description.includes(keyword))
    }
    // 按分类过滤
    if (category) {
      products = products.filter((p) => p.category === category)
    }

    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(products)),
    })
  })

  // 商品详情（用正则匹配数字 ID；`:id` 不是 Playwright glob 语法，且 `*` 会把 search 请求一并吃掉）
  await page.route(/\/api\/product\/products\/\d+(\?.*)?$/, (route) => {
    const url = route.request().url()
    const idMatch = url.match(/\/products\/(\d+)/)
    const id = idMatch ? parseInt(idMatch[1]) : 1

    if (id === 2) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(mockSeckillProduct())),
      })
    } else {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(mockProduct({ id }))),
      })
    }
  })

  // 秒杀接口
  await page.route('**/api/product/products/seckill/**', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess({
        message: '秒杀成功',
        orderNo: 'SECKILL-ORD-001',
      })),
    })
  })
}
