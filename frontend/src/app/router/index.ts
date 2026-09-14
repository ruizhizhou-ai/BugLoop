/**
 * 本文件集中定义前端路由与登录守卫，未登录访问业务页面时统一跳转登录页。
 */
import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/features/auth/authStore'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/features/auth/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/features/auth/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      name: 'home',
      component: () => import('@/features/home/HomeView.vue'),
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    return auth.isLoggedIn ? { name: 'home' } : true
  }

  if (!auth.isLoggedIn) {
    return { name: 'login' }
  }

  if (!auth.user) {
    try {
      await auth.loadCurrentUser()
    } catch {
      auth.resetSession()
      return { name: 'login' }
    }
  }

  return true
})

export default router
