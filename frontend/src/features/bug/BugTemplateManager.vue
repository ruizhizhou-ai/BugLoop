<!-- 本文件提供模板管理页面：按类型筛选数据库模板，用户维护个人模板和共享开关，系统管理员额外维护系统模板。 -->
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
  ElSwitch,
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
import 'element-plus/es/components/switch/style/css'
import 'element-plus/es/components/tag/style/css'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { BUG_PRIORITY_OPTIONS, PRIORITY_META, formatDateTime } from './bugMeta'
import {
  createBugTemplate,
  createSystemBugTemplate,
  deleteBugTemplate,
  listBugTemplates,
  updateBugTemplate,
  updateBugTemplateSharing,
} from './bugTemplateApi'
import type { BugTemplate, CreateBugTemplateRequest, TemplateScope } from './bugTemplateApi'
import { isApiError } from '@/shared/api/types'

interface BugTemplateManagerProps {
  /** 当前工作空间决定个人模板的读取、新建和共享范围。 */
  workspaceId: number
  /** 当前登录用户，用于将自己和他人共享的个人模板分开，并限制管理入口。 */
  currentUserId?: number | null
  /** 系统管理员可新建、编辑和删除全局系统模板。 */
  isSystemAdmin?: boolean
}

const props = withDefaults(defineProps<BugTemplateManagerProps>(), {
  currentUserId: null,
  isSystemAdmin: false,
})
const emit = defineEmits<{ changed: [] }>()
const templates = ref<BugTemplate[]>([])
const loading = ref(false)
const errorMessage = ref('')
const deletingId = ref<number | null>(null)
const sharingId = ref<number | null>(null)
type TemplateCategory = 'SYSTEM' | 'PERSONAL' | 'SHARED'
const selectedCategory = ref<TemplateCategory>('PERSONAL')
const systemTemplates = computed(() =>
  templates.value.filter((template) => template.scope === 'SYSTEM'),
)
const personalTemplates = computed(() =>
  templates.value.filter(
    (template) => template.scope === 'PERSONAL' && template.creatorId === props.currentUserId,
  ),
)
const sharedTemplates = computed(() =>
  templates.value.filter(
    (template) => template.scope === 'PERSONAL' && template.creatorId !== props.currentUserId,
  ),
)

const editorVisible = ref(false)
const editorMode = ref<'create-personal' | 'create-system' | 'edit'>('create-personal')
const editorScope = ref<TemplateScope>('PERSONAL')
const editorFormRef = ref<FormInstance>()
const editorSubmitting = ref(false)
const editorError = ref('')
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
      validator: (_rule, value, callback) =>
        !String(value ?? '').trim() ? callback(new Error('请输入详细说明')) : callback(),
      trigger: 'blur',
    },
  ],
}

/** 进入页面或切换工作空间时重新读取数据库，防止显示其他空间的个人或共享模板。 */
watch(
  () => props.workspaceId,
  (workspaceId) => {
    if (workspaceId > 0) void loadTemplates()
  },
  { immediate: true },
)

/** 数据库接口已按服务端权限过滤，前端仅做范围和创建人分组。 */
async function loadTemplates(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    templates.value = (await listBugTemplates(props.workspaceId)).sort(
      (left, right) => left.sortOrder - right.sortOrder,
    )
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载模板失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 来源列只展示业务编号；系统与手工创建模板均没有来源 Bug。 */
function sourceLabel(template: BugTemplate): string {
  return template.sourceBugNo ? `基于 ${template.sourceBugNo}` : '手工创建'
}
/** 模板列表按天展示更新时间，避免和管理操作的时间精度混淆。 */
function updatedDate(template: BugTemplate): string {
  return formatDateTime(template.updatedAt).slice(0, 10)
}

/** 将下拉框选中的类型映射为对应列表，确保页面每次仅渲染一类模板。 */
const selectedTemplates = computed(() => {
  if (selectedCategory.value === 'SYSTEM') return systemTemplates.value
  if (selectedCategory.value === 'SHARED') return sharedTemplates.value
  return personalTemplates.value
})
/** 根据选中类型提供准确的标题，避免不同权限下出现模糊的模板归属描述。 */
const selectedCategoryTitle = computed(() => {
  if (selectedCategory.value === 'SYSTEM') return '系统模板'
  if (selectedCategory.value === 'SHARED') return '共享模板'
  return '我的模板'
})
/** 当前类型为空时的提示需区分可创建和只读场景，帮助用户理解下一步操作。 */
const selectedEmptyHint = computed(() => {
  if (selectedCategory.value === 'SYSTEM') return '暂无系统模板。'
  if (selectedCategory.value === 'SHARED') return '暂无其他成员共享的模板。'
  return '暂无个人模板，可新建模板或在 Bug 详情页把现有 Bug 存为模板。'
})
/** 共享模板来自其他成员，仅用于创建 Bug，不能在当前页面修改。 */
const canCreateSelectedCategory = computed(
  () =>
    selectedCategory.value === 'PERSONAL' ||
    (selectedCategory.value === 'SYSTEM' && props.isSystemAdmin),
)
/** 选中系统模板时使用系统接口，其他可创建场景均创建个人模板。 */
function createSelectedCategory(): void {
  openCreateEditor(selectedCategory.value === 'SYSTEM' ? 'SYSTEM' : 'PERSONAL')
}
/** 统一判断行操作权限，页面隐藏入口只为减少误操作，服务端仍执行最终鉴权。 */
function canManageTemplate(template: BugTemplate): boolean {
  return template.scope === 'SYSTEM'
    ? props.isSystemAdmin
    : template.creatorId === props.currentUserId
}

/** 以空白表单打开个人或系统模板的新建入口，不复用上一次编辑内容。 */
function openCreateEditor(scope: TemplateScope): void {
  editorMode.value = scope === 'SYSTEM' ? 'create-system' : 'create-personal'
  editorScope.value = scope
  editorTemplateId.value = null
  editorSortOrder.value = 0
  editorForm.name = ''
  editorForm.title = ''
  editorForm.descriptionMd = ''
  editorForm.priority = 'P2'
  editorError.value = ''
  editorVisible.value = true
}
/** 编辑入口只对自己的个人模板及系统管理员可管理的系统模板开放。 */
function openEditEditor(template: BugTemplate): void {
  if (
    (template.scope === 'PERSONAL' && template.creatorId !== props.currentUserId) ||
    (template.scope === 'SYSTEM' && !props.isSystemAdmin)
  )
    return
  editorMode.value = 'edit'
  editorScope.value = template.scope
  editorTemplateId.value = template.id
  editorSortOrder.value = template.sortOrder
  editorForm.name = template.name
  editorForm.title = template.title
  editorForm.descriptionMd = template.descriptionMd
  editorForm.priority = template.priority
  editorError.value = ''
  editorVisible.value = true
}
/** 根据编辑范围调用对应接口；服务端仍会再次校验系统管理员和个人归属权限。 */
async function handleSaveEditor(): Promise<void> {
  if (!editorFormRef.value || !(await editorFormRef.value.validate().catch(() => false))) return
  editorSubmitting.value = true
  editorError.value = ''
  const request: CreateBugTemplateRequest = {
    name: editorForm.name.trim(),
    title: editorForm.title.trim(),
    descriptionMd: editorForm.descriptionMd,
    priority: editorForm.priority,
  }
  try {
    if (editorMode.value === 'edit' && editorTemplateId.value !== null)
      await updateBugTemplate(editorTemplateId.value, {
        ...request,
        sortOrder: editorSortOrder.value,
      })
    else if (editorScope.value === 'SYSTEM') await createSystemBugTemplate(request)
    else await createBugTemplate(props.workspaceId, request)
    editorVisible.value = false
    await loadTemplates()
    emit('changed')
  } catch (error) {
    editorError.value = isApiError(error) ? error.message : '保存模板失败，请稍后重试'
  } finally {
    editorSubmitting.value = false
  }
}
/** 个人模板仅创建人可切换共享，失败后刷新列表还原开关的真实状态。 */
async function handleSharingChange(template: BugTemplate, shared: boolean): Promise<void> {
  sharingId.value = template.id
  errorMessage.value = ''
  try {
    await updateBugTemplateSharing(template.id, shared)
    await loadTemplates()
    emit('changed')
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '更新模板共享状态失败，请稍后重试'
    await loadTemplates()
  } finally {
    sharingId.value = null
  }
}
/** 删除权限与编辑权限一致：个人模板仅创建人，系统模板仅系统管理员。 */
async function handleDelete(template: BugTemplate): Promise<void> {
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
  <main class="template-manager">
    <header class="template-manager__page-header">
      <div>
        <p class="template-manager__eyebrow">工作空间配置</p>
        <h2>模板管理</h2>
        <p>按类型查看模板，数量增加后也能快速定位并维护需要的内容。</p>
      </div>
      <label class="template-manager__filter">
        <span>模板类型</span>
        <el-select v-model="selectedCategory" data-template-scope-select aria-label="模板类型">
          <el-option label="我的模板" value="PERSONAL" />
          <el-option label="共享模板" value="SHARED" />
          <el-option label="系统模板" value="SYSTEM" />
        </el-select>
      </label>
    </header>

    <el-alert
      v-if="errorMessage"
      class="template-manager__alert"
      :title="errorMessage"
      type="error"
      :closable="true"
      show-icon
      @close="errorMessage = ''"
    />
    <section class="template-manager__section" aria-live="polite">
      <header class="template-manager__header">
        <h3>
          {{ selectedCategoryTitle }} <span>{{ selectedTemplates.length }}</span>
        </h3>
        <el-button v-if="canCreateSelectedCategory" type="primary" @click="createSelectedCategory">
          {{ selectedCategory === 'SYSTEM' ? '新建系统模板' : '新建模板' }}
        </el-button>
      </header>
      <p v-if="loading" class="template-manager__hint">加载中…</p>
      <p v-else-if="!selectedTemplates.length" class="template-manager__hint">
        {{ selectedEmptyHint }}
      </p>
      <ul v-else class="template-list">
        <li v-for="template in selectedTemplates" :key="template.id" class="template-row">
          <div class="template-row__main">
            <p class="template-row__title">
              <el-tag
                size="small"
                :type="
                  template.scope === 'SYSTEM'
                    ? 'info'
                    : template.creatorId === currentUserId
                      ? 'primary'
                      : 'success'
                "
                effect="plain"
                >{{
                  template.scope === 'SYSTEM'
                    ? '系统'
                    : template.creatorId === currentUserId
                      ? '我的'
                      : '共享'
                }}</el-tag
              ><strong>{{ template.name }}</strong>
            </p>
            <p class="template-row__meta">
              <el-tag size="small" :type="PRIORITY_META[template.priority].tag" effect="plain">{{
                PRIORITY_META[template.priority].label
              }}</el-tag
              ><span v-if="template.scope === 'SYSTEM'">{{
                isSystemAdmin ? '系统管理员可维护' : '全员可使用'
              }}</span
              ><span v-else>{{ sourceLabel(template) }}</span
              ><span v-if="template.scope !== 'SYSTEM'">更新于 {{ updatedDate(template) }}</span>
            </p>
          </div>
          <div v-if="canManageTemplate(template)" class="template-row__actions">
            <span v-if="template.scope === 'PERSONAL'" class="template-row__sharing"
              >共享<el-switch
                :model-value="template.shared"
                :loading="sharingId === template.id"
                @change="handleSharingChange(template, Boolean($event))" /></span
            ><el-button link type="primary" @click="openEditEditor(template)">编辑</el-button
            ><el-popconfirm
              title="删除后不可恢复，确认删除该模板吗？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="handleDelete(template)"
              ><template #reference
                ><el-button link type="danger" :loading="deletingId === template.id"
                  >删除</el-button
                ></template
              ></el-popconfirm
            >
          </div>
        </li>
      </ul>
    </section>
  </main>
  <el-dialog
    v-model="editorVisible"
    :title="
      editorMode === 'edit' ? '编辑模板' : editorScope === 'SYSTEM' ? '新建系统模板' : '新建模板'
    "
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
      ><el-form-item label="模板名称" prop="name"
        ><el-input
          v-model="editorForm.name"
          maxlength="100"
          show-word-limit
          placeholder="例如：登录失败排查模板" /></el-form-item
      ><el-form-item label="标题" prop="title"
        ><el-input v-model="editorForm.title" maxlength="200" show-word-limit /></el-form-item
      ><el-form-item label="详细说明（Markdown）" prop="descriptionMd"
        ><md-editor v-model="editorForm.descriptionMd" /></el-form-item
      ><el-form-item label="优先级" prop="priority"
        ><el-select v-model="editorForm.priority"
          ><el-option
            v-for="option in BUG_PRIORITY_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value" /></el-select
      ></el-form-item>
      <p class="template-manager__note">
        模板只保存模板名称、标题、详细说明和优先级；共享开关仅适用于个人模板。
      </p></el-form
    >
    <template #footer
      ><el-button :disabled="editorSubmitting" @click="editorVisible = false">取消</el-button
      ><el-button type="primary" :loading="editorSubmitting" @click="handleSaveEditor"
        >保存</el-button
      ></template
    >
  </el-dialog>
</template>

<style scoped>
.template-manager {
  width: min(100%, 1040px);
  margin: 0 auto;
}
.template-manager__page-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  padding: 25px 28px;
  margin-bottom: 18px;
  background: linear-gradient(135deg, rgb(49 140 255 / 10%), transparent 52%), var(--bl-surface);
  border: 1px solid var(--bl-border);
  border-radius: 14px;
}
.template-manager__eyebrow,
.template-manager__page-header h2,
.template-manager__page-header p {
  margin: 0;
}
.template-manager__eyebrow {
  margin-bottom: 7px;
  color: var(--bl-primary);
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.08em;
}
.template-manager__page-header h2 {
  color: var(--bl-text);
  font-size: 25px;
  line-height: 1.25;
}
.template-manager__page-header p:not(.template-manager__eyebrow) {
  margin-top: 9px;
  color: var(--bl-muted);
  font-size: 14px;
}
.template-manager__filter {
  display: grid;
  width: 238px;
  flex: 0 0 auto;
  gap: 7px;
  color: var(--bl-muted);
  font-size: 12px;
  font-weight: 600;
}
.template-manager__alert {
  margin-bottom: 16px;
}
.template-manager__section {
  padding: 22px 26px;
  background: var(--bl-surface);
  border: 1px solid var(--bl-border);
  border-radius: 14px;
}
.template-manager__header,
.template-row,
.template-row__title,
.template-row__meta,
.template-row__actions,
.template-row__sharing {
  display: flex;
  align-items: center;
}
.template-manager__header {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.template-manager__header h3,
.template-row__title,
.template-row__meta,
.template-manager__hint,
.template-manager__note {
  margin: 0;
}
.template-manager__header h3,
.template-row__title strong {
  color: var(--bl-text);
}
.template-manager__header h3 {
  font-size: 15px;
}
.template-manager__header h3 span,
.template-row__meta,
.template-manager__hint,
.template-manager__note {
  color: var(--bl-muted);
  font-size: 12px;
  font-weight: 400;
}
.template-manager__hint,
.template-manager__note {
  line-height: 1.7;
}
.template-list {
  padding: 0;
  margin: 0;
  list-style: none;
}
.template-row {
  gap: 12px;
  padding: 15px 4px;
  border-bottom: 1px solid var(--bl-border);
}
.template-row:hover {
  background: color-mix(in srgb, var(--bl-primary) 4%, transparent);
}
.template-row:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}
.template-row__main {
  display: grid;
  min-width: 0;
  gap: 6px;
}
.template-row__title {
  gap: 8px;
  min-width: 0;
}
.template-row__title strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.template-row__meta {
  flex-wrap: wrap;
  gap: 8px;
}
.template-row__actions {
  gap: 4px;
  margin-left: auto;
  white-space: nowrap;
}
.template-row__sharing {
  gap: 6px;
  color: var(--bl-muted);
  font-size: 12px;
}
@media (max-width: 680px) {
  .template-manager__page-header {
    align-items: stretch;
    flex-direction: column;
    padding: 21px;
  }
  .template-manager__filter {
    width: 100%;
  }
  .template-manager__section {
    padding: 18px;
  }
  .template-row {
    align-items: flex-start;
    flex-direction: column;
  }
  .template-row__actions {
    width: 100%;
    margin-left: 0;
  }
}
</style>
