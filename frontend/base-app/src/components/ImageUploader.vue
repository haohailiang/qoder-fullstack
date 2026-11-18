<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Plus } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { uploadProductImage, type UploadResult } from '@/api/product'

/**
 * 商品图片上传组件：负责选图、客户端预校验与上传，
 * 上传成功后只抛事件，图片 URL 回写业务由父组件决定。
 */
const props = withDefaults(
  defineProps<{
    /** 当前图片地址，为空时展示上传占位 */
    url?: string
    /** 外部禁用（如未选择商品） */
    disabled?: boolean
    /** 单文件大小上限（MB），与后端 oss.max-size-mb 保持一致 */
    maxSizeMb?: number
  }>(),
  { url: '', disabled: false, maxSizeMb: 5 },
)

const emit = defineEmits<{
  (e: 'uploaded', payload: UploadResult): void
}>()

const { t } = useI18n()

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const uploading = ref(false)

function beforeUpload(file: File): boolean {
  if (!ALLOWED_TYPES.includes(file.type)) {
    ElMessage.error(t('productImage.invalidType'))
    return false
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.error(t('productImage.tooLarge', { size: props.maxSizeMb }))
    return false
  }
  return true
}

interface UploadRequestOptions {
  file: File
  onSuccess?: (response: unknown) => void
  onError?: (error: Error) => void
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const result = await uploadProductImage(options.file)
    ElMessage.success(t('productImage.uploadSuccess'))
    emit('uploaded', result)
    options.onSuccess?.(result)
  } catch (error) {
    // 失败提示已由 axios 响应拦截器统一处理，这里只把错误抛给 el-upload 收尾
    options.onError?.(error as Error)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="image-uploader">
    <el-upload
      class="uploader-trigger"
      :show-file-list="false"
      :before-upload="beforeUpload"
      :http-request="handleUpload"
      :disabled="disabled || uploading"
      accept="image/jpeg,image/png,image/webp,image/gif"
    >
      <div class="preview-box" :class="{ 'is-disabled': disabled }">
        <el-image v-if="url" :src="url" fit="contain" class="preview-image" />
        <div v-else class="preview-placeholder">
          <el-icon :size="28"><Plus /></el-icon>
          <span class="placeholder-text">{{ t('productImage.upload') }}</span>
        </div>
        <div v-if="uploading" class="uploading-mask">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        </div>
      </div>
    </el-upload>
    <p class="uploader-hint">{{ t('productImage.uploadHint') }}</p>
  </div>
</template>

<style scoped>
.image-uploader {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-box {
  position: relative;
  width: 180px;
  height: 180px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s;
}

.preview-box:hover {
  border-color: var(--el-color-primary);
}

.preview-box.is-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.preview-image {
  width: 100%;
  height: 100%;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
}

.placeholder-text {
  font-size: 12px;
}

.uploading-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.uploader-hint {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
