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
          name: 'workspace-dashboard',
          component: () => import('@/features/workspace/WorkspaceDashboardView.vue'),
        },
        {
          path: 'bugs',
          name: 'bug-list',
          component: () => import('@/features/bug/BugListView.vue'),
          meta: { bugListTitle: 'Bug 列表', bugListSubtitle: '查看和筛选工作空间内的全部 Bug' },
        },
        {
          path: 'bugs/mine',
          name: 'my-bugs',
          component: () => import('@/features/bug/MyBugsView.vue'),
        },
        {
          path: 'bugs/submitted',
          name: 'submitted-bugs',
          component: () => import('@/features/bug/BugListView.vue'),
          meta: {
            bugListPreset: 'submitted',
            bugListTitle: '我提交的',
            bugListSubtitle: '追踪由我创建的 Bug',
          },
        },
        {
          path: 'bugs/assigned',
          name: 'assigned-bugs',
          component: () => import('@/features/bug/BugListView.vue'),
          meta: {
            bugListPreset: 'assigned',
            bugListTitle: '指派给我的',
            bugListSubtitle: '集中处理当前由我负责的 Bug',
          },
        },
        {
          path: 'bugs/acceptance',
          name: 'acceptance-bugs',
          component: () => import('@/features/bug/BugListView.vue'),
          meta: {
            bugListPreset: 'acceptance',
            bugListTitle: '待我验收',
            bugListSubtitle: '需要我确认修复结果的 Bug',
          },
        },
        {
          path: 'members',
          name: 'workspace-members',
          component: () => import('@/features/workspace/WorkspaceMembersView.vue'),
        },
        {
          path: 'settings',
          name: 'workspace-settings',
          component: () => import('@/features/workspace/WorkspaceSettingsView.vue'),
        },
        {
          path: 'system/users',
          name: 'system-users',
          component: () => import('@/features/user/SystemUsersView.vue'),
          meta: { requiresSystemAdmin: true },
        },
        {
          path: 'bug-templates',
          name: 'bug-templates',
          component: () => import('@/features/bug/BugTemplateManager.vue'),
          // 模板管理同时服务普通成员和系统管理员，路由层注入当前权限以保证页面操作入口准确。
          props: (route) => ({
            workspaceId: Number(route.params.workspaceId),
            currentUserId: useAuthStore().user?.id ?? null,
            isSystemAdmin: useAuthStore().user?.systemRole === 'SYSTEM_ADMIN',
          }),
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
          ? { name: 'workspace-dashboard', params: { workspaceId } }
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

  // 系统管理页面只对 SYSTEM_ADMIN 开放；前端守卫用于体验，后端接口仍执行最终鉴权。
  if (to.meta.requiresSystemAdmin && auth.user?.systemRole !== 'SYSTEM_ADMIN') {
    return { name: 'home' }
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
