<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { logout } from '@/api/auth'
import { clearAuth, currentUserRef, tokenRef } from '@/utils/auth'
import { computed, watch } from 'vue'
import { themeRef, toggleTheme } from '@/utils/theme'
import { useI18n } from 'vue-i18n'
import { langRef, setLang, type Lang } from '@/utils/lang'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import enUs from 'element-plus/es/locale/lang/en'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

// Element Plus 动态 locale（跟随语言切换）
const epLocale = computed(() => langRef.value === 'en' ? enUs : zhCn)

// 动态更新页面标题（跟随语言切换）
watch(langRef, (lang) => {
  document.title = lang === 'en' ? 'E-Commerce · Users' : '全栈示例 · 用户管理'
}, { immediate: true })

// 判断当前是否在子应用路由中
const isInSubApp = computed(() => {
  return route.path.startsWith('/product') || route.path.startsWith('/order')
})

// 当前是否为管理员
const isAdmin = computed(() => currentUserRef.value?.username === 'admin')

// 当前是否为暗黑模式
const isDark = computed(() => themeRef.value === 'dark')

// 当前语言标签（用于按钮显示）
const langLabel = computed(() => langRef.value === 'en' ? 'EN' : '中')

function handleLangChange(lang: Lang) {
  setLang(lang)
}

async function handleLogout() {
  try {
    await logout()
  } catch {
    // 忽略：本地清理不依赖后端结果
  }
  clearAuth()
  ElMessage.success(t('app.logoutSuccess'))
  router.push('/login')
}

// 菜单活跃索引（双向绑定 el-menu，保持与路由同步）
const activeMenu = computed(() => {
  const p = route.path
  // 子应用内部路由（如 /product/list）映射到顶级菜单索引 /product
  if (p.startsWith('/product')) return '/product'
  if (p.startsWith('/order')) return '/order'
  return p
})

function navigateTo(index: unknown) {
  // el-menu 的 @select 在 active-index 属性变化时可能内部触发，传入非字符串值
  // 仅处理用户主动点击产生的合法字符串路径
  if (typeof index !== 'string' || !index.startsWith('/')) return
  if (index === route.path) return
  router.push(index)
}
</script>

<template>
  <el-config-provider :locale="epLocale">
    <el-container class="app-container">
      <el-header class="app-header">
        <div class="header-left">
          <span class="app-title">{{ t('app.title') }}</span>
          <el-menu
            mode="horizontal"
            :active-index="activeMenu"
            class="nav-menu"
            @select="navigateTo"
          >
            <el-menu-item index="/users">{{ t('app.menuUsers') }}</el-menu-item>
            <el-menu-item index="/product">{{ t('app.menuProducts') }}</el-menu-item>
            <el-menu-item index="/order">{{ t('app.menuOrders') }}</el-menu-item>
            <el-menu-item v-if="isAdmin" index="/admin/flights">{{ t('app.menuFlightMap') }}</el-menu-item>
          </el-menu>
        </div>
        <div class="user-area">
          <!-- 语言切换 -->
          <el-dropdown trigger="click" @command="handleLangChange">
            <el-button class="lang-toggle-btn" circle size="small">
              {{ langLabel }}
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="zh" :disabled="langRef === 'zh'">中文</el-dropdown-item>
                <el-dropdown-item command="en" :disabled="langRef === 'en'">English</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <!-- 主题切换 -->
          <el-tooltip :content="isDark ? t('app.switchToLight') : t('app.switchToDark')" placement="bottom">
            <el-button
              class="theme-toggle-btn"
              :icon="isDark ? 'Sunny' : 'Moon'"
              circle
              size="small"
              @click="toggleTheme"
            />
          </el-tooltip>
          <template v-if="tokenRef">
            <span class="user-name">{{ currentUserRef?.name || currentUserRef?.username }}</span>
            <el-button type="primary" plain size="small" @click="handleLogout">{{ t('app.logout') }}</el-button>
          </template>
        </div>
      </el-header>
      <el-main class="app-main">
        <!-- 基座应用的路由视图 -->
        <router-view v-if="!isInSubApp" />
        <!-- 子应用容器 -->
        <div id="subapp-container" v-show="isInSubApp"></div>
      </el-main>
    </el-container>
  </el-config-provider>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: var(--el-color-primary, #409eff);
  color: #fff;
  padding: 0 20px;
  transition: background-color 0.3s;
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
  color: rgba(255, 255, 255, 0.75);
  border-bottom-color: transparent;
  font-weight: 400;
  transition: all 0.2s ease;
}

.nav-menu .el-menu-item:hover {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.15);
  border-bottom-color: rgba(255, 255, 255, 0.6);
}

.nav-menu .el-menu-item.is-active {
  color: #fff;
  background-color: rgba(0, 0, 0, 0.15);
  border-bottom-color: #fff;
  border-bottom-width: 3px;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.3);
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.lang-toggle-btn {
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  cursor: pointer;
  transition: background-color 0.3s;
  font-size: 12px;
  font-weight: 600;
}

.lang-toggle-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}

.theme-toggle-btn {
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  cursor: pointer;
  transition: background-color 0.3s;
}

.theme-toggle-btn:hover {
  background: rgba(255, 255, 255, 0.25);
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
