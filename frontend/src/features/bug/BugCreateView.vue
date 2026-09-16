<!-- 本文件实现 Bug 创建页，支持从内置或个人模板回填基础字段，默认验收人为当前用户。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessageBox,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/radio/style/css'
import 'element-plus/es/components/select/style/css'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { useBugStore } from './bugStore'
import BugTemplateManager from './BugTemplateManager.vue'
import {
  ATTACHMENT_ACCEPT,
  BUG_PRIORITY_OPTIONS,
  attachmentValidationError,
  formatFileSize,
} from './bugMeta'
import type { BugCreated, BugPriority } from './bugApi'
import { SYSTEM_BUG_TEMPLATES } from './bugTemplates'
import {
  listBugTemplates,
  toPersonalBugTemplateViewModel,
  toSystemBugTemplateViewModel,
} from './bugTemplateApi'
import type { BugTemplateViewModel } from './bugTemplateApi'
import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'
import AppNotice from '@/shared/components/AppNotice.vue'
import { isApiError } from '@/shared/api/types'

const route = useRoute()
const router = useRouter()
const bugStore = useBugStore()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const BLANK_TEMPLATE_KEY = 'BLANK'
const workspaceId = computed(() => Number(route.params.workspaceId))
const workspaceTitlePrefix = computed(() => `${workspaceStore.currentWorkspace?.name ?? '当前工作空间'}-`)
// 服务端会将该前缀写入最终标题；输入框仅接收问题描述，并为前缀占用的长度预留空间。
const issueTitleMaxLength = computed(() => Math.max(1, 200 - workspaceTitlePrefix.value.length))
const formRef = ref<FormInstance>()
const errorMessage = ref('')
const attachmentInput = ref<HTMLInputElement | null>(null)
const pendingFiles = ref<File[]>([])
// 附件依赖 Bug 主键，创建成功后立即上传；上传失败时保留该对象并提供进入详情的入口。
const createdBug = ref<BugCreated | null>(null)
const personalTemplates = ref<BugTemplateViewModel[]>([])
const templatesLoading = ref(false)
const templateManagerVisible = ref(false)
// “空白”是默认项；只有确认实际应用模板后才更新 appliedTemplateKey，用于取消覆盖时回退选择状态。
const selectedTemplateKey = ref(BLANK_TEMPLATE_KEY)
const appliedTemplateKey = ref(BLANK_TEMPLATE_KEY)

const systemTemplates = SYSTEM_BUG_TEMPLATES.map(toSystemBugTemplateViewModel)
const templateByKey = computed(() => {
  const entries = [...systemTemplates, ...personalTemplates.value].map(
    (template): [string, BugTemplateViewModel] => [buildTemplateKey(template), template],
  )
  return new Map<string, BugTemplateViewModel>(entries)
})

const form = reactive({
  title: '',
  descriptionMd: '',
  priority: 'P2' as BugPriority,
  assigneeId: undefined as number | undefined,
  acceptorId: auth.user?.id as number | undefined,
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 200, message: '标题最多 200 字符', trigger: 'blur' },
  ],
  descriptionMd: [
    {
      validator: (_rule, value, callback) => {
        if (!value || !String(value).trim()) {
          callback(new Error('请输入详细说明'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

/** 工作空间切换时重新加载当前用户的个人模板，不能复用上一个空间的列表。 */
watch(
  workspaceId,
  () => {
    selectedTemplateKey.value = BLANK_TEMPLATE_KEY
    appliedTemplateKey.value = BLANK_TEMPLATE_KEY
    personalTemplates.value = []
    void loadPersonalTemplates()
  },
  { immediate: true },
)

/** 加载个人模板失败不影响空白或内置模板创建，提示保留在当前创建页。 */
async function loadPersonalTemplates(): Promise<void> {
  templatesLoading.value = true
  try {
    personalTemplates.value = (await listBugTemplates(workspaceId.value))
      .map(toPersonalBugTemplateViewModel)
      .sort((left, right) => left.sortOrder - right.sortOrder)
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载我的模板失败，请稍后重试'
  } finally {
    templatesLoading.value = false
  }
}

/** 为不同来源生成稳定且不冲突的单选项键，内置字符串 ID 与个人数字 ID 可共存。 */
function buildTemplateKey(template: BugTemplateViewModel): string {
  return `${template.scope}:${template.id}`
}

/** 标题或 Markdown 已存在时，选择另一模板必须获得确认，避免一次点击静默覆盖用户输入。 */
function hasTemplateOverwritableContent(): boolean {
  return Boolean(form.title.trim() || form.descriptionMd.trim())
}

/**
 * 处理模板选择；切换到空白模板会清除模板填入的基础字段，负责人和验收人保持用户选择。
 *
 * @param selectedKey 当前单选组件选择的模板键；Element Plus 允许多种值类型，页面仅接受字符串键
 */
async function handleTemplateSelection(
  selectedKey: string | number | boolean | undefined,
): Promise<void> {
  if (typeof selectedKey !== 'string') {
    selectedTemplateKey.value = appliedTemplateKey.value
    return
  }
  if (selectedKey === BLANK_TEMPLATE_KEY) {
    if (hasTemplateOverwritableContent()) {
      try {
        // 空白模板会移除当前标题和描述，先确认以避免用户手写内容被一次点击清空。
        await ElMessageBox.confirm('当前模板内容将被清空，是否继续？', '使用空白模板', {
          confirmButtonText: '清空内容',
          cancelButtonText: '取消',
          type: 'warning',
        })
      } catch {
        // 用户取消后恢复已实际应用的模板选项，表单内容与人员选择均不变。
        selectedTemplateKey.value = appliedTemplateKey.value
        return
      }
    }
    // “空白”只重置模板可控制的基础字段，不能影响负责人和验收人。
    form.title = ''
    form.descriptionMd = ''
    form.priority = 'P2'
    appliedTemplateKey.value = BLANK_TEMPLATE_KEY
    return
  }
  const template = templateByKey.value.get(selectedKey)
  if (!template) {
    // 模板列表异步刷新后不存在的旧选择必须回退，避免展示无法应用的错误状态。
    selectedTemplateKey.value = appliedTemplateKey.value
    return
  }
  if (hasTemplateOverwritableContent()) {
    try {
      await ElMessageBox.confirm('当前内容将被模板覆盖，是否继续？', '使用模板', {
        confirmButtonText: '继续覆盖',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      // 用户取消后恢复到上一次已确认的选项，表单和负责人、验收人均保持不变。
      selectedTemplateKey.value = appliedTemplateKey.value
      return
    }
  }
  // 仅回填产品定义允许的三个基础字段，绝不能通过模板改写责任人与验收人。
  form.title = template.title
  form.descriptionMd = template.descriptionMd
  form.priority = template.priority
  appliedTemplateKey.value = selectedKey
}

/** 创建 Bug，随后补传选中的附件并跳转详情页。 */
async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  errorMessage.value = ''
  try {
    const created = await bugStore.create(workspaceId.value, {
      title: form.title.trim(),
      descriptionMd: form.descriptionMd,
      priority: form.priority,
      assigneeId: form.assigneeId ?? null,
      acceptorId: form.acceptorId ?? null,
    })
    createdBug.value = created

    const failed = pendingFiles.value.length
      ? await bugStore.uploadAttachments(created.id, pendingFiles.value, {
          bizType: 'BUG_CREATE',
          bizId: created.id,
        })
      : []
    if (failed.length) {
      errorMessage.value = `Bug 已创建（${created.bugNo}），但以下附件上传失败：${failed.join('、')}，可进入详情页重新上传。`
      return
    }

    await openDetail()
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '创建 Bug 失败，请稍后重试'
  }
}

async function openDetail(): Promise<void> {
  if (!createdBug.value) {
    return
  }
  await router.push({
    name: 'bug-detail',
    params: { workspaceId: workspaceId.value, bugId: createdBug.value.id },
  })
}

function pickAttachments(): void {
  attachmentInput.value?.click()
}

/** 选择文件后逐个做与后端一致的前置校验，不合规的文件直接提示并不入列。 */
function handleAttachmentsPicked(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    const invalidReason = attachmentValidationError(file, pendingFiles.value.length)
    if (invalidReason) {
      errorMessage.value = invalidReason
      continue
    }
    pendingFiles.value.push(file)
  }
}

function removeAttachment(index: number): void {
  pendingFiles.value.splice(index, 1)
}

function goBack(): void {
  void router.push({ name: 'bug-list', params: { workspaceId: workspaceId.value } })
}
</script>

<template>
  <main class="bug-create">
    <el-card shadow="never">
      <template #header>
        <div class="bug-create__header">
          <h2>新建 Bug</h2>
          <el-button @click="goBack">返回列表</el-button>
        </div>
      </template>

      <app-notice v-if="errorMessage" :message="errorMessage" @close="errorMessage = ''" />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <section class="bug-create__templates" aria-labelledby="template-heading">
          <div class="bug-create__templates-header">
            <div>
              <h3 id="template-heading">使用模板</h3>
              <p>模板仅会填充标题、详细说明和优先级。</p>
            </div>
            <button
              type="button"
              class="bug-create__templates-manage"
              @click="templateManagerVisible = true"
            >
              管理我的模板 →
            </button>
          </div>

          <el-radio-group
            v-model="selectedTemplateKey"
            class="bug-create__template-options"
            :disabled="templatesLoading"
            @change="handleTemplateSelection"
          >
            <el-radio :value="BLANK_TEMPLATE_KEY" class="bug-create__template-option">空白</el-radio>

            <div class="bug-create__template-group">
              <span class="bug-create__template-group-title">内置模板</span>
              <el-radio
                v-for="template in systemTemplates"
                :key="buildTemplateKey(template)"
                :value="buildTemplateKey(template)"
                class="bug-create__template-option"
                >{{ template.name }}</el-radio
              >
            </div>

            <div class="bug-create__template-group">
              <span class="bug-create__template-group-title">我的模板</span>
              <el-radio
                v-for="template in personalTemplates"
                :key="buildTemplateKey(template)"
                :value="buildTemplateKey(template)"
                class="bug-create__template-option"
                >{{ template.name }}</el-radio
              >
              <span v-if="!templatesLoading && !personalTemplates.length" class="bug-create__template-empty"
                >暂无个人模板</span
              >
            </div>
          </el-radio-group>
        </section>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            :maxlength="issueTitleMaxLength"
            show-word-limit
            placeholder="问题描述"
          >
            <template #prepend>{{ workspaceTitlePrefix }}</template>
          </el-input>
        </el-form-item>

        <el-form-item label="详细说明（Markdown）" prop="descriptionMd">
          <md-editor
            v-model="form.descriptionMd"
            class="bug-create__editor"
            placeholder="问题现象、复现步骤、期望结果等"
          />
        </el-form-item>

        <div class="bug-create__row">
          <el-form-item label="优先级" prop="priority">
            <el-select v-model="form.priority">
              <el-option
                v-for="option in BUG_PRIORITY_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="负责人" prop="assigneeId">
            <el-select v-model="form.assigneeId" placeholder="暂不指定" clearable filterable>
              <el-option
                v-for="member in workspaceStore.members"
                :key="member.userId"
                :label="member.displayName"
                :value="member.userId"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="验收人" prop="acceptorId">
            <el-select v-model="form.acceptorId" clearable filterable>
              <el-option
                v-for="member in workspaceStore.members"
                :key="member.userId"
                :label="member.displayName"
                :value="member.userId"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item v-if="!createdBug" label="附件">
          <input
            ref="attachmentInput"
            class="bug-create__file-input"
            type="file"
            multiple
            :accept="ATTACHMENT_ACCEPT"
            @change="handleAttachmentsPicked"
          />
          <div class="bug-create__attachment-picker">
            <el-button :disabled="bugStore.submitting" @click="pickAttachments">选择文件</el-button>
            <span>支持 png/jpg/gif/webp/pdf/txt/log，单个不超过 20MB，最多 20 个</span>
          </div>
          <ul v-if="pendingFiles.length" class="bug-create__file-list">
            <li v-for="(file, index) in pendingFiles" :key="file.name + index">
              <span class="bug-create__file-name">{{ file.name }}</span>
              <span class="bug-create__file-size">{{ formatFileSize(file.size) }}</span>
              <el-button link type="danger" class="bug-create__file-remove" @click="removeAttachment(index)"
                >移除</el-button
              >
            </li>
          </ul>
        </el-form-item>

        <el-button
          v-if="!createdBug"
          type="primary"
          :loading="bugStore.submitting"
          @click="handleSubmit"
          >创建</el-button
        >
        <el-button v-else type="primary" @click="openDetail">进入详情页</el-button>
      </el-form>
    </el-card>

    <BugTemplateManager
      v-model="templateManagerVisible"
      :workspace-id="workspaceId"
      @changed="loadPersonalTemplates"
    />
  </main>
</template>

<style scoped>
.bug-create__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.bug-create__header h2 {
  margin: 0;
  color: #f0f5fb;
  font-size: 21px;
}

.bug-create__editor {
  width: 100%;
}

.bug-create__templates {
  margin: 0 0 24px;
  padding: 18px 20px;
  border: 1px solid var(--bl-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--bl-surface) 88%, transparent);
}

.bug-create__templates-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.bug-create__templates-header h3 {
  margin: 0;
  color: var(--bl-text-primary);
  font-size: 15px;
}

.bug-create__templates-header p {
  margin: 5px 0 0;
  color: var(--bl-muted);
  font-size: 12px;
}

.bug-create__templates-manage {
  flex: 0 0 auto;
  padding: 0;
  color: var(--bl-muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.bug-create__templates-manage:hover,
.bug-create__templates-manage:focus-visible {
  color: var(--bl-primary-light);
}

.bug-create__template-options {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px 24px;
}

.bug-create__template-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 14px;
  min-height: 24px;
  padding-left: 24px;
  border-left: 1px solid var(--bl-border);
}

.bug-create__template-group-title {
  color: var(--bl-muted);
  font-size: 12px;
}

.bug-create__template-option {
  margin-right: 0;
}

.bug-create__template-empty {
  color: var(--bl-muted);
  font-size: 13px;
}

.bug-create__row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.bug-create__row :deep(.el-form-item) {
  min-width: 200px;
}

.bug-create__file-input {
  display: none;
}

.bug-create__attachment-picker {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--bl-muted);
  font-size: 12px;
}

.bug-create__file-list {
  width: 100%;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.bug-create__file-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  color: var(--bl-text-secondary);
  font-size: 13px;
  border-bottom: 1px solid var(--bl-border);
}

.bug-create__file-list li:last-child {
  border-bottom: 0;
}

.bug-create__file-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-create__file-size {
  color: var(--bl-muted);
  font-size: 12px;
}

.bug-create__file-remove {
  margin-left: auto;
}

.bug-create {
  max-width: 1120px;
  margin: 0 auto;
}

.bug-create :deep(.el-card) {
  border-radius: 9px;
}

.bug-create :deep(.el-card__body) {
  padding: 24px;
}

.bug-create :deep(.el-form-item__label) {
  color: #aeb9c7;
}

@media (max-width: 640px) {
  .bug-create__templates-header {
    flex-direction: column;
  }

  .bug-create__template-options {
    flex-direction: column;
    align-items: stretch;
  }

  .bug-create__template-group {
    padding-top: 10px;
    padding-left: 0;
    border-top: 1px solid var(--bl-border);
    border-left: 0;
  }
}
</style>
