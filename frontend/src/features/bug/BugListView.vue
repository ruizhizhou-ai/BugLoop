<!-- 本文件实现 Bug 列表页：筛选、分页、状态与人员展示，并提供新建入口。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElDatePicker,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElPopover,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/checkbox/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/pagination/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/popover/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'

import { useBugStore } from './bugStore'
import BugDetailView from './BugDetailView.vue'
import {
  BUG_PRIORITY_OPTIONS,
  BUG_STATUS_OPTIONS,
  PRIORITY_META,
  STATUS_META,
  formatDateTime,
} from './bugMeta'
import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'
import { isApiError } from '@/shared/api/types'
import AppIcon from '@/shared/components/AppIcon.vue'

const route = useRoute()
const router = useRouter()
const bugStore = useBugStore()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const errorMessage = ref('')
const dateRange = ref<[string, string] | null>(null)

type OptionalFilterKey = 'status' | 'priority' | 'assignee' | 'creator' | 'acceptor' | 'date'

const OPTIONAL_FILTER_OPTIONS: Array<{ key: OptionalFilterKey; label: string }> = [
  { key: 'status', label: '状态' },
  { key: 'priority', label: '优先级' },
  { key: 'assignee', label: '负责人' },
  { key: 'creator', label: '提交人' },
  { key: 'acceptor', label: '验收人' },
  { key: 'date', label: '创建时间' },
]
// 初始不主动展开可选项；已有预置值的字段由 isFilterVisible 自动显现。
const visibleOptionalFilters = ref<OptionalFilterKey[]>([])

const workspaceId = computed(() => Number(route.params.workspaceId))
const canCreate = computed(() => workspaceStore.isEnabled)
const pageTitle = computed(() => String(route.meta?.bugListTitle ?? 'Bug 列表'))
const pageSubtitle = computed(() =>
  String(route.meta?.bugListSubtitle ?? '查看和筛选工作空间内的全部 Bug'),
)
const selectedBugId = computed<number | null>(() => {
  const queryValue = route.query?.bugId
  const rawValue = Array.isArray(queryValue) ? queryValue[0] : queryValue
  const parsed = Number(rawValue)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})
const detailDrawerVisible = computed({
  get: () => selectedBugId.value !== null,
  set: (visible: boolean) => {
    if (!visible) closeBug()
  },
})

onMounted(() => {
  applyRoutePreset()
  void loadBugs()
})

/** 根据侧栏入口设置人员和状态筛选，让个人工作视图直接复用标准列表能力。 */
function applyRoutePreset(): void {
  const preset = route.meta?.bugListPreset
  const userId = auth.user?.id
  if (!preset || !userId) {
    return
  }
  bugStore.resetQuery()
  if (preset === 'submitted') bugStore.query.creatorId = userId
  if (preset === 'assigned') bugStore.query.assigneeId = userId
  if (preset === 'acceptance') {
    bugStore.query.acceptorId = userId
    bugStore.query.status = 'WAIT_ACCEPTANCE'
  }
}

/** 切换工作空间时清空筛选，避免把上一个空间的条件带过来。 */
async function loadBugs(): Promise<void> {
  errorMessage.value = ''
  try {
    await bugStore.loadBugs(workspaceId.value)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载 Bug 列表失败'
  }
}

/** 应用筛选条件并回到第一页。 */
async function applyFilters(): Promise<void> {
  bugStore.query.page = 1
  await loadBugs()
}

/** 重置全部筛选条件。 */
async function resetFilters(): Promise<void> {
  bugStore.resetQuery()
  dateRange.value = null
  visibleOptionalFilters.value = []
  await loadBugs()
}

/** 判断一个可选筛选项是否已有生效值，保证预置条件不会因默认折叠而不可见。 */
function hasFilterValue(key: OptionalFilterKey): boolean {
  switch (key) {
    case 'status':
      return Boolean(bugStore.query.status)
    case 'priority':
      return Boolean(bugStore.query.priority)
    case 'assignee':
      return bugStore.query.assigneeId !== undefined
    case 'creator':
      return bugStore.query.creatorId !== undefined
    case 'acceptor':
      return bugStore.query.acceptorId !== undefined
    case 'date':
      return Boolean(bugStore.query.startDate || bugStore.query.endDate)
  }
}

/** 已手动选择展示，或当前已携带预置筛选值时，才渲染可选条件。 */
function isFilterVisible(key: OptionalFilterKey): boolean {
  return visibleOptionalFilters.value.includes(key) || hasFilterValue(key)
}

/** 菜单勾选状态同时反映手动展示项与路由预置的生效条件，避免已显示字段在菜单中看似未选中。 */
const selectedOptionalFilters = computed(() =>
  OPTIONAL_FILTER_OPTIONS.filter(({ key }) => isFilterVisible(key)).map(({ key }) => key),
)

/** 清除被隐藏条件的实际查询值，避免界面看不到却仍然影响列表结果。 */
function clearFilterValue(key: OptionalFilterKey): void {
  switch (key) {
    case 'status':
      bugStore.query.status = undefined
      break
    case 'priority':
      bugStore.query.priority = undefined
      break
    case 'assignee':
      bugStore.query.assigneeId = undefined
      break
    case 'creator':
      bugStore.query.creatorId = undefined
      break
    case 'acceptor':
      bugStore.query.acceptorId = undefined
      break
    case 'date':
      dateRange.value = null
      bugStore.query.startDate = undefined
      bugStore.query.endDate = undefined
      break
  }
}

/**
 * 更新用户选择的筛选项。取消勾选已生效的条件时立即清理并重新查询，保持筛选状态可见可控。
 */
function handleVisibleFiltersChange(nextValues: Array<string | number | boolean>): void {
  // Element Plus 允许复选框值为字符串或数字；这里仅接收本页声明的筛选键，隔离组件库通用类型。
  const nextKeys = nextValues.filter(
    (value): value is OptionalFilterKey =>
      typeof value === 'string' && OPTIONAL_FILTER_OPTIONS.some(({ key }) => key === value),
  )
  const hiddenKeys = selectedOptionalFilters.value.filter((key) => !nextKeys.includes(key))
  const requiresReload = hiddenKeys.some((key) => hasFilterValue(key))
  hiddenKeys.forEach(clearFilterValue)
  visibleOptionalFilters.value = nextKeys
  if (requiresReload) void applyFilters()
}

/** 关键词或任一可选条件已有输入时，提供重置入口以快速回到默认精简状态。 */
const hasActiveFilters = computed(
  () => Boolean(bugStore.query.keyword) || OPTIONAL_FILTER_OPTIONS.some(({ key }) => hasFilterValue(key)),
)

/** 日期范围转为服务端需要的 YYYY-MM-DD 参数。 */
function handleDateChange(value: [string, string] | null): void {
  dateRange.value = value
  bugStore.query.startDate = value?.[0]
  bugStore.query.endDate = value?.[1]
  void applyFilters()
}

async function handlePageChange(page: number): Promise<void> {
  bugStore.query.page = page
  await loadBugs()
}

/** 修改每页条数后回到第一页，避免旧页码在新分页规模下越界。 */
async function handlePageSizeChange(pageSize: number): Promise<void> {
  bugStore.query.page = 1
  bugStore.query.pageSize = pageSize
  await loadBugs()
}

/**
 * 在当前列表路由打开右侧详情抽屉。
 * Bug 主键写入查询参数，既保留列表筛选状态，也支持刷新和浏览器前进后退。
 */
function openBug(bugId: number): void {
  void router.push({
    name: route.name ?? 'bug-list',
    params: route.params,
    query: { ...route.query, bugId: String(bugId) },
  })
}

/** 关闭详情抽屉时只移除 bugId，其他列表查询参数保持不变。 */
function closeBug(): void {
  const query = { ...route.query }
  delete query.bugId
  void router.replace({ name: route.name ?? 'bug-list', params: route.params, query })
}

/** 从抽屉切换到原有独立详情页，供需要更宽编辑空间的场景使用。 */
function openBugStandalone(): void {
  if (!selectedBugId.value) return
  void router.push({
    name: 'bug-detail',
    params: { workspaceId: workspaceId.value, bugId: selectedBugId.value },
  })
}

/**
 * 标记所有数据行并高亮当前抽屉对应行。
 * 通用行标记同时供页面外部点击判断使用：切换另一条 Bug 时不应先关闭抽屉，而应直接刷新详情。
 */
function tableRowClassName({ row }: { row: { id: number } }): string {
  return row.id === selectedBugId.value
    ? 'bug-list__data-row bug-list__row--selected'
    : 'bug-list__data-row'
}

/**
 * 点击抽屉外的列表页面时收起详情；点击任一列表行例外，交由行点击直接切换到新的 Bug。
 * 抽屉使用非模态模式以保留 Plane 式主从浏览，因此需要在页面层补足“点击外部关闭”的交互。
 */
function handleListPageClick(event: MouseEvent): void {
  if (!detailDrawerVisible.value || !(event.target instanceof Element)) return
  // Drawer 默认不一定 Teleport 到 body；因此必须先排除详情自身，避免编辑、评论等内部点击误触关闭。
  if (event.target.closest('.bug-detail-drawer')) return
  // Element Plus 的行点击在冒泡阶段触发。捕获阶段先识别原生行，才能避免先执行关闭再打开的竞态。
  if (event.target.closest('.bug-list__data-row, .el-table__row')) return
  closeBug()
}

/** 进入当前工作空间的新建 Bug 页面。 */
function createBug(): void {
  void router.push({ name: 'bug-create', params: { workspaceId: workspaceId.value } })
}

/** 用户为空时以短横线展示，和列表中未指派状态保持一致。 */
function personName(user: { displayName: string; username?: string } | null): string {
  return user?.displayName || user?.username || '-'
}

/** 头像只显示首个字符，长姓名不会撑开表格列。 */
function personInitial(user: { displayName: string; username?: string } | null): string {
  return personName(user).slice(0, 1).toUpperCase()
}

/** 按用户主键稳定分配头像颜色，同一用户在不同列表行中保持一致。 */
function avatarTone(user: { id: number } | null): string {
  if (!user) return ''
  return `person-avatar--tone-${Math.abs(user.id) % 5}`
}
</script>

<template>
  <main class="bug-list" @click.capture="handleListPageClick">
    <header class="bug-list__page-heading">
      <div>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageSubtitle }} · 共 {{ bugStore.total }} 条</p>
      </div>
      <el-button v-if="canCreate" type="primary" @click="createBug">
        <AppIcon name="plus" :size="18" />
        新建 Bug
      </el-button>
    </header>

    <el-alert
      v-if="errorMessage"
      class="bug-list__alert"
      :title="errorMessage"
      type="error"
      :closable="true"
      show-icon
      @close="errorMessage = ''"
    />

    <el-card class="bug-list__card bug-list__filter-panel" shadow="never">
      <div class="bug-list__filters">
        <el-input
          v-model="bugStore.query.keyword"
          class="filter-item filter-item--keyword"
          placeholder="搜索编号或标题"
          clearable
          @keyup.enter="applyFilters"
          @clear="applyFilters"
        >
          <template #prefix><AppIcon name="search" :size="17" /></template>
        </el-input>
        <el-select
          v-if="isFilterVisible('status')"
          v-model="bugStore.query.status"
          class="filter-item filter-item--status"
          placeholder="状态"
          clearable
          @change="applyFilters"
        >
          <el-option
            v-for="option in BUG_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-if="isFilterVisible('priority')"
          v-model="bugStore.query.priority"
          class="filter-item filter-item--priority"
          placeholder="优先级"
          clearable
          @change="applyFilters"
        >
          <el-option
            v-for="option in BUG_PRIORITY_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-if="isFilterVisible('assignee')"
          v-model="bugStore.query.assigneeId"
          class="filter-item filter-item--assignee"
          placeholder="负责人"
          clearable
          filterable
          @change="applyFilters"
        >
          <el-option
            v-for="member in workspaceStore.members"
            :key="member.userId"
            :label="member.displayName"
            :value="member.userId"
          />
        </el-select>
        <el-select
          v-if="isFilterVisible('creator')"
          v-model="bugStore.query.creatorId"
          class="filter-item filter-item--creator"
          placeholder="提交人"
          clearable
          filterable
          @change="applyFilters"
        >
          <el-option
            v-for="member in workspaceStore.members"
            :key="member.userId"
            :label="member.displayName"
            :value="member.userId"
          />
        </el-select>
        <el-select
          v-if="isFilterVisible('acceptor')"
          v-model="bugStore.query.acceptorId"
          class="filter-item filter-item--acceptor"
          placeholder="验收人"
          clearable
          filterable
          @change="applyFilters"
        >
          <el-option
            v-for="member in workspaceStore.members"
            :key="member.userId"
            :label="member.displayName"
            :value="member.userId"
          />
        </el-select>
        <el-date-picker
          v-if="isFilterVisible('date')"
          :model-value="dateRange"
          class="filter-item filter-item--date"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          range-separator="→"
          @update:model-value="handleDateChange"
        />
        <el-popover placement="bottom-start" :width="250" trigger="click">
          <template #reference>
            <el-button class="filter-customize" plain>
              <AppIcon name="filter" :size="16" />
              筛选条件
            </el-button>
          </template>
          <div class="filter-customize__popover">
            <strong>显示筛选条件</strong>
            <el-checkbox-group
              :model-value="selectedOptionalFilters"
              @update:model-value="handleVisibleFiltersChange"
            >
              <el-checkbox
                v-for="option in OPTIONAL_FILTER_OPTIONS"
                :key="option.key"
                :value="option.key"
              >
                {{ option.label }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </el-popover>
        <el-button v-if="hasActiveFilters" class="filter-reset" @click="resetFilters">
          重置
        </el-button>
      </div>
    </el-card>

    <el-card class="bug-list__card bug-list__table-panel" shadow="never">
      <div class="bug-list__table-scroll">
        <el-table
          v-loading="bugStore.loading"
          :data="bugStore.list"
          row-key="id"
          class="bug-list__table"
          :row-class-name="tableRowClassName"
          @row-click="(row: { id: number }) => openBug(row.id)"
        >
          <el-table-column type="selection" width="44" />
          <el-table-column prop="bugNo" label="编号" width="120" />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="优先级" width="104">
            <template #default="{ row }">
              <span
                class="priority-pill"
                :class="`priority-pill--${String(row.priority).toLowerCase()}`"
              >
                <span class="priority-pill__bars" aria-hidden="true"><i /><i /><i /></span>
                {{ PRIORITY_META[row.priority as keyof typeof PRIORITY_META].label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <span class="status-pill" :class="`status-pill--${String(row.status).toLowerCase()}`">
                <i aria-hidden="true" />
                {{ STATUS_META[row.status as keyof typeof STATUS_META].label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="负责人" width="122">
            <template #default="{ row }">
              <span v-if="row.assignee" class="person-cell">
                <i class="person-avatar" :class="avatarTone(row.assignee)">{{
                  personInitial(row.assignee)
                }}</i>
                <span>{{ personName(row.assignee) }}</span>
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="提交人" width="122">
            <template #default="{ row }">
              <span v-if="row.creator" class="person-cell">
                <i class="person-avatar" :class="avatarTone(row.creator)">{{
                  personInitial(row.creator)
                }}</i>
                <span>{{ personName(row.creator) }}</span>
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="验收人" width="122">
            <template #default="{ row }">
              <span v-if="row.acceptor" class="person-cell">
                <i class="person-avatar" :class="avatarTone(row.acceptor)">{{
                  personInitial(row.acceptor)
                }}</i>
                <span>{{ personName(row.acceptor) }}</span>
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="160" sortable prop="createdAt">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column width="54" align="center">
            <template #default="{ row }">
              <button
                class="row-more"
                type="button"
                aria-label="查看 Bug 详情"
                @click.stop="openBug(row.id)"
              >
                <span /><span /><span />
              </button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="当前条件下没有 Bug">
              <el-button v-if="canCreate" type="primary" @click="createBug"
                >创建第一个 Bug</el-button
              >
            </el-empty>
          </template>
        </el-table>
      </div>

      <footer class="bug-list__footer">
        <div class="bug-list__count">
          <span>共 {{ bugStore.total }} 条，每页</span>
          <el-select
            :model-value="bugStore.query.pageSize"
            class="page-size-select"
            aria-label="每页条数"
            @change="handlePageSizeChange"
          >
            <el-option :value="20" label="20 条" />
            <el-option :value="50" label="50 条" />
            <el-option :value="100" label="100 条" />
          </el-select>
        </div>
        <el-pagination
          class="bug-list__pagination"
          layout="prev, pager, next"
          :total="bugStore.total"
          :page-size="bugStore.query.pageSize"
          :current-page="bugStore.query.page"
          :hide-on-single-page="false"
          @current-change="handlePageChange"
        />
      </footer>
    </el-card>

    <el-drawer
      v-model="detailDrawerVisible"
      class="bug-detail-drawer"
      direction="rtl"
      size="min(900px, calc(100vw - 300px))"
      :with-header="false"
      :modal="false"
      :lock-scroll="false"
      :destroy-on-close="true"
      :z-index="40"
      @close="closeBug"
    >
      <section class="bug-detail-drawer__shell" aria-label="Bug 详情抽屉">
        <header class="bug-detail-drawer__toolbar">
          <button type="button" aria-label="关闭详情" @click="closeBug">
            <AppIcon name="arrow-right" :size="19" />
          </button>
          <span class="bug-detail-drawer__divider" />
          <strong>Bug 详情</strong>
          <button
            class="bug-detail-drawer__standalone"
            type="button"
            aria-label="在独立页面打开"
            title="在独立页面打开"
            @click="openBugStandalone"
          >
            <AppIcon name="external-link" :size="18" />
          </button>
        </header>
        <div class="bug-detail-drawer__content">
          <BugDetailView
            v-if="selectedBugId"
            :key="selectedBugId"
            :bug-id="selectedBugId"
            drawer-mode
            @close="closeBug"
          />
        </div>
      </section>
    </el-drawer>
  </main>
</template>

<style scoped>
.bug-list {
  width: 100%;
  max-width: 1390px;
  margin: 0 auto;
}

.bug-list__page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 26px;
}

.bug-list__page-heading h1 {
  margin: 0 0 8px;
  color: var(--bl-text);
  font-size: 31px;
  font-weight: 730;
  letter-spacing: -0.7px;
  line-height: 1.2;
}

.bug-list__page-heading p {
  margin: 0;
  color: var(--bl-text-secondary);
  font-size: 14px;
  line-height: 1.5;
}

.bug-list__page-heading :deep(.el-button) {
  min-width: 138px;
  min-height: 48px;
  padding: 0 20px;
  border-radius: 9px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 8px 24px rgb(25 117 238 / 24%);
}

.bug-list__alert {
  margin-bottom: 16px;
}

.bug-list__card {
  border: 1px solid var(--bl-border);
  border-radius: 11px;
}

.bug-list__card :deep(.el-card__body) {
  padding: 0;
}

.bug-list__filter-panel {
  margin-bottom: 20px;
}

.bug-list__filter-panel :deep(.el-card__body) {
  padding: 16px 18px;
}

.bug-list__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.filter-item {
  flex: 0 1 136px;
  width: 136px;
}

.filter-item--keyword {
  flex: 1 1 220px;
  max-width: 280px;
}

.filter-item--date {
  flex-basis: 292px;
  width: 292px !important;
}

.filter-reset {
  min-width: 70px;
}

.bug-list__filters :deep(.el-input__wrapper),
.bug-list__filters :deep(.el-select__wrapper),
.bug-list__filters :deep(.el-date-editor.el-input__wrapper) {
  min-height: 38px;
  padding: 0 11px;
  border-radius: 8px;
}

.bug-list__filters :deep(.el-input__prefix) {
  color: #87a2c2;
}

.bug-list__filters :deep(.el-range__icon) {
  color: #8ba6c4;
}

.bug-list__filters :deep(.el-range-separator) {
  color: var(--bl-muted);
}

.filter-reset.el-button {
  min-height: 38px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 13px;
}

.filter-customize.el-button {
  min-height: 38px;
  padding: 0 13px;
  color: var(--bl-text-secondary);
  border-color: var(--bl-border);
  border-radius: 8px;
  font-size: 13px;
}

.filter-customize.el-button:hover {
  color: var(--bl-primary);
  border-color: color-mix(in srgb, var(--bl-primary) 45%, var(--bl-border));
}

.filter-customize__popover {
  padding: 2px;
}

.filter-customize__popover strong {
  display: block;
  margin: 3px 4px 9px;
  color: var(--bl-text);
  font-size: 13px;
}

.filter-customize__popover :deep(.el-checkbox-group) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 4px;
}

.filter-customize__popover :deep(.el-checkbox) {
  height: 26px;
  margin-right: 0;
  color: var(--bl-text-secondary);
  font-size: 13px;
}

.bug-list__table-panel :deep(.el-card__body) {
  padding: 18px 14px 16px;
}

.bug-list__table-scroll {
  overflow-x: auto;
}

.bug-list__table {
  min-width: 1180px;
  --el-table-border-color: var(--bl-border);
}

.bug-list__table :deep(.el-table__row) {
  cursor: pointer;
}

/* 使用主题变量覆盖组件库按需注入的默认行底色，避免深色模式回退为白底。 */
.bug-list__table :deep(.el-table__body tr),
.bug-list__table :deep(.el-table__body td.el-table__cell) {
  background-color: var(--el-table-tr-bg-color);
}

.bug-list__table :deep(.el-table__body tr:nth-child(even) td.el-table__cell) {
  background: rgb(47 76 109 / 20%);
}

.bug-list__table :deep(.el-table__row:hover > td.el-table__cell) {
  background: var(--el-table-row-hover-bg-color);
}

.bug-list__table :deep(.bug-list__row--selected > td.el-table__cell) {
  background: rgb(34 112 205 / 22%) !important;
}

.bug-list__table :deep(.el-table__header-wrapper th) {
  height: 48px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 600;
  background: var(--el-table-header-bg-color);
}

.bug-list__table :deep(.el-table__header-wrapper th:first-child) {
  border-radius: 7px 0 0 7px;
}

.bug-list__table :deep(.el-table__header-wrapper th:last-child) {
  border-radius: 0 7px 7px 0;
}

.bug-list__table :deep(.el-table__cell) {
  height: 62px;
  padding: 0;
  font-size: 13px;
}

.bug-list__table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.bug-list__table :deep(.el-checkbox__inner) {
  width: 17px;
  height: 17px;
  background: transparent;
  border-color: #6883a0;
  border-radius: 4px;
}

.priority-pill,
.status-pill {
  display: inline-flex;
  height: 28px;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  border-radius: 13px;
  white-space: nowrap;
}

.priority-pill__bars {
  display: flex;
  height: 12px;
  align-items: flex-end;
  gap: 2px;
}

.priority-pill__bars i {
  display: block;
  width: 2px;
  background: currentcolor;
  border-radius: 2px;
}

.priority-pill__bars i:nth-child(1) {
  height: 5px;
}

.priority-pill__bars i:nth-child(2) {
  height: 8px;
}

.priority-pill__bars i:nth-child(3) {
  height: 11px;
}

.priority-pill--p0 {
  color: #ff625d;
  background: rgb(121 35 39 / 42%);
}

.priority-pill--p1 {
  color: #f3a52f;
  background: rgb(106 72 30 / 43%);
}

.priority-pill--p2 {
  color: #529eff;
  background: rgb(29 77 137 / 43%);
}

.priority-pill--p3 {
  color: #38cd82;
  background: rgb(24 92 65 / 43%);
}

.status-pill {
  color: #aebdd0;
  background: rgb(40 56 73 / 60%);
}

.status-pill i {
  width: 9px;
  height: 9px;
  border: 2px solid currentcolor;
  border-radius: 50%;
}

.status-pill--processing {
  color: #4b9cff;
  background: rgb(27 69 124 / 55%);
}

.status-pill--wait_acceptance {
  color: #efbd3f;
  background: rgb(96 73 25 / 48%);
}

.status-pill--reopened {
  color: #9b82ff;
  background: rgb(67 49 125 / 52%);
}

.status-pill--closed {
  color: #37ce84;
  background: rgb(19 89 62 / 52%);
}

.status-pill--closed i {
  background: currentcolor;
  border-color: currentcolor;
  box-shadow: inset 0 0 0 2px rgb(17 62 47 / 75%);
}

.person-cell {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.person-avatar {
  display: grid;
  width: 27px;
  height: 27px;
  flex: 0 0 auto;
  place-items: center;
  color: white;
  font-size: 12px;
  font-style: normal;
  background: linear-gradient(145deg, #2377ec, #3199ff);
  border: 1px solid rgb(255 255 255 / 13%);
  border-radius: 50%;
}

.person-avatar--tone-1 {
  background: linear-gradient(145deg, #6947e5, #8b6cff);
}

.person-avatar--tone-2 {
  background: linear-gradient(145deg, #11a760, #43d381);
}

.person-avatar--tone-3 {
  background: linear-gradient(145deg, #de497c, #ff72a2);
}

.person-avatar--tone-4 {
  background: linear-gradient(145deg, #3898c9, #63c7f6);
}

.row-more {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: var(--bl-text-secondary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.row-more:hover {
  color: var(--bl-text);
  background: var(--bl-control-hover);
}

.row-more span {
  width: 3px;
  height: 3px;
  background: currentcolor;
  border-radius: 50%;
}

.bug-list__footer {
  display: flex;
  min-height: 60px;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 4px 0;
}

.bug-list__count {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--bl-text-secondary);
  font-size: 13px;
}

.page-size-select {
  width: 88px;
}

.page-size-select :deep(.el-select__wrapper) {
  min-height: 34px;
  background: transparent !important;
  border: 0;
  box-shadow: none !important;
}

.bug-list__pagination {
  justify-content: flex-end;
}

.bug-list__pagination :deep(.btn-prev),
.bug-list__pagination :deep(.btn-next),
.bug-list__pagination :deep(.el-pager li) {
  min-width: 36px;
  height: 36px;
  border: 1px solid var(--bl-border);
  border-radius: 8px;
}

.bug-list__pagination :deep(.el-pager li.is-active) {
  color: white;
  background: linear-gradient(145deg, #1680f7, #247bff);
  border-color: #318eff;
  box-shadow: 0 5px 14px rgb(28 124 242 / 25%);
}

/* 详情抽屉从全局顶栏下方展开，列表保持可见，形成 Plane 式主从浏览体验。 */
:global(.bug-detail-drawer.el-drawer) {
  top: 62px;
  height: calc(100vh - 62px);
  color: var(--bl-text);
  background: var(--bl-panel);
  border-left: 1px solid var(--bl-border-strong);
  box-shadow: -18px 0 48px rgb(0 0 0 / 32%);
}

:global(.bug-detail-drawer .el-drawer__body) {
  padding: 0;
  overflow: hidden;
}

.bug-detail-drawer__shell {
  display: flex;
  height: 100%;
  flex-direction: column;
}

.bug-detail-drawer__toolbar {
  display: flex;
  height: 56px;
  flex: 0 0 56px;
  align-items: center;
  gap: 11px;
  padding: 0 18px;
  border-bottom: 1px solid var(--bl-border);
}

.bug-detail-drawer__toolbar button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: var(--bl-text-secondary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 7px;
}

.bug-detail-drawer__toolbar button:hover {
  color: var(--bl-text);
  background: var(--bl-control-hover);
}

.bug-detail-drawer__toolbar strong {
  color: var(--bl-text);
  font-size: 13px;
  font-weight: 600;
}

.bug-detail-drawer__divider {
  width: 1px;
  height: 22px;
  background: var(--bl-border);
}

.bug-detail-drawer__standalone {
  margin-left: auto;
}

.bug-detail-drawer__content {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
}

:global(:root[data-theme='light'])
  .bug-list__table
  :deep(.el-table__body tr:nth-child(even) td.el-table__cell) {
  background: #f5f8fc;
}

:global(:root[data-theme='light'])
  .bug-list__table
  :deep(.bug-list__row--selected > td.el-table__cell) {
  background: #e6f2ff !important;
}

:global(:root[data-theme='light']) .priority-pill--p0 {
  color: #d94743;
  background: #fdeceb;
}

:global(:root[data-theme='light']) .priority-pill--p1 {
  color: #bd7b0c;
  background: #fdf3dc;
}

:global(:root[data-theme='light']) .priority-pill--p2 {
  color: #1677e8;
  background: #e9f3ff;
}

:global(:root[data-theme='light']) .priority-pill--p3 {
  color: #188a50;
  background: #e7f7ef;
}

:global(:root[data-theme='light']) .status-pill {
  color: #697b90;
  background: #edf2f7;
}

:global(:root[data-theme='light']) .status-pill--processing {
  color: #1677e8;
  background: #e9f3ff;
}

:global(:root[data-theme='light']) .status-pill--wait_acceptance {
  color: #ae7809;
  background: #fdf5df;
}

:global(:root[data-theme='light']) .status-pill--reopened {
  color: #704ec8;
  background: #f0ebff;
}

:global(:root[data-theme='light']) .status-pill--closed {
  color: #16834b;
  background: #e6f7ee;
}

@media (max-width: 1320px) {
  .filter-item--keyword {
    max-width: none;
  }
}

@media (max-width: 760px) {
  .bug-list__page-heading {
    flex-direction: column;
  }

  .bug-list__page-heading :deep(.el-button) {
    width: 100%;
  }

  .bug-list__filters {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-item,
  .filter-item--date {
    width: 100%;
  }

  .filter-reset {
    width: 100%;
  }

  .filter-customize.el-button {
    width: 100%;
  }

  .bug-list__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  :global(.bug-detail-drawer.el-drawer) {
    top: 0;
    width: 100% !important;
    height: 100vh;
  }
}
</style>
