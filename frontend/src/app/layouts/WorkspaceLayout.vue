<!-- 本文件构建工作空间内的统一应用外壳：深色侧栏、全局搜索、账号菜单以及业务页面内容区。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElOption, ElSelect, ElTooltip } from 'element-plus'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/tooltip/style/css'

import AppIcon from '@/shared/components/AppIcon.vue'
import ThemeToggle from '@/shared/components/ThemeToggle.vue'
import { useAuthStore } from '@/features/auth/authStore'
import { useBugStore } from '@/features/bug/bugStore'
import WorkspaceCreateDialog from '@/features/workspace/WorkspaceCreateDialog.vue'
import { canCreateWorkspace } from '@/features/workspace/workspacePermissions'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'

interface NavigationItem {
  label: string
  icon: 'home' | 'box' | 'bugs' | 'submitted' | 'assigned' | 'acceptance' | 'users' | 'settings'
  routeName: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()
const bugStore = useBugStore()

const errorMessage = ref('')
const searchKeyword = ref('')
const sidebarOpen = ref(false)
const accountMenuOpen = ref(false)
const utilityPanel = ref<'notifications' | 'help' | null>(null)
const createWorkspaceDialogVisible = ref(false)

const workspaceId = computed(() => workspaceStore.currentWorkspaceId)
const accountInitial = computed(() =>
  (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase(),
)
const pageKey = computed(() => `${String(route.name)}-${String(route.params.workspaceId ?? '')}`)
const canCreate = computed(() =>
  canCreateWorkspace(auth.user?.systemRole, workspaceStore.workspaces),
)

const mainNavigation: NavigationItem[] = [
  { label: '首页', icon: 'home', routeName: 'workspace-dashboard' },
  { label: 'Bug 列表', icon: 'box', routeName: 'bug-list' },
  { label: '我的 Bug', icon: 'bugs', routeName: 'my-bugs' },
  { label: '我提交的', icon: 'submitted', routeName: 'submitted-bugs' },
  { label: '指派给我的', icon: 'assigned', routeName: 'assigned-bugs' },
  { label: '待我验收', icon: 'acceptance', routeName: 'acceptance-bugs' },
]

const manageNavigation: NavigationItem[] = [
  { label: '成员管理', icon: 'users', routeName: 'workspace-members' },
  { label: '空间设置', icon: 'settings', routeName: 'workspace-settings' },
]

const visibleManageNavigation = computed<NavigationItem[]>(() => {
  if (auth.user?.systemRole !== 'SYSTEM_ADMIN') return manageNavigation
  return [...manageNavigation, { label: '用户管理', icon: 'users', routeName: 'system-users' }]
})

onMounted(() => {
  if (workspaceStore.workspaces.length === 0) {
    void loadWorkspaces()
  }
})

/** 重新加载可访问工作空间，并在失败时保留当前页面便于用户重试。 */
async function loadWorkspaces(): Promise<void> {
  try {
    await workspaceStore.loadWorkspaces()
  } catch {
    errorMessage.value = '加载工作空间失败，请刷新重试'
  }
}

/**
 * 切换工作空间时清理 Bug 查询状态，并回到新空间首页，避免详情页残留旧空间数据。
 *
 * @param value 新工作空间主键
 */
async function handleWorkspaceChange(value: number): Promise<void> {
  try {
    await workspaceStore.selectWorkspace(value)
    bugStore.resetQuery()
    sidebarOpen.value = false
    await router.push({ name: 'workspace-dashboard', params: { workspaceId: value } })
  } catch {
    errorMessage.value = '切换工作空间失败，请稍后重试'
  }
}

/** 创建成功后复用切换流程进入新空间，并清理旧空间的 Bug 查询条件。 */
async function handleWorkspaceCreated(workspaceId: number): Promise<void> {
  bugStore.resetQuery()
  sidebarOpen.value = false
  await router.push({ name: 'workspace-dashboard', params: { workspaceId } })
}

/** 进入侧栏目标页，并在移动端自动收起导航抽屉。 */
function navigate(routeName: string): void {
  if (!workspaceId.value) {
    return
  }
  sidebarOpen.value = false
  void router.push({ name: routeName, params: { workspaceId: workspaceId.value } })
}

/** 全局搜索复用 Bug 列表接口，回车后将关键词带入列表筛选。 */
function submitSearch(): void {
  if (!workspaceId.value) {
    return
  }
  bugStore.resetQuery()
  bugStore.query.keyword = searchKeyword.value.trim() || undefined
  void router.push({ name: 'bug-list', params: { workspaceId: workspaceId.value } })
}

/** 顶部辅助入口先展示完整交互外观，并明确说明后续接口接入范围。 */
function toggleUtilityPanel(panel: 'notifications' | 'help'): void {
  utilityPanel.value = utilityPanel.value === panel ? null : panel
  accountMenuOpen.value = false
}

/** 退出前清理业务 Store，防止下一账号看到上一账号的瞬时缓存。 */
async function handleLogout(): Promise<void> {
  bugStore.reset()
  workspaceStore.reset()
  await auth.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <div class="workspace-shell">
    <aside class="workspace-sidebar" :class="{ 'workspace-sidebar--open': sidebarOpen }">
      <div class="brand" @click="navigate('workspace-dashboard')">
        <span class="brand__mark" aria-hidden="true"><i /></span>
        <span>BugLoop</span>
      </div>

      <div v-if="workspaceStore.workspaces.length" class="workspace-switcher-row">
        <el-tooltip content="切换工作空间" placement="top" :show-after="300">
          <el-select
            class="workspace-switcher"
            :model-value="workspaceStore.currentWorkspaceId"
            placeholder="选择工作空间"
            :loading="workspaceStore.loading"
            @change="handleWorkspaceChange"
          >
            <el-option
              v-for="workspace in workspaceStore.workspaces"
              :key="workspace.id"
              :label="workspace.name"
              :value="workspace.id"
            >
              <span>{{ workspace.name }}</span>
              <span v-if="workspace.status === 'DISABLED'" class="workspace-option__status"
                >已停用</span
              >
            </el-option>
          </el-select>
        </el-tooltip>
        <el-tooltip v-if="canCreate" content="新建工作空间" placement="top" :show-after="300">
          <button
            type="button"
            class="workspace-create-button"
            aria-label="新建工作空间"
            @click="createWorkspaceDialogVisible = true"
          >
            <AppIcon name="plus" :size="18" />
          </button>
        </el-tooltip>
      </div>

      <nav class="sidebar-nav" aria-label="工作空间导航">
        <button
          v-for="item in mainNavigation"
          :key="item.routeName"
          type="button"
          class="sidebar-nav__item"
          :class="{ 'sidebar-nav__item--active': route.name === item.routeName }"
          @click="navigate(item.routeName)"
        >
          <AppIcon :name="item.icon" :size="19" />
          <span>{{ item.label }}</span>
        </button>

        <div class="sidebar-nav__divider" />

        <button
          v-for="item in visibleManageNavigation"
          :key="item.routeName"
          type="button"
          class="sidebar-nav__item"
          :class="{ 'sidebar-nav__item--active': route.name === item.routeName }"
          @click="navigate(item.routeName)"
        >
          <AppIcon :name="item.icon" :size="19" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="sidebar-card">
        <span class="sidebar-card__icon"><AppIcon name="bugs" :size="18" /></span>
        <div>
          <strong>打造更高效的<br />Bug 协作体验</strong>
          <p>让产品更好，让团队更强</p>
        </div>
      </div>
    </aside>

    <button
      v-if="sidebarOpen"
      class="workspace-sidebar__backdrop"
      type="button"
      aria-label="关闭导航"
      @click="sidebarOpen = false"
    />

    <section class="workspace-main">
      <header class="workspace-topbar">
        <button
          class="topbar-icon topbar-menu"
          type="button"
          aria-label="打开导航"
          @click="sidebarOpen = true"
        >
          <AppIcon name="menu" />
        </button>

        <form class="global-search" role="search" @submit.prevent="submitSearch">
          <AppIcon name="search" :size="18" />
          <input
            v-model="searchKeyword"
            type="search"
            placeholder="搜索 Bug、编号、标题或内容..."
          />
          <kbd>⌘ K</kbd>
        </form>

        <div class="topbar-actions">
          <div class="utility-anchor">
            <button
              class="topbar-icon"
              type="button"
              aria-label="通知"
              @click="toggleUtilityPanel('notifications')"
            >
              <AppIcon name="bell" />
              <span class="notification-dot" />
            </button>
            <div v-if="utilityPanel === 'notifications'" class="utility-panel">
              <strong>通知中心</strong>
              <p>通知聚合与已读状态将在后续版本接入。</p>
              <span>页面结构已预留</span>
            </div>
          </div>

          <div class="utility-anchor">
            <button
              class="topbar-icon"
              type="button"
              aria-label="帮助"
              @click="toggleUtilityPanel('help')"
            >
              <AppIcon name="help" />
            </button>
            <div v-if="utilityPanel === 'help'" class="utility-panel utility-panel--help">
              <strong>帮助与快捷操作</strong>
              <p>输入关键词可搜索 Bug，使用左侧导航快速切换工作视图。</p>
              <span>更多帮助文档将在后续开放</span>
            </div>
          </div>

          <ThemeToggle />

          <span class="topbar-actions__divider" />

          <div class="account-menu">
            <button
              type="button"
              class="account-menu__trigger"
              @click="accountMenuOpen = !accountMenuOpen"
            >
              <span class="account-avatar">{{ accountInitial }}</span>
              <span class="account-name">{{ auth.user?.displayName || auth.user?.username }}</span>
              <span class="account-caret">⌄</span>
            </button>
            <div v-if="accountMenuOpen" class="account-menu__panel">
              <div class="account-menu__identity">
                <strong>{{ auth.user?.displayName }}</strong>
                <span>{{
                  auth.user?.systemRole === 'SYSTEM_ADMIN' ? '系统管理员' : '普通用户'
                }}</span>
              </div>
              <button type="button" @click="handleLogout">
                <AppIcon name="logout" :size="17" />
                退出登录
              </button>
            </div>
          </div>
        </div>
      </header>

      <div v-if="errorMessage" class="workspace-error">{{ errorMessage }}</div>
      <div class="workspace-content">
        <router-view :key="pageKey" />
      </div>
    </section>

    <WorkspaceCreateDialog
      v-model="createWorkspaceDialogVisible"
      @created="handleWorkspaceCreated"
    />
  </div>
</template>

<style scoped>
.workspace-shell {
  min-height: 100vh;
  color: var(--bl-text);
  background:
    radial-gradient(circle at 70% 0%, rgb(35 67 104 / 11%), transparent 30%), var(--bl-bg);
}

.workspace-sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 30;
  display: flex;
  width: 256px;
  flex-direction: column;
  padding: 0 15px 18px;
  background: linear-gradient(180deg, #11171e 0%, #0f141a 100%);
  border-right: 1px solid var(--bl-border);
}

.brand {
  display: flex;
  height: 64px;
  align-items: center;
  gap: 11px;
  padding: 0 10px;
  color: #f5f8fc;
  font-size: 21px;
  font-weight: 730;
  letter-spacing: -0.4px;
  cursor: pointer;
}

.brand__mark {
  position: relative;
  display: grid;
  width: 31px;
  height: 31px;
  place-items: center;
  transform: rotate(-32deg);
  border: 6px solid #318cff;
  border-radius: 50%;
  box-shadow: 0 0 18px rgb(49 140 255 / 22%);
}

.brand__mark::after {
  position: absolute;
  right: -7px;
  width: 10px;
  height: 6px;
  content: '';
  background: #318cff;
  border-radius: 2px;
}

.brand__mark i {
  width: 7px;
  height: 7px;
  border: 2px solid #62c4ff;
  border-radius: 50%;
}

.workspace-switcher-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 7px 0 16px;
}

.workspace-switcher {
  min-width: 0;
  flex: 1;
}

.workspace-switcher :deep(.el-select__wrapper) {
  min-height: 49px;
  padding: 0 13px;
  background: #182029;
  border: 1px solid #29333f;
  border-radius: 9px;
  box-shadow: none;
}

.workspace-switcher :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--bl-primary) inset;
}

.workspace-option__status {
  margin-left: 8px;
  color: var(--bl-muted);
  font-size: 12px;
}

.workspace-create-button {
  display: grid;
  width: 40px;
  height: 49px;
  flex: 0 0 40px;
  place-items: center;
  color: #8ebef5;
  cursor: pointer;
  background: #182029;
  border: 1px solid #29333f;
  border-radius: 9px;
  transition: 160ms ease;
}

.workspace-create-button:hover {
  color: #fff;
  background: #1d2d40;
  border-color: #357cc8;
  box-shadow: 0 0 0 3px rgb(49 140 255 / 8%);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.sidebar-nav__item {
  display: flex;
  width: 100%;
  height: 44px;
  align-items: center;
  gap: 14px;
  padding: 0 16px;
  color: #c2ccda;
  font: inherit;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  transition: 160ms ease;
}

.sidebar-nav__item:hover {
  color: #e7eef8;
  background: #18212b;
}

.sidebar-nav__item--active {
  color: #54a6ff;
  background: linear-gradient(90deg, #202b37, #1c252f);
  border-color: #2c3743;
  box-shadow: 0 8px 20px rgb(0 0 0 / 12%);
}

.sidebar-nav__divider {
  height: 1px;
  margin: 13px 0;
  background: #252d36;
}

.sidebar-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: auto;
  padding: 20px 17px;
  border: 1px solid #2a333e;
  border-radius: 9px;
  background: linear-gradient(145deg, rgb(22 31 41 / 85%), rgb(15 20 26 / 70%));
}

.sidebar-card__icon {
  display: grid;
  width: 37px;
  height: 37px;
  flex: 0 0 auto;
  place-items: center;
  color: #3f9bff;
  background: #172b43;
  border-radius: 8px;
}

.sidebar-card strong {
  color: #dce5f0;
  font-size: 13px;
  line-height: 1.55;
}

.sidebar-card p {
  margin: 8px 0 0;
  color: #788697;
  font-size: 11px;
}

.workspace-main {
  min-width: 0;
  min-height: 100vh;
  margin-left: 256px;
}

.workspace-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  height: 62px;
  align-items: center;
  justify-content: center;
  padding: 0 31px;
  background: rgb(15 20 26 / 92%);
  border-bottom: 1px solid var(--bl-border);
  backdrop-filter: blur(18px);
}

.global-search {
  display: flex;
  width: min(560px, 52vw);
  height: 39px;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  color: #778598;
  background: #171e26;
  border: 1px solid #2d3743;
  border-radius: 9px;
  box-shadow: 0 7px 20px rgb(0 0 0 / 14%);
}

.global-search:focus-within {
  border-color: #397ec9;
  box-shadow: 0 0 0 3px rgb(48 137 240 / 9%);
}

.global-search input {
  min-width: 0;
  flex: 1;
  color: #dce6f3;
  font: inherit;
  font-size: 13px;
  outline: none;
  background: transparent;
  border: 0;
}

.global-search input::placeholder {
  color: #718093;
}

.global-search input::-webkit-search-cancel-button {
  filter: invert(0.8);
}

.global-search kbd {
  padding: 2px 5px;
  color: #6f7b8a;
  font: 11px inherit;
  background: #202832;
  border: 1px solid #313b47;
  border-radius: 4px;
}

.topbar-actions {
  position: absolute;
  right: 28px;
  display: flex;
  align-items: center;
  gap: 9px;
}

.topbar-icon {
  position: relative;
  display: grid;
  width: 37px;
  height: 37px;
  place-items: center;
  color: #aebac9;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
}

.topbar-icon:hover {
  color: #eef5ff;
  background: #1c252f;
}

.topbar-menu {
  display: none;
}

.notification-dot {
  position: absolute;
  top: 7px;
  right: 6px;
  width: 6px;
  height: 6px;
  background: #ff5148;
  border: 1px solid #11171e;
  border-radius: 50%;
}

.topbar-actions__divider {
  width: 1px;
  height: 28px;
  margin: 0 4px;
  background: #29313b;
}

.utility-anchor,
.account-menu {
  position: relative;
}

.utility-panel,
.account-menu__panel {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: 280px;
  padding: 17px;
  color: var(--bl-text);
  background: #182029;
  border: 1px solid #303b47;
  border-radius: 10px;
  box-shadow: 0 18px 45px rgb(0 0 0 / 34%);
}

.utility-panel strong {
  font-size: 13px;
}

.utility-panel p {
  margin: 10px 0 12px;
  color: var(--bl-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.utility-panel span {
  color: var(--bl-primary-light);
  font-size: 12px;
}

.utility-panel--help {
  right: -45px;
}

.account-menu__trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 5px;
  color: #e6edf7;
  font: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
}

.account-menu__trigger:hover {
  background: #1c252f;
}

.account-avatar {
  display: grid;
  width: 35px;
  height: 35px;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, #1976e9, #3a99ff);
  border: 1px solid #52a7ff;
  border-radius: 50%;
  box-shadow: 0 4px 15px rgb(33 135 246 / 22%);
}

.account-name {
  max-width: 110px;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-caret {
  color: #8592a3;
}

.account-menu__panel {
  width: 210px;
}

.account-menu__identity {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 13px;
  border-bottom: 1px solid var(--bl-border);
}

.account-menu__identity span {
  color: var(--bl-muted);
  font-size: 12px;
}

.account-menu__panel button {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 9px;
  margin-top: 10px;
  padding: 9px;
  color: #c5cfdb;
  font: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.account-menu__panel button:hover {
  color: #fff;
  background: #222d38;
}

.workspace-content {
  padding: 28px 36px 36px;
}

.workspace-error {
  padding: 9px 36px;
  color: #ff938d;
  font-size: 13px;
  background: rgb(127 32 32 / 28%);
  border-bottom: 1px solid rgb(232 75 75 / 22%);
}

@media (max-width: 1050px) {
  .workspace-topbar {
    justify-content: flex-start;
  }

  .global-search {
    margin-left: 48px;
  }

  .account-name,
  .account-caret,
  .topbar-actions__divider {
    display: none;
  }
}

@media (max-width: 820px) {
  .workspace-sidebar {
    transform: translateX(-100%);
    transition: transform 180ms ease;
  }

  .workspace-sidebar--open {
    transform: translateX(0);
  }

  .workspace-sidebar__backdrop {
    position: fixed;
    inset: 0;
    z-index: 25;
    cursor: pointer;
    background: rgb(0 0 0 / 55%);
    border: 0;
  }

  .workspace-main {
    margin-left: 0;
  }

  .topbar-menu {
    position: absolute;
    left: 13px;
    display: grid;
  }

  .workspace-topbar {
    padding: 0 14px;
  }

  .global-search {
    width: calc(100% - 155px);
    margin-left: 45px;
  }

  .topbar-actions {
    right: 12px;
  }

  .topbar-actions .utility-anchor {
    display: none;
  }

  .workspace-content {
    padding: 20px 16px 28px;
  }
}

@media (max-width: 520px) {
  .global-search {
    width: calc(100% - 104px);
  }

  .global-search kbd {
    display: none;
  }
}
</style>
