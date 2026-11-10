import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL || '/'),
  routes: [
    {
      path: '/',
      redirect: '/users',
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
      meta: { requiresAuth: true },
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
    // 订单子应用路由（由 qiankun 加载 Angular 子应用处理）
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
      return { path: '/users' }
    }
    return true
  }
  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
