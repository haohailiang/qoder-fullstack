<template>
  <!-- 子应用占位组件：实际内容由 qiankun 渲染到 #subapp-container -->
  <div class="subapp-loading">
    <el-icon class="loading-icon"><Loading /></el-icon>
    <p>正在加载子应用...</p>
  </div>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
</script>

<style scoped>
.subapp-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: #909399;
}

.loading-icon {
  font-size: 40px;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
