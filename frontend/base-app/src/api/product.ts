import request from './request'

/** 商品实体（管理端视角，与后端 product 服务 entity.Product 对应） */
export interface Product {
  id: number
  name: string
  description: string
  price: number
  stock: number
  category: string
  imageUrl: string
  status: number
  isSeckill: number
  seckillPrice?: number | null
  seckillStartTime?: string | null
  seckillEndTime?: string | null
}

/** OSS 上传结果（与后端 FileStorageService.UploadResult 对应） */
export interface UploadResult {
  url: string
  objectKey: string
  originalName: string
  size: number
  contentType: string
}

/** 查询商品列表 */
export function listProducts() {
  return request.get<Product[]>('/product/products')
}

/** 上传商品图片到 OSS（管理接口，仅 ADMIN） */
export function uploadProductImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<UploadResult>('/product/files/images', formData)
}

/** 仅更新商品图片 URL（传空字符串表示清除图片） */
export function updateProductImage(id: number, imageUrl: string) {
  return request.put<Product>(`/product/products/${id}/image`, { imageUrl })
}

/** 删除 OSS 中的图片对象（管理接口，仅 ADMIN，运维用途） */
export function deleteProductImage(objectKey: string) {
  return request.delete<void>('/product/files/images', { params: { objectKey } })
}
