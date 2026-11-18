import { createRouter, createWebHistory } from 'vue-router'
import { getToken, isAdminUser, resolveHomePath } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL || '/'),
  routes: [
    {
      path: '/',
      // 按角色分流：管理员进用户管理，普通用户进商品中心
      redirect: () => resolveHomePath(),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Register.vue'),
      meta: { public: true },
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('@/views/UserManage.vue'),
      meta: { requiresAuth: true, adminOnly: true },
    },
    {
      path: '/admin/flights',
      name: 'flightMap',
      component: () => import('@/views/FlightMap.vue'),
      meta: { requiresAuth: true, adminOnly: true },
    },
    // 商品子应用路由（由 qiankun 加载 React 子应用处理）
    {
      path: '/product/:pathMatch(.*)*',
      name: 'product-app',
      component: () => import('@/views/SubAppPlaceholder.vue'),
      meta: { requiresAuth: true, isSubApp: true },
    },
    // 订单子应用路由（由 qiankun 加载原生 TypeScript 子应用处理）
    {
      path: '/order/:pathMatch(.*)*',
      name: 'order-app',
      component: () => import('@/views/SubAppPlaceholder.vue'),
      meta: { requiresAuth: true, isSubApp: true },
    },
  ],
})

router.beforeEach((to) => {
  const token = getToken()
  if (to.meta.public) {
    if (token && (to.name === 'login' || to.name === 'register')) {
      return { path: resolveHomePath() }
    }
    return true
  }
  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 管理员专属页面：普通用户直接回到自己的首页，避免进入后接口返回 403 提示“权限不足”
  if (to.meta.adminOnly && !isAdminUser()) {
    return { path: '/product' }
  }
  return true
})

export default router
