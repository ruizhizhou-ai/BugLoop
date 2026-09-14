/**
 * 本文件集中定义前端路由与登录守卫。工作空间是业务数据的边界，
 * 所有业务页面都以 /workspaces/:workspaceId 为前缀，URL 与 Store 双向同步。
 */
import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'

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
      path: '/workspaces',
      name: 'workspace-picker',
      component: () => import('@/features/workspace/WorkspacePickerView.vue'),
    },
    {
      path: '/workspaces/:workspaceId',
      component: () => import('@/app/layouts/WorkspaceLayout.vue'),
      children: [
        {
          path: '',
          name: 'bug-list',
          component: () => import('@/features/bug/BugListView.vue'),
        },
        {
          path: 'settings',
          name: 'workspace-settings',
          component: () => import('@/features/workspace/WorkspaceSettingsView.vue'),
        },
        {
          path: 'bugs/new',
          name: 'bug-create',
          component: () => import('@/features/bug/BugCreateView.vue'),
        },
        {
          path: 'bugs/:bugId',
          name: 'bug-detail',
          component: () => import('@/features/bug/BugDetailView.vue'),
        },
      ],
    },
    {
      path: '/',
      name: 'home',
      redirect: () => {
        const workspaceId = useWorkspaceStore().currentWorkspaceId
        return workspaceId
          ? { name: 'bug-list', params: { workspaceId } }
          : { name: 'workspace-picker' }
      },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: { name: 'home' },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const workspaceStore = useWorkspaceStore()

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

  if (to.name === 'workspace-picker') {
    return true
  }

  // 工作空间路由必须先加载列表，再把 URL 中的空间同步到 Store。
  try {
    if (workspaceStore.workspaces.length === 0) {
      await workspaceStore.loadWorkspaces()
    }
  } catch {
    return { name: 'workspace-picker' }
  }

  const routeWorkspaceId = Number(to.params.workspaceId)
  const target = workspaceStore.workspaces.find((workspace) => workspace.id === routeWorkspaceId)
  if (target) {
    if (workspaceStore.currentWorkspaceId !== target.id) {
      await workspaceStore.selectWorkspace(target.id)
    }
    return true
  }

  const fallback = workspaceStore.workspaces[0]
  if (fallback) {
    return { name: String(to.name), params: { ...to.params, workspaceId: fallback.id } }
  }
  return { name: 'workspace-picker' }
})

export default router
