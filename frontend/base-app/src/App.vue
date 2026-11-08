<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { logout } from '@/api/auth'
import { clearAuth, currentUserRef, tokenRef } from '@/utils/auth'
import { computed } from 'vue'

const router = useRouter()
const route = useRoute()

// 判断当前是否在子应用路由中
const isInSubApp = computed(() => {
  return route.path.startsWith('/product') || route.path.startsWith('/order')
})

async function handleLogout() {
  try {
    await logout()
  } catch {
    // 忽略：本地清理不依赖后端结果
  }
  clearAuth()
  ElMessage.success('已退出登录')
  router.push('/login')
}

function navigateTo(path: string) {
  router.push(path)
}
</script>

<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <div class="header-left">
        <span class="app-title">郝海亮电商平台</span>
        <el-menu
          mode="horizontal"
          :default-active="route.path"
          class="nav-menu"
          @select="navigateTo"
        >
          <el-menu-item index="/users">用户管理</el-menu-item>
          <el-menu-item index="/product">商品中心</el-menu-item>
          <el-menu-item index="/order">订单管理</el-menu-item>
        </el-menu>
      </div>
      <div v-if="tokenRef" class="user-area">
        <span class="user-name">{{ currentUserRef?.name || currentUserRef?.username }}</span>
        <el-button type="primary" plain size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>
    <el-main class="app-main">
      <!-- 基座应用的路由视图 -->
      <router-view v-if="!isInSubApp" />
      <!-- 子应用容器 -->
      <div id="subapp-container" v-show="isInSubApp"></div>
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
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.app-title {
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}

.nav-menu {
  background-color: transparent;
  border-bottom: none;
  flex: 1;
  min-width: 0;
}

.nav-menu .el-menu-item {
  color: rgba(255, 255, 255, 0.85);
  border-bottom-color: transparent;
}

.nav-menu .el-menu-item:hover,
.nav-menu .el-menu-item.is-active {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.15);
  border-bottom-color: #fff;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-size: 14px;
}

.app-main {
  padding: 0;
}

#subapp-container {
  width: 100%;
  min-height: calc(100vh - 60px);
}
</style>
