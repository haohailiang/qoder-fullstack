<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { logout } from '@/api/auth'
import { clearAuth, currentUserRef, tokenRef } from '@/utils/auth'

const router = useRouter()

async function handleLogout() {
  try {
    // 通知后端（JWT 无状态，服务端仅确认），无论成败都清理本地状态
    await logout()
  } catch {
    // 忽略：本地清理不依赖后端结果
  }
  clearAuth()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <span class="app-title">全栈示例 · 用户管理</span>
      <div v-if="tokenRef" class="user-area">
        <span class="user-name">{{ currentUserRef?.name || currentUserRef?.username }}</span>
        <el-button type="primary" plain size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #409eff;
  color: #fff;
}

.app-title {
  font-size: 18px;
  font-weight: 600;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-size: 14px;
}
</style>
