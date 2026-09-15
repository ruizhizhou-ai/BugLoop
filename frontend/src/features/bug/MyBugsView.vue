<!-- 本文件实现“我的 Bug”聚合页，将我负责、我提交和待我验收的数据并列展示，补足单一筛选无法表达多角色关系的问题。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppIcon from '@/shared/components/AppIcon.vue'
import { useAuthStore } from '@/features/auth/authStore'
import { isApiError } from '@/shared/api/types'
import { fetchBugs } from './bugApi'
import type { BugSummary } from './bugApi'
import { PRIORITY_META, STATUS_META, formatDateTime } from './bugMeta'

interface PersonalGroup {
  key: 'assigned' | 'submitted' | 'acceptance'
  title: string
  description: string
  routeName: 'assigned-bugs' | 'submitted-bugs' | 'acceptance-bugs'
  records: BugSummary[]
  total: number
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const errorMessage = ref('')
const assigned = ref<BugSummary[]>([])
const submitted = ref<BugSummary[]>([])
const acceptance = ref<BugSummary[]>([])
const assignedTotal = ref(0)
const submittedTotal = ref(0)
const acceptanceTotal = ref(0)

const workspaceId = computed(() => Number(route.params.workspaceId))
const userId = computed(() => auth.user?.id)
const groups = computed<PersonalGroup[]>(() => [
  {
    key: 'assigned',
    title: '指派给我的',
    description: '需要我处理和推进',
    routeName: 'assigned-bugs',
    records: assigned.value,
    total: assignedTotal.value,
  },
  {
    key: 'submitted',
    title: '我提交的',
    description: '由我发现和创建',
    routeName: 'submitted-bugs',
    records: submitted.value,
    total: submittedTotal.value,
  },
  {
    key: 'acceptance',
    title: '待我验收',
    description: '等待我确认修复结果',
    routeName: 'acceptance-bugs',
    records: acceptance.value,
    total: acceptanceTotal.value,
  },
])

onMounted(() => {
  void loadPersonalBugs()
})

/** 并行查询当前用户在空间中的三类职责，避免建立后端不存在的“我的 Bug”临时接口。 */
async function loadPersonalBugs(): Promise<void> {
  if (!userId.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const [assignedPage, submittedPage, acceptancePage] = await Promise.all([
      fetchBugs(workspaceId.value, { page: 1, pageSize: 5, assigneeId: userId.value }),
      fetchBugs(workspaceId.value, { page: 1, pageSize: 5, creatorId: userId.value }),
      fetchBugs(workspaceId.value, {
        page: 1,
        pageSize: 5,
        acceptorId: userId.value,
        status: 'WAIT_ACCEPTANCE',
      }),
    ])
    assigned.value = assignedPage.records
    submitted.value = submittedPage.records
    acceptance.value = acceptancePage.records
    assignedTotal.value = assignedPage.total
    submittedTotal.value = submittedPage.total
    acceptanceTotal.value = acceptancePage.total
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载我的 Bug 失败'
  } finally {
    loading.value = false
  }
}

/** 进入对应完整列表页继续筛选和分页。 */
function openGroup(group: PersonalGroup): void {
  void router.push({ name: group.routeName, params: { workspaceId: workspaceId.value } })
}

/** 进入 Bug 详情。 */
function openBug(bugId: number): void {
  void router.push({ name: 'bug-detail', params: { workspaceId: workspaceId.value, bugId } })
}
</script>

<template>
  <main class="my-bugs-page">
    <header class="page-heading">
      <div>
        <h1>我的 Bug</h1>
        <p>聚合你在当前工作空间中负责、提交和验收的事项。</p>
      </div>
    </header>

    <div v-if="errorMessage" class="page-alert">
      <span>{{ errorMessage }}</span
      ><button type="button" @click="loadPersonalBugs">重新加载</button>
    </div>

    <div class="personal-grid">
      <section v-for="group in groups" :key="group.key" class="personal-card">
        <header>
          <div>
            <h2>{{ group.title }}</h2>
            <p>{{ group.description }}</p>
          </div>
          <strong>{{ loading ? '—' : group.total }}</strong>
        </header>
        <div class="personal-list">
          <button
            v-for="item in group.records"
            :key="item.id"
            type="button"
            @click="openBug(item.id)"
          >
            <span class="personal-list__top"
              ><i>{{ item.bugNo }}</i
              ><time>{{ formatDateTime(item.updatedAt) }}</time></span
            >
            <strong>{{ item.title }}</strong>
            <span class="personal-list__meta">
              <i :class="`priority priority--${item.priority.toLowerCase()}`">{{
                PRIORITY_META[item.priority].label
              }}</i>
              <i :class="`status status--${item.status.toLowerCase()}`">{{
                STATUS_META[item.status].label
              }}</i>
            </span>
          </button>
          <div v-if="!loading && group.records.length === 0" class="personal-list__empty">
            <AppIcon name="acceptance" :size="23" /><span>当前没有相关 Bug</span>
          </div>
        </div>
        <button type="button" class="personal-card__more" @click="openGroup(group)">
          查看全部 <AppIcon name="arrow-right" :size="15" />
        </button>
      </section>
    </div>
  </main>
</template>

<style scoped>
.my-bugs-page {
  max-width: 1400px;
  margin: 0 auto;
}
.page-heading {
  margin-bottom: 25px;
}
.page-heading h1 {
  margin: 0 0 7px;
  color: #f2f6fb;
  font-size: 29px;
}
.page-heading p {
  margin: 0;
  color: var(--bl-text-secondary);
  font-size: 14px;
}
.page-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 11px 14px;
  color: #ffaaa5;
  font-size: 13px;
  background: rgb(117 34 38 / 25%);
  border: 1px solid rgb(226 76 76 / 30%);
  border-radius: 8px;
}
.page-alert button {
  color: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
}
.personal-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 17px;
}
.personal-card {
  overflow: hidden;
  background: linear-gradient(145deg, #171e26, #141a21);
  border: 1px solid var(--bl-border);
  border-radius: 9px;
}
.personal-card > header {
  display: flex;
  min-height: 84px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 20px;
  border-bottom: 1px solid var(--bl-border);
}
.personal-card h2 {
  margin: 0;
  color: #edf3fa;
  font-size: 17px;
}
.personal-card header p {
  margin: 6px 0 0;
  color: var(--bl-muted);
  font-size: 11px;
}
.personal-card header > strong {
  color: #4da3ff;
  font-size: 29px;
}
.personal-list {
  min-height: 375px;
  padding: 8px 18px;
}
.personal-list > button {
  display: grid;
  width: 100%;
  gap: 9px;
  padding: 16px 3px;
  color: #cbd4df;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #29323c;
}
.personal-list > button:hover strong {
  color: #fff;
}
.personal-list__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
}
.personal-list__top i {
  color: #52a9ff;
  font-size: 11px;
  font-style: normal;
}
.personal-list__top time {
  color: #718093;
  font-size: 10px;
}
.personal-list > button > strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.personal-list__meta {
  display: flex;
  gap: 7px;
}
.personal-list__meta i {
  padding: 3px 7px;
  font-size: 10px;
  font-style: normal;
  border-radius: 5px;
}
.priority--p0,
.priority--p1,
.status--todo,
.status--reopened {
  color: #ff7971;
  background: #492624;
}
.priority--p2,
.status--wait_acceptance {
  color: #f0c858;
  background: #453b18;
}
.priority--p3,
.status--closed {
  color: #56d399;
  background: #173c2e;
}
.status--processing {
  color: #56a6ff;
  background: #193755;
}
.personal-list__empty {
  display: grid;
  min-height: 330px;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: #687789;
  font-size: 12px;
}
.personal-card__more {
  display: flex;
  width: 100%;
  height: 47px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: #4c9fff;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  background: #161e27;
  border: 0;
  border-top: 1px solid var(--bl-border);
}
@media (max-width: 1150px) {
  .personal-grid {
    grid-template-columns: 1fr;
  }
  .personal-list {
    min-height: 0;
  }
  .personal-list__empty {
    min-height: 130px;
  }
}
</style>
