/**
 * ImageUploader 单元测试
 *
 * 覆盖：客户端类型/大小预校验、上传成功后向外抛事件
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import i18n from '@/i18n'

vi.mock('@/api/product', () => ({
  uploadProductImage: vi.fn(),
}))

import ImageUploader from '@/components/ImageUploader.vue'
import { uploadProductImage } from '@/api/product'

function mountUploader() {
  return mount(ImageUploader, {
    props: { url: '' },
    global: { plugins: [ElementPlus, i18n] },
  })
}

/** 用 defineProperty 伪造文件大小，避免真的分配大内存 */
function fileWithSize(type: string, size: number, name = 'a.png') {
  const file = new File(['x'], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('ImageUploader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('非图片类型在 before-upload 阶段被拦截', () => {
    const wrapper = mountUploader()
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    const beforeUpload = upload.props('beforeUpload') as (file: File) => boolean

    expect(beforeUpload(fileWithSize('text/plain', 100, 'a.txt'))).toBe(false)
  })

  it('超过大小上限在 before-upload 阶段被拦截', () => {
    const wrapper = mountUploader()
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    const beforeUpload = upload.props('beforeUpload') as (file: File) => boolean

    expect(beforeUpload(fileWithSize('image/png', 6 * 1024 * 1024))).toBe(false)
    expect(beforeUpload(fileWithSize('image/png', 1024))).toBe(true)
  })

  it('上传成功后 emit uploaded 并透传结果', async () => {
    const result = {
      url: 'https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/product/a.png',
      objectKey: 'product/a.png',
      originalName: 'a.png',
      size: 10,
      contentType: 'image/png',
    }
    vi.mocked(uploadProductImage).mockResolvedValue(result)

    const wrapper = mountUploader()
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    const httpRequest = upload.props('httpRequest') as (options: {
      file: File
      onSuccess?: (response: unknown) => void
      onError?: (error: Error) => void
    }) => Promise<void>
    const onSuccess = vi.fn()

    await httpRequest({ file: fileWithSize('image/png', 10), onSuccess })
    await flushPromises()

    expect(uploadProductImage).toHaveBeenCalledTimes(1)
    expect(onSuccess).toHaveBeenCalledWith(result)
    expect(wrapper.emitted('uploaded')?.[0]?.[0]).toEqual(result)
  })

  it('上传失败时不 emit uploaded', async () => {
    vi.mocked(uploadProductImage).mockRejectedValue(new Error('upload failed'))

    const wrapper = mountUploader()
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    const httpRequest = upload.props('httpRequest') as (options: {
      file: File
      onError?: (error: Error) => void
    }) => Promise<void>
    const onError = vi.fn()

    await httpRequest({ file: fileWithSize('image/png', 10), onError })
    await flushPromises()

    expect(onError).toHaveBeenCalled()
    expect(wrapper.emitted('uploaded')).toBeUndefined()
  })
})
