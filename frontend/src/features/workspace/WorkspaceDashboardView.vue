<!-- 本文件实现工作空间首页概览，使用现有 Bug 接口聚合状态数据、最近更新和空间摘要，并为活动流预留展示区域。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppIcon from '@/shared/components/AppIcon.vue'
import { useAuthStore } from '@/features/auth/authStore'
import { fetchBugs } from '@/features/bug/bugApi'
import type { BugStatus, BugSummary } from '@/features/bug/bugApi'
import { PRIORITY_META, STATUS_META, formatDateTime } from '@/features/bug/bugMeta'
import { useBugStore } from '@/features/bug/bugStore'
import { isApiError } from '@/shared/api/types'
import { useWorkspaceStore } from './workspaceStore'

interface DashboardMetric {
  key: 'pending' | 'processing' | 'acceptance' | 'closed'
  label: string
  value: number
  tone: 'red' | 'blue' | 'yellow' | 'green'
  icon: 'bugs' | 'acceptance'
  status: BugStatus
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const bugStore = useBugStore()
const workspaceStore = useWorkspaceStore()

const loading = ref(true)
const errorMessage = ref('')
const recentBugs = ref<BugSummary[]>([])
const totals = ref<Record<BugStatus, number>>({
  TODO: 0,
  PROCESSING: 0,
  WAIT_ACCEPTANCE: 0,
  REOPENED: 0,
  CLOSED: 0,
})

const workspaceId = computed(() => Number(route.params.workspaceId))
const workspace = computed(() => workspaceStore.currentWorkspace)
const canCreate = computed(() => workspaceStore.isEnabled)
const canManageMembers = computed(() => {
  const role = workspace.value?.currentUserRole
  return auth.user?.systemRole === 'SYSTEM_ADMIN' || role === 'OWNER' || role === 'ADMIN'
})
const openBugCount = computed(
  () =>
    totals.value.TODO +
    totals.value.PROCESSING +
    totals.value.WAIT_ACCEPTANCE +
    totals.value.REOPENED,
)

const metrics = computed<DashboardMetric[]>(() => [
  {
    key: 'pending',
    label: '待处理',
    value: totals.value.TODO + totals.value.REOPENED,
    tone: 'red',
    icon: 'bugs',
    status: 'TODO',
  },
  {
    key: 'processing',
    label: '处理中',
    value: totals.value.PROCESSING,
    tone: 'blue',
    icon: 'bugs',
    status: 'PROCESSING',
  },
  {
    key: 'acceptance',
    label: '待验收',
    value: totals.value.WAIT_ACCEPTANCE,
    tone: 'yellow',
    icon: 'acceptance',
    status: 'WAIT_ACCEPTANCE',
  },
  {
    key: 'closed',
    label: '已关闭',
    value: totals.value.CLOSED,
    tone: 'green',
    icon: 'acceptance',
    status: 'CLOSED',
  },
])

onMounted(() => {
  void loadDashboard()
})

/** 并行读取各状态总数和最近更新，首页只消费现有列表接口，不引入临时统计协议。 */
async function loadDashboard(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const statuses: BugStatus[] = ['TODO', 'PROCESSING', 'WAIT_ACCEPTANCE', 'REOPENED', 'CLOSED']
    const [recent, ...statusPages] = await Promise.all([
      fetchBugs(workspaceId.value, { page: 1, pageSize: 5 }),
      ...statuses.map((status) => fetchBugs(workspaceId.value, { page: 1, pageSize: 1, status })),
    ])
    recentBugs.value = recent.records
    statuses.forEach((status, index) => {
      totals.value[status] = statusPages[index]?.total ?? 0
    })
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载工作空间概览失败'
  } finally {
    loading.value = false
  }
}

/** 状态卡跳转到列表并应用对应筛选；“待处理”先进入 TODO，重新打开仍可在状态筛选中选择。 */
function openMetric(metric: DashboardMetric): void {
  bugStore.resetQuery()
  bugStore.query.status = metric.status
  void router.push({ name: 'bug-list', params: { workspaceId: workspaceId.value } })
}

/** 最近 Bug 行跳转到详情页。 */
function openBug(bugId: number): void {
  void router.push({ name: 'bug-detail', params: { workspaceId: workspaceId.value, bugId } })
}

/** 进入创建页，停用空间不会展示入口。 */
function createBug(): void {
  void router.push({ name: 'bug-create', params: { workspaceId: workspaceId.value } })
}

/** 打开成员页，复用已实现的成员邀请和角色维护能力。 */
function inviteMember(): void {
  void router.push({ name: 'workspace-members', params: { workspaceId: workspaceId.value } })
}

/** 将角色枚举转换为工作空间信息区的中文标签。 */
function roleLabel(role: string | null | undefined): string {
  if (role === 'OWNER') return '负责人'
  if (role === 'ADMIN') return '管理员'
  return '成员'
}

/** 用更新时间生成轻量相对时间，便于活动区域快速扫描。 */
function relativeTime(value: string): string {
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) {
    return formatDateTime(value)
  }
  const minutes = Math.max(0, Math.floor((Date.now() - time) / 60_000))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  return days < 30 ? `${days} 天前` : formatDateTime(value)
}

/** 从显示名称生成稳定头像文字，不依赖后端头像字段。 */
function avatarText(bug: BugSummary): string {
  return (bug.assignee?.displayName || bug.creator?.displayName || 'B').slice(0, 1)
}
</script>

<template>
  <main class="dashboard">
    <div v-if="errorMessage" class="page-alert">
      <span>{{ errorMessage }}</span>
      <button type="button" @click="loadDashboard">重新加载</button>
    </div>

    <header class="dashboard-hero">
      <div>
        <h1>工作空间概览</h1>
        <p>
          这里是 {{ workspace?.name || '当前' }} 的工作空间，随时掌握项目进展，提升团队协作效率。
        </p>
      </div>
      <div class="dashboard-hero__actions">
        <button
          v-if="canManageMembers"
          type="button"
          class="button button--secondary"
          @click="inviteMember"
        >
          <AppIcon name="users" :size="18" />
          邀请成员
        </button>
        <button v-if="canCreate" type="button" class="button button--primary" @click="createBug">
          <AppIcon name="plus" :size="19" />
          新建 Bug
        </button>
      </div>
    </header>

    <section class="metric-grid" aria-label="Bug 状态概览">
      <button
        v-for="metric in metrics"
        :key="metric.key"
        type="button"
        class="metric-card"
        :class="`metric-card--${metric.tone}`"
        @click="openMetric(metric)"
      >
        <span class="metric-card__icon"><AppIcon :name="metric.icon" :size="21" /></span>
        <span class="metric-card__body">
          <span class="metric-card__label">{{ metric.label }}</span>
          <strong>{{ loading ? '—' : metric.value }}</strong>
          <small>{{ metric.key === 'pending' ? '含重新打开' : '当前工作空间' }}</small>
        </span>
        <span class="metric-card__spark" aria-hidden="true"><i /><i /><i /><i /><i /></span>
      </button>
    </section>

    <div class="dashboard-grid">
      <div class="dashboard-grid__primary">
        <section class="dashboard-panel recent-panel">
          <header class="panel-header">
            <h2>最近 Bug</h2>
            <button
              type="button"
              @click="router.push({ name: 'bug-list', params: { workspaceId } })"
            >
              查看全部 <AppIcon name="arrow-right" :size="15" />
            </button>
          </header>

          <div class="recent-table" :class="{ 'recent-table--loading': loading }">
            <div class="recent-table__row recent-table__head">
              <span>Bug 编号</span><span>标题</span><span>优先级</span><span>状态</span
              ><span>负责人</span><span>更新时间</span>
            </div>
            <button
              v-for="item in recentBugs"
              :key="item.id"
              type="button"
              class="recent-table__row"
              @click="openBug(item.id)"
            >
              <span class="recent-table__number">{{ item.bugNo }}</span>
              <span class="recent-table__title">{{ item.title }}</span>
              <span
                ><i class="pill" :class="`pill--${item.priority.toLowerCase()}`">{{
                  PRIORITY_META[item.priority].label
                }}</i></span
              >
              <span
                ><i class="status-pill" :class="`status-pill--${item.status.toLowerCase()}`">{{
                  STATUS_META[item.status].label
                }}</i></span
              >
              <span class="recent-table__person">
                <i>{{ (item.assignee?.displayName || '未').slice(0, 1) }}</i>
                {{ item.assignee?.displayName || '未指定' }}
              </span>
              <span class="recent-table__time">{{ formatDateTime(item.updatedAt) }}</span>
            </button>
            <div v-if="!loading && recentBugs.length === 0" class="recent-table__empty">
              还没有 Bug，创建第一条记录开始协作。
            </div>
          </div>
        </section>

        <section class="dashboard-panel workspace-panel">
          <header class="panel-header">
            <h2>工作空间信息</h2>
            <button
              type="button"
              @click="router.push({ name: 'workspace-settings', params: { workspaceId } })"
            >
              <AppIcon name="edit" :size="15" /> 编辑设置
            </button>
          </header>
          <div class="workspace-panel__content">
            <div class="workspace-identity">
              <span class="workspace-identity__logo">{{
                (workspace?.name || 'W').slice(0, 1).toUpperCase()
              }}</span>
              <div>
                <strong>{{ workspace?.name }}</strong>
                <p>{{ workspace?.description || '专注问题闭环，让每一个 Bug 都被看见并解决。' }}</p>
              </div>
            </div>
            <dl class="workspace-facts">
              <div>
                <dt><AppIcon name="users" :size="17" /> {{ workspaceStore.members.length }}</dt>
                <dd>成员数量</dd>
              </div>
              <div>
                <dt><AppIcon name="bugs" :size="17" /> {{ openBugCount }}</dt>
                <dd>未关闭 Bug</dd>
              </div>
              <div>
                <dt>
                  <AppIcon name="settings" :size="17" /> {{ roleLabel(workspace?.currentUserRole) }}
                </dt>
                <dd>我的角色</dd>
              </div>
              <div>
                <dt>{{ formatDateTime(workspace?.createdAt).slice(0, 10) }}</dt>
                <dd>创建日期</dd>
              </div>
            </dl>
            <div class="workspace-panel__motto">
              <strong>让每一个 Bug<br />都被看见并解决</strong>
              <p>高效协作 · 持续改进 · 更好的产品</p>
            </div>
          </div>
        </section>
      </div>

      <section class="dashboard-panel activity-panel">
        <header class="panel-header">
          <h2>最近活动</h2>
          <span>基于最近更新</span>
        </header>
        <div v-if="recentBugs.length" class="activity-list">
          <button
            v-for="item in recentBugs"
            :key="item.id"
            type="button"
            class="activity-item"
            @click="openBug(item.id)"
          >
            <span class="activity-item__dot" />
            <span class="activity-item__avatar">{{ avatarText(item) }}</span>
            <span class="activity-item__content">
              <span
                ><strong>{{
                  item.assignee?.displayName || item.creator?.displayName || '工作空间成员'
                }}</strong>
                更新了 Bug</span
              >
              <small>{{ item.bugNo }} · {{ item.title }}</small>
            </span>
            <time>{{ relativeTime(item.updatedAt) }}</time>
          </button>
        </div>
        <div v-else-if="!loading" class="activity-empty">
          <span><AppIcon name="bugs" :size="24" /></span>
          <strong>等待第一条活动</strong>
          <p>创建或更新 Bug 后，这里会展示最新动态。</p>
        </div>
        <div class="activity-preview">
          <span>操作日志与评论动态</span>
          <em>接口接入后自动替换为完整活动流</em>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.dashboard {
  max-width: 1440px;
  margin: 0 auto;
}

.page-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
  padding: 11px 14px;
  color: #ffaaa5;
  font-size: 13px;
  background: rgb(117 34 38 / 25%);
  border: 1px solid rgb(226 76 76 / 30%);
  border-radius: 8px;
}

.page-alert button {
  color: #ffb0ab;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.dashboard-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 25px;
}

.dashboard-hero h1 {
  margin: 0 0 7px;
  color: #f2f6fb;
  font-size: clamp(24px, 2.2vw, 31px);
  line-height: 1.2;
  letter-spacing: -0.6px;
}

.dashboard-hero p {
  margin: 0;
  color: var(--bl-text-secondary);
  font-size: 14px;
}

.dashboard-hero__actions {
  display: flex;
  gap: 12px;
}

.button {
  display: inline-flex;
  height: 45px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 0 21px;
  color: #e8eef6;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid #303a46;
  border-radius: 8px;
}

.button--secondary {
  background: linear-gradient(#1b222b, #181f27);
}

.button--primary {
  color: white;
  background: linear-gradient(135deg, #1680f7, #247bff);
  border-color: #2f8cff;
  box-shadow: 0 8px 22px rgb(25 117 238 / 20%);
}

.button:hover {
  filter: brightness(1.1);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.metric-card {
  position: relative;
  display: flex;
  min-height: 129px;
  align-items: flex-start;
  gap: 16px;
  overflow: hidden;
  padding: 19px 17px;
  color: var(--bl-text);
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: linear-gradient(145deg, #171e26, #141a21);
  border: 1px solid #29323d;
  border-radius: 9px;
  box-shadow: 0 9px 27px rgb(0 0 0 / 10%);
}

.metric-card:hover {
  border-color: #3a4653;
  transform: translateY(-1px);
}

.metric-card__icon {
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
}

.metric-card--red .metric-card__icon {
  color: #ff625a;
  background: #482522;
  box-shadow: 0 0 0 8px rgb(169 52 45 / 12%);
}
.metric-card--blue .metric-card__icon {
  color: #4698ff;
  background: #183a68;
  box-shadow: 0 0 0 8px rgb(35 97 190 / 12%);
}
.metric-card--yellow .metric-card__icon {
  color: #ffca27;
  background: #514415;
  box-shadow: 0 0 0 8px rgb(174 139 13 / 11%);
}
.metric-card--green .metric-card__icon {
  color: #3bd77c;
  background: #153e2b;
  box-shadow: 0 0 0 8px rgb(25 138 73 / 11%);
}

.metric-card__body {
  display: grid;
  gap: 3px;
}

.metric-card__label {
  color: #aeb8c6;
  font-size: 14px;
}

.metric-card strong {
  margin-top: 2px;
  color: #f4f7fb;
  font-size: 30px;
  font-weight: 650;
  line-height: 1;
}

.metric-card small {
  margin-top: 4px;
  color: #6f7d8e;
  font-size: 11px;
}

.metric-card__spark {
  position: absolute;
  right: 18px;
  bottom: 29px;
  display: flex;
  height: 30px;
  align-items: flex-end;
  gap: 3px;
  opacity: 0.8;
}

.metric-card__spark i {
  display: block;
  width: 4px;
  height: 8px;
  background: currentColor;
  border-radius: 3px;
}
.metric-card__spark i:nth-child(2) {
  height: 14px;
}
.metric-card__spark i:nth-child(3) {
  height: 11px;
}
.metric-card__spark i:nth-child(4) {
  height: 21px;
}
.metric-card__spark i:nth-child(5) {
  height: 27px;
}
.metric-card--red .metric-card__spark {
  color: #ed4e47;
}
.metric-card--blue .metric-card__spark {
  color: #3d86e9;
}
.metric-card--yellow .metric-card__spark {
  color: #e7b61b;
}
.metric-card--green .metric-card__spark {
  color: #27aa63;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 2.3fr) minmax(310px, 1fr);
  gap: 17px;
}

.dashboard-grid__primary {
  display: grid;
  align-content: start;
  gap: 17px;
  min-width: 0;
}

.dashboard-panel {
  min-width: 0;
  overflow: hidden;
  background: linear-gradient(145deg, #161d25, #141a21);
  border: 1px solid #29323d;
  border-radius: 9px;
  box-shadow: 0 9px 28px rgb(0 0 0 / 10%);
}

.panel-header {
  display: flex;
  min-height: 57px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 22px;
}

.panel-header h2 {
  margin: 0;
  color: #eff4fa;
  font-size: 17px;
}

.panel-header button,
.panel-header > span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #419bff;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.panel-header > span {
  color: #6f7d8d;
  cursor: default;
}

.recent-table {
  margin: 0 22px 17px;
  overflow-x: auto;
}

.recent-table__row {
  display: grid;
  min-width: 780px;
  grid-template-columns: 128px minmax(210px, 1.5fr) 95px 120px 135px 155px;
  align-items: center;
  padding: 0 13px;
  color: #c3cbd6;
  font: inherit;
  font-size: 13px;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #28313a;
}

button.recent-table__row {
  width: 100%;
  min-height: 48px;
  cursor: pointer;
}

button.recent-table__row:hover {
  background: rgb(40 51 63 / 55%);
}

.recent-table__head {
  min-height: 42px;
  color: #8290a1;
  background: #1d252f;
  border: 1px solid #2a3540;
  border-radius: 7px;
}

.recent-table__number {
  color: #55adff;
}

.recent-table__title,
.activity-item__content small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pill,
.status-pill {
  display: inline-flex;
  align-items: center;
  min-width: 30px;
  justify-content: center;
  padding: 4px 9px;
  font-size: 11px;
  font-style: normal;
  border-radius: 7px;
}

.pill--p0,
.pill--p1 {
  color: #ff7971;
  background: #492624;
}
.pill--p2 {
  color: #f0c858;
  background: #453b18;
}
.pill--p3 {
  color: #56d399;
  background: #173c2e;
}
.status-pill--todo,
.status-pill--reopened {
  color: #ff7770;
  background: #462424;
}
.status-pill--processing {
  color: #56a6ff;
  background: #193755;
}
.status-pill--wait_acceptance {
  color: #f3c943;
  background: #453b16;
}
.status-pill--closed {
  color: #52d38b;
  background: #163b2a;
}

.recent-table__person {
  display: flex;
  align-items: center;
  gap: 8px;
}

.recent-table__person i,
.activity-item__avatar {
  display: grid;
  width: 27px;
  height: 27px;
  flex: 0 0 auto;
  place-items: center;
  color: white;
  font-size: 11px;
  font-style: normal;
  background: linear-gradient(145deg, #4d5ee8, #7482f8);
  border-radius: 50%;
}

.recent-table__time {
  color: #8d9bab;
}

.recent-table__empty {
  padding: 44px 16px;
  color: #768597;
  font-size: 13px;
  text-align: center;
}

/* 首页左栏最宽约 990px，三列并排至少需要 1110px，因此固定为两行：
   上行空间标识与标语，下行四个字段整行平分，避免字段被逐列压成竖排。 */
.workspace-panel__content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'identity motto'
    'facts facts';
  align-items: center;
  gap: 16px 22px;
  margin: 0 22px 16px;
  padding: 15px 18px;
  background: rgb(24 32 41 / 50%);
  border: 1px solid #26303a;
  border-radius: 8px;
}

.workspace-facts div:first-child {
  border-left: 0;
  padding-left: 0;
}

.workspace-identity {
  display: flex;
  grid-area: identity;
  min-width: 0;
  align-items: center;
  gap: 14px;
}

/* 说明文字为单行省略，所在列必须允许收缩，否则会溢出到标语块下方造成重叠。 */
.workspace-identity > div {
  min-width: 0;
}

.workspace-identity__logo {
  display: grid;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  place-items: center;
  color: white;
  font-size: 20px;
  background: linear-gradient(145deg, #1f80e9, #23a7dd);
  border: 1px solid #4ab8ee;
  border-radius: 8px;
}

.workspace-identity strong {
  color: #eef4fb;
  font-size: 16px;
}

.workspace-identity p {
  max-width: min(100%, 300px);
  margin: 5px 0 0;
  overflow: hidden;
  color: #7d8b9c;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-facts {
  display: grid;
  grid-area: facts;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding-top: 14px;
  margin: 0;
  border-top: 1px solid #26303a;
}

.workspace-facts div {
  padding: 0 14px;
  border-left: 1px solid #303844;
}

.workspace-facts dt {
  display: flex;
  min-height: 22px;
  align-items: center;
  gap: 8px;
  color: #dce4ee;
  font-size: 13px;
  white-space: nowrap;
}

.workspace-facts dd {
  margin: 4px 0 0;
  color: #758394;
  font-size: 10px;
}

.workspace-panel__motto {
  grid-area: motto;
  padding-left: 28px;
  border-left: 1px solid #303844;
}

.workspace-panel__motto strong {
  color: #e6edf6;
  font-size: 14px;
  line-height: 1.45;
}

.workspace-panel__motto p {
  margin: 7px 0 0;
  color: #768495;
  font-size: 10px;
}

.activity-panel {
  min-height: 100%;
}

.activity-list {
  position: relative;
  display: grid;
  padding: 4px 22px 8px;
}

.activity-list::before {
  position: absolute;
  top: 28px;
  bottom: 28px;
  left: 31px;
  width: 1px;
  content: '';
  background: #34404c;
}

.activity-item {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 13px 35px minmax(0, 1fr) auto;
  align-items: start;
  gap: 10px;
  min-height: 79px;
  padding: 10px 0;
  color: #cbd3de;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.activity-item:hover .activity-item__content > span {
  color: #fff;
}

.activity-item__dot {
  width: 8px;
  height: 8px;
  margin-top: 12px;
  background: #499eff;
  border: 2px solid #17202a;
  border-radius: 50%;
  box-shadow: 0 0 0 2px #499eff;
}

.activity-item__avatar {
  width: 35px;
  height: 35px;
  font-size: 12px;
  background: linear-gradient(145deg, #1ca664, #34c97e);
}

.activity-item:nth-child(even) .activity-item__avatar {
  background: linear-gradient(145deg, #4c58d8, #7c75f1);
}

.activity-item__content {
  display: grid;
  min-width: 0;
  gap: 7px;
  font-size: 12px;
}

.activity-item__content strong {
  color: #eef3f9;
}

.activity-item__content small {
  color: #768496;
  font-size: 11px;
}

.activity-item time {
  margin-top: 3px;
  color: #788697;
  font-size: 10px;
  white-space: nowrap;
}

.activity-preview {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 10px 22px 20px;
  padding: 13px 15px;
  color: #8c9aad;
  font-size: 11px;
  background: #18212a;
  border: 1px dashed #35414e;
  border-radius: 7px;
}

.activity-preview em {
  color: #667587;
  font-style: normal;
}

.activity-empty {
  display: grid;
  min-height: 280px;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: #778596;
  text-align: center;
}

.activity-empty > span {
  display: grid;
  width: 50px;
  height: 50px;
  place-items: center;
  color: #419bff;
  background: #182d43;
  border-radius: 50%;
}

.activity-empty strong {
  color: #aeb9c7;
  font-size: 13px;
}

.activity-empty p {
  margin: 0;
  font-size: 11px;
}

@media (max-width: 1250px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .activity-panel {
    min-height: 420px;
  }
}

@media (max-width: 650px) {
  .dashboard-hero {
    flex-direction: column;
  }

  .dashboard-hero__actions,
  .dashboard-hero__actions .button {
    width: 100%;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .workspace-facts {
    grid-template-columns: repeat(2, 1fr);
    gap: 18px 0;
  }

  .workspace-facts div:nth-child(odd) {
    padding-left: 0;
    border-left: 0;
  }
}
</style>
