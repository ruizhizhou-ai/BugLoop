/**
 * 本文件集中定义前端路由。业务页面将在对应模块实现后按需接入，骨架阶段保持空路由表。
 */
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [],
})

export default router
