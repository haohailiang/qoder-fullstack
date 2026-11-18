/**
 * ProductImageManage 单元测试
 *
 * 覆盖：加载商品列表、上传成功后仅回写 image_url
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import i18n from '@/i18n'

vi.mock('@/api/product', () => ({
  listProducts: vi.fn(),
  updateProductImage: vi.fn(),
}))

import ProductImageManage from '@/views/ProductImageManage.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import { listProducts, updateProductImage, type Product } from '@/api/product'

const PRODUCT: Product = {
  id: 1,
  name: 'iPhone 15 Pro',
  description: '旗舰手机',
  price: 7999,
  stock: 10,
  category: 'electronics',
  imageUrl: '',
  status: 1,
  isSeckill: 0,
}

describe('ProductImageManage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listProducts).mockResolvedValue([PRODUCT])
    vi.mocked(updateProductImage).mockResolvedValue({ ...PRODUCT, imageUrl: 'https://cdn/a.png' })
  })

  it('挂载后加载商品列表', async () => {
    const wrapper = mount(ProductImageManage, { global: { plugins: [ElementPlus, i18n] } })
    await flushPromises()

    expect(listProducts).toHaveBeenCalledTimes(1)
    // el-select 的下拉项是懒渲染的，这里验证选择器已渲染且未选中商品时展示引导态
    expect(wrapper.findComponent({ name: 'ElSelect' }).exists()).toBe(true)
    expect(wrapper.findComponent(ImageUploader).exists()).toBe(false)
  })

  it('上传成功后调用图片回写接口并更新本地状态', async () => {
    const wrapper = mount(ProductImageManage, { global: { plugins: [ElementPlus, i18n] } })
    await flushPromises()

    // 选择商品（el-select 双向绑定的 update 事件）
    wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', PRODUCT.id)
    await flushPromises()

    const uploader = wrapper.findComponent(ImageUploader)
    expect(uploader.exists()).toBe(true)

    uploader.vm.$emit('uploaded', {
      url: 'https://cdn/a.png',
      objectKey: 'product/a.png',
      originalName: 'a.png',
      size: 10,
      contentType: 'image/png',
    })
    await flushPromises()

    expect(updateProductImage).toHaveBeenCalledWith(PRODUCT.id, 'https://cdn/a.png')
    expect(uploader.props('url')).toBe('https://cdn/a.png')
  })
})
