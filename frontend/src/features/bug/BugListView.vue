<!-- 本文件实现 Bug 列表页：筛选、分页、状态与人员展示，并提供新建入口。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/pagination/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import { useBugStore } from './bugStore'
import { BUG_PRIORITY_OPTIONS, BUG_STATUS_OPTIONS, PRIORITY_META, STATUS_META, formatDateTime } from './bugMeta'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'
import { isApiError } from '@/shared/api/types'

const route = useRoute()
const router = useRouter()
const bugStore = useBugStore()
const workspaceStore = useWorkspaceStore()

const errorMessage = ref('')
const dateRange = ref<[string, string] | null>(null)

const workspaceId = computed(() => Number(route.params.workspaceId))
const canCreate = computed(() => workspaceStore.isEnabled)

onMounted(() => {
  void loadBugs()
})

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
  await loadBugs()
}

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

function openBug(bugId: number): void {
  void router.push({ name: 'bug-detail', params: { workspaceId: workspaceId.value, bugId } })
}

function createBug(): void {
  void router.push({ name: 'bug-create', params: { workspaceId: workspaceId.value } })
}

function personName(user: { displayName: string } | null): string {
  return user?.displayName ?? '未指定'
}
</script>

<template>
  <main class="bug-list">
    <el-alert
      v-if="errorMessage"
      class="bug-list__alert"
      :title="errorMessage"
      type="error"
      :closable="true"
      show-icon
      @close="errorMessage = ''"
    />

    <el-card shadow="never">
      <template #header>
        <div class="bug-list__header">
          <div>
            <h2>Bug 列表</h2>
            <span class="bug-list__subtitle">共 {{ bugStore.total }} 条</span>
          </div>
          <el-button v-if="canCreate" type="primary" @click="createBug">新建 Bug</el-button>
        </div>
      </template>

      <div class="bug-list__filters">
        <el-input
          v-model="bugStore.query.keyword"
          class="filter-item filter-item--keyword"
          placeholder="搜索编号或标题"
          clearable
          @keyup.enter="applyFilters"
          @clear="applyFilters"
        />
        <el-select v-model="bugStore.query.status" class="filter-item" placeholder="状态" clearable @change="applyFilters">
          <el-option v-for="option in BUG_STATUS_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select
          v-model="bugStore.query.priority"
          class="filter-item"
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
          v-model="bugStore.query.assigneeId"
          class="filter-item"
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
          v-model="bugStore.query.creatorId"
          class="filter-item"
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
        <el-date-picker
          :model-value="dateRange"
          class="filter-item filter-item--date"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="创建开始"
          end-placeholder="创建结束"
          @update:model-value="handleDateChange"
        />
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table
        v-loading="bugStore.loading"
        :data="bugStore.list"
        row-key="id"
        class="bug-list__table"
        @row-click="(row: { id: number }) => openBug(row.id)"
      >
        <el-table-column prop="bugNo" label="编号" width="130" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="PRIORITY_META[row.priority as keyof typeof PRIORITY_META].tag" size="small">
              {{ PRIORITY_META[row.priority as keyof typeof PRIORITY_META].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status as keyof typeof STATUS_META].tag" size="small">
              {{ STATUS_META[row.status as keyof typeof STATUS_META].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="120">
          <template #default="{ row }">{{ personName(row.assignee) }}</template>
        </el-table-column>
        <el-table-column label="提交人" width="120">
          <template #default="{ row }">{{ personName(row.creator) }}</template>
        </el-table-column>
        <el-table-column label="验收人" width="120">
          <template #default="{ row }">{{ personName(row.acceptor) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="当前条件下没有 Bug">
            <el-button v-if="canCreate" type="primary" @click="createBug">创建第一个 Bug</el-button>
          </el-empty>
        </template>
      </el-table>

      <el-pagination
        v-if="bugStore.total > bugStore.query.pageSize"
        class="bug-list__pagination"
        layout="prev, pager, next, total"
        :total="bugStore.total"
        :page-size="bugStore.query.pageSize"
        :current-page="bugStore.query.page"
        @current-change="handlePageChange"
      />
    </el-card>
  </main>
</template>

<style scoped>
.bug-list__alert {
  margin-bottom: 16px;
}

.bug-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.bug-list__header h2 {
  margin: 0;
  font-size: 16px;
}

.bug-list__subtitle {
  color: #909399;
  font-size: 13px;
}

.bug-list__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.filter-item {
  width: 150px;
}

.filter-item--keyword {
  width: 220px;
}

.filter-item--date {
  width: 300px;
}

.bug-list__table :deep(.el-table__row) {
  cursor: pointer;
}

.bug-list__pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
