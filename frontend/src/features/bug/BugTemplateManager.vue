<!-- 本文件实现个人模板管理弹窗：展示我的模板与内置模板，个人模板可新建、编辑、删除。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElPopconfirm,
  ElSelect,
  ElTag,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/tag/style/css'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { BUG_PRIORITY_OPTIONS, PRIORITY_META, formatDateTime } from './bugMeta'
import { SYSTEM_BUG_TEMPLATES } from './bugTemplates'
import {
  createBugTemplate,
  deleteBugTemplate,
  listBugTemplates,
  toPersonalBugTemplateViewModel,
  toSystemBugTemplateViewModel,
  updateBugTemplate,
} from './bugTemplateApi'
import type { BugTemplateViewModel, CreateBugTemplateRequest } from './bugTemplateApi'
import { isApiError } from '@/shared/api/types'

interface BugTemplateManagerProps {
  modelValue: boolean
  /** 个人模板所属工作空间，新建和查询都以该空间为边界。 */
  workspaceId: number
}

const props = defineProps<BugTemplateManagerProps>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 个人模板发生增删改后通知调用页刷新模板列表。 */
  changed: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
// 内置模板来自前端静态配置，不随工作空间变化，只在初始化时转换一次。
const systemTemplates = SYSTEM_BUG_TEMPLATES.map(toSystemBugTemplateViewModel)
const personalTemplates = ref<BugTemplateViewModel[]>([])
const loading = ref(false)
const errorMessage = ref('')
const deletingId = ref<number | null>(null)

const editorVisible = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const editorFormRef = ref<FormInstance>()
const editorSubmitting = ref(false)
const editorError = ref('')
// 编辑必须回传原排序值：更新接口不允许省略，页面也不提供排序入口。
const editorTemplateId = ref<number | null>(null)
const editorSortOrder = ref(0)
const editorForm = reactive<CreateBugTemplateRequest>({
  name: '',
  title: '',
  descriptionMd: '',
  priority: 'P2',
})

const editorRules: FormRules = {
  name: [
    { required: true, whitespace: true, message: '请输入模板名称', trigger: 'blur' },
    { max: 100, message: '模板名称不能超过 100 个字符', trigger: 'blur' },
  ],
  title: [
    { required: true, whitespace: true, message: '请输入标题', trigger: 'blur' },
    { max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' },
  ],
  descriptionMd: [
    {
      validator: (_rule, value, callback) => {
        if (!String(value ?? '').trim()) {
          callback(new Error('请输入详细说明'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

/** 打开弹窗或切换工作空间时重新拉取列表，不能复用上一个空间的模板。 */
watch(
  () => (props.modelValue ? props.workspaceId : null),
  (workspaceId) => {
    if (workspaceId !== null) {
      void loadTemplates()
    }
  },
  { immediate: true },
)

/** 只读取当前用户在当前工作空间的个人模板，失败提示保留在弹窗内。 */
async function loadTemplates(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    personalTemplates.value = (await listBugTemplates(props.workspaceId))
      .map(toPersonalBugTemplateViewModel)
      .sort((left, right) => left.sortOrder - right.sortOrder)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载我的模板失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 来源列只展示业务编号；手工创建的模板没有来源 Bug。 */
function sourceLabel(template: BugTemplateViewModel): string {
  return template.sourceBugNo ? `基于 ${template.sourceBugNo}` : '手工创建'
}

/** 模板列表按天展示更新时间，避免和管理操作的时间精度混淆。 */
function updatedDate(template: BugTemplateViewModel): string {
  return formatDateTime(template.updatedAt).slice(0, 10)
}

/** 新建入口使用空白表单，不复用上一次编辑的内容。 */
function openCreateEditor(): void {
  editorMode.value = 'create'
  editorTemplateId.value = null
  editorSortOrder.value = 0
  editorForm.name = ''
  editorForm.title = ''
  editorForm.descriptionMd = ''
  editorForm.priority = 'P2'
  editorError.value = ''
  editorVisible.value = true
}

/** 编辑入口回填当前模板内容，归属和来源 Bug 不进入表单。 */
function openEditEditor(template: BugTemplateViewModel): void {
  if (template.scope !== 'PERSONAL' || typeof template.id !== 'number') {
    return
  }
  editorMode.value = 'edit'
  editorTemplateId.value = template.id
  editorSortOrder.value = template.sortOrder
  editorForm.name = template.name
  editorForm.title = template.title
  editorForm.descriptionMd = template.descriptionMd
  editorForm.priority = template.priority
  editorError.value = ''
  editorVisible.value = true
}

/** 校验后按模式调用创建或更新接口，成功后刷新列表并通知调用页。 */
async function handleSaveEditor(): Promise<void> {
  if (!editorFormRef.value) {
    return
  }
  const valid = await editorFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  editorSubmitting.value = true
  editorError.value = ''
  const request: CreateBugTemplateRequest = {
    name: editorForm.name.trim(),
    title: editorForm.title.trim(),
    descriptionMd: editorForm.descriptionMd,
    priority: editorForm.priority,
  }
  try {
    if (editorMode.value === 'edit' && editorTemplateId.value !== null) {
      await updateBugTemplate(editorTemplateId.value, {
        ...request,
        sortOrder: editorSortOrder.value,
      })
    } else {
      await createBugTemplate(props.workspaceId, request)
    }
    editorVisible.value = false
    await loadTemplates()
    emit('changed')
  } catch (error) {
    editorError.value = isApiError(error) ? error.message : '保存模板失败，请稍后重试'
  } finally {
    editorSubmitting.value = false
  }
}

/** 删除个人模板；内置模板没有删除入口，后端也会拒绝越权删除。 */
async function handleDelete(template: BugTemplateViewModel): Promise<void> {
  if (typeof template.id !== 'number') {
    return
  }
  deletingId.value = template.id
  errorMessage.value = ''
  try {
    await deleteBugTemplate(template.id)
    await loadTemplates()
    emit('changed')
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '删除模板失败，请稍后重试'
  } finally {
    deletingId.value = null
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="模板管理" width="min(94vw, 720px)" append-to-body>
    <el-alert
      v-if="errorMessage"
      class="template-manager__alert"
      :title="errorMessage"
      type="error"
      :closable="true"
      show-icon
      @close="errorMessage = ''"
    />

    <section class="template-manager__section" aria-labelledby="personal-templates">
      <header class="template-manager__header">
        <h3 id="personal-templates">
          我的模板 <span>{{ personalTemplates.length }}</span>
        </h3>
        <el-button type="primary" @click="openCreateEditor">新建模板</el-button>
      </header>

      <p v-if="loading" class="template-manager__hint">加载中…</p>
      <p v-else-if="!personalTemplates.length" class="template-manager__hint">
        暂无个人模板，可新建模板或在 Bug 详情页把现有 Bug 存为模板。
      </p>
      <ul v-else class="template-list">
        <li v-for="template in personalTemplates" :key="template.id" class="template-row">
          <div class="template-row__main">
            <p class="template-row__title">
              <el-tag size="small" type="primary" effect="plain">我的</el-tag>
              <strong>{{ template.name }}</strong>
            </p>
            <p class="template-row__meta">
              <el-tag size="small" :type="PRIORITY_META[template.priority].tag" effect="plain">
                {{ PRIORITY_META[template.priority].label }}
              </el-tag>
              <span>{{ sourceLabel(template) }}</span>
              <span>更新于 {{ updatedDate(template) }}</span>
            </p>
          </div>
          <div class="template-row__actions">
            <el-button link type="primary" @click="openEditEditor(template)">编辑</el-button>
            <el-popconfirm
              title="删除后不可恢复，确认删除该模板吗？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="handleDelete(template)"
            >
              <template #reference>
                <el-button link type="danger" :loading="deletingId === template.id">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </li>
      </ul>
    </section>

    <section class="template-manager__section" aria-labelledby="system-templates">
      <header class="template-manager__header">
        <h3 id="system-templates">
          内置模板 <span>{{ systemTemplates.length }}</span>
        </h3>
      </header>
      <ul class="template-list">
        <li v-for="template in systemTemplates" :key="template.id" class="template-row">
          <div class="template-row__main">
            <p class="template-row__title">
              <el-tag size="small" type="info" effect="plain">内置</el-tag>
              <strong>{{ template.name }}</strong>
            </p>
            <p class="template-row__meta">
              <el-tag size="small" :type="PRIORITY_META[template.priority].tag" effect="plain">
                {{ PRIORITY_META[template.priority].label }}
              </el-tag>
              <span>系统内置，不支持编辑</span>
            </p>
          </div>
        </li>
      </ul>
    </section>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="editorVisible"
    :title="editorMode === 'edit' ? '编辑模板' : '新建模板'"
    width="min(92vw, 860px)"
    append-to-body
  >
    <el-alert
      v-if="editorError"
      class="template-manager__alert"
      :title="editorError"
      type="error"
      :closable="true"
      show-icon
      @close="editorError = ''"
    />
    <el-form
      ref="editorFormRef"
      :model="editorForm"
      :rules="editorRules"
      label-position="top"
      @submit.prevent="handleSaveEditor"
    >
      <el-form-item label="模板名称" prop="name">
        <el-input
          v-model="editorForm.name"
          maxlength="100"
          show-word-limit
          placeholder="例如：登录失败排查模板"
        />
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="editorForm.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="详细说明（Markdown）" prop="descriptionMd">
        <md-editor v-model="editorForm.descriptionMd" />
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="editorForm.priority">
          <el-option
            v-for="option in BUG_PRIORITY_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <p class="template-manager__note">
        模板只保存模板名称、标题、详细说明和优先级；编辑不会改变模板归属和来源 Bug。
      </p>
    </el-form>
    <template #footer>
      <el-button :disabled="editorSubmitting" @click="editorVisible = false">取消</el-button>
      <el-button type="primary" :loading="editorSubmitting" @click="handleSaveEditor"
        >保存</el-button
      >
    </template>
  </el-dialog>
</template>

<style scoped>
.template-manager__alert {
  margin-bottom: 16px;
}

.template-manager__section + .template-manager__section {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--bl-border);
}

.template-manager__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.template-manager__header h3 {
  margin: 0;
  color: var(--bl-text);
  font-size: 15px;
}

.template-manager__header h3 span {
  margin-left: 4px;
  color: var(--bl-muted);
  font-size: 12px;
  font-weight: 400;
}

.template-manager__hint {
  margin: 0;
  color: var(--bl-muted);
  font-size: 13px;
  line-height: 1.7;
}

.template-manager__note {
  margin: 0;
  color: var(--bl-muted);
  font-size: 12px;
  line-height: 1.6;
}

.template-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.template-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--bl-border);
}

.template-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.template-row__main {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.template-row__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  min-width: 0;
}

.template-row__title strong {
  min-width: 0;
  overflow: hidden;
  color: var(--bl-text);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-row__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  color: var(--bl-muted);
  font-size: 12px;
}

.template-row__actions {
  display: flex;
  align-items: center;
  margin-left: auto;
  white-space: nowrap;
}
</style>
