import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
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
  ],
})

/**
 * 全局路由守卫：
 * - 未登录访问受保护页面（meta.requiresAuth）→ 跳转 /login，并用 redirect 参数记录原目标
 * - 已登录访问 /login、/register → 直接重定向到用户管理页（保持与“登录后默认落地页”一致）
 */
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
