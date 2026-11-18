<template>
  <!-- 子应用加载占位层：覆盖在 #subapp-container 之上，挂载完成后由父组件移除 -->
  <div class="subapp-loading">
    <div class="subapp-loading__content">
      <el-icon class="is-loading subapp-loading__icon"><Loading /></el-icon>
      <p class="subapp-loading__text">{{ t('common.loadingSubApp') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<style scoped>
.subapp-loading {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-bg-color, #fff);
  transition: background-color 0.3s;
}

.subapp-loading__content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.subapp-loading__icon {
  font-size: 24px;
  color: var(--el-color-primary, #409eff);
}

.subapp-loading__text {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
