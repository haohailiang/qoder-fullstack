<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import ImageUploader from '@/components/ImageUploader.vue'
import { listProducts, updateProductImage, type Product, type UploadResult } from '@/api/product'
import { useDelayedLoading } from '@/composables/useDelayedLoading'

/**
 * 商品图片管理（仅 ADMIN）：选择商品 -> 上传图片到 OSS -> 仅回写 image_url。
 * 不整实体更新商品，避免把名称、价格等字段意外置空。
 */
const { t } = useI18n()

const products = ref<Product[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const { visible: loadingVisible, wrap: withLoading } = useDelayedLoading()

const selectedProduct = computed(() => products.value.find((item) => item.id === selectedId.value) ?? null)

const selectOptions = computed(() =>
  products.value.map((item) => ({
    label: `${item.name}（ID: ${item.id}）`,
    value: item.id,
  })),
)

async function loadProducts() {
  loading.value = true
  try {
    products.value = await withLoading(() => listProducts())
    if (selectedId.value != null && !products.value.some((item) => item.id === selectedId.value)) {
      selectedId.value = null
    }
  } catch {
    // 错误提示已在 axios 响应拦截器中统一处理
  } finally {
    loading.value = false
  }
}

/** 把最新图片 URL 回写到商品（空字符串表示清除） */
async function saveImageUrl(id: number, imageUrl: string) {
  saving.value = true
  try {
    const updated = await updateProductImage(id, imageUrl)
    const target = products.value.find((item) => item.id === id)
    if (target) {
      target.imageUrl = updated.imageUrl ?? imageUrl
    }
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}

async function handleUploaded(result: UploadResult) {
  if (!selectedProduct.value) return
  const ok = await saveImageUrl(selectedProduct.value.id, result.url)
  if (ok) {
    ElMessage.success(t('productImage.updateSuccess'))
  }
}

async function handleRemove() {
  const product = selectedProduct.value
  if (!product) return
  try {
    await ElMessageBox.confirm(
      t('productImage.removeConfirm', { name: product.name }),
      t('productImage.removeConfirmTitle'),
      {
        confirmButtonText: t('productImage.remove'),
        cancelButtonText: t('productImage.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return
  }
  const ok = await saveImageUrl(product.id, '')
  if (ok) {
    ElMessage.success(t('productImage.removeSuccess'))
  }
}

onMounted(loadProducts)
</script>

<template>
  <div class="product-image-page">
    <el-card v-loading="loading || loadingVisible">
      <template #header>
        <span class="card-title">{{ t('productImage.title') }}</span>
      </template>

      <el-form label-width="120px" class="image-form">
        <el-form-item :label="t('productImage.selectProduct')">
          <el-select
            v-model="selectedId"
            filterable
            clearable
            :placeholder="t('productImage.selectPlaceholder')"
            :loading="loading"
            class="product-select"
          >
            <el-option
              v-for="item in selectOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-button class="refresh-btn" :loading="loading" @click="loadProducts">
            {{ t('productImage.refresh') }}
          </el-button>
        </el-form-item>

        <el-empty v-if="!selectedProduct" :description="t('productImage.selectHint')" />

        <template v-else>
          <el-form-item :label="t('productImage.currentImage')">
            <div class="image-block">
              <ImageUploader
                :url="selectedProduct.imageUrl || ''"
                :disabled="saving"
                @uploaded="handleUploaded"
              />
              <div class="image-actions">
                <span class="image-meta">
                  {{ selectedProduct.name }} · {{ selectedProduct.category || t('productImage.uncategorized') }}
                </span>
                <el-button
                  v-if="selectedProduct.imageUrl"
                  type="danger"
                  plain
                  size="small"
                  :loading="saving"
                  @click="handleRemove"
                >
                  {{ t('productImage.remove') }}
                </el-button>
              </div>
            </div>
          </el-form-item>
        </template>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.product-image-page {
  padding: 20px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.image-form {
  max-width: 720px;
}

.product-select {
  width: 360px;
}

.refresh-btn {
  margin-left: 12px;
}

.image-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.image-meta {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-right: 12px;
}
</style>
