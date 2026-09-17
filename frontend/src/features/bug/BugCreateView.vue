<!-- 本文件实现 Bug 创建页，支持按模板类型下拉选择数据库系统、个人或共享模板，默认验收人为当前用户。 -->
<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessageBox,
  ElOption,
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
import 'element-plus/es/components/select/style/css'
import { MdEditor } from 'md-editor-v3'
import type { UploadImgEvent } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { useBugStore } from './bugStore'
import {
  ATTACHMENT_ACCEPT,
  BUG_PRIORITY_OPTIONS,
  attachmentValidationError,
  formatFileSize,
} from './bugMeta'
import { uploadBugDraftImage } from './bugApi'
import type { BugCreated, BugPriority } from './bugApi'
import { listBugTemplates } from './bugTemplateApi'
import type { BugTemplate } from './bugTemplateApi'
import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'
import AppNotice from '@/shared/components/AppNotice.vue'
import { downloadFile } from '@/shared/api/http'
import { isApiError } from '@/shared/api/types'

const route = useRoute()
const router = useRouter()
const bugStore = useBugStore()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const BLANK_TEMPLATE_KEY = 'BLANK'
type TemplateCategory = 'BLANK' | 'SYSTEM' | 'PERSONAL' | 'SHARED'
const workspaceId = computed(() => Number(route.params.workspaceId))
const workspaceTitlePrefix = computed(
  () => `${workspaceStore.currentWorkspace?.name ?? '当前工作空间'}-`,
)
// 服务端会将该前缀写入最终标题；输入框仅接收问题描述，并为前缀占用的长度预留空间。
const issueTitleMaxLength = computed(() => Math.max(1, 200 - workspaceTitlePrefix.value.length))
const formRef = ref<FormInstance>()
const errorMessage = ref('')
const attachmentInput = ref<HTMLInputElement | null>(null)
const pendingFiles = ref<File[]>([])
// 附件依赖 Bug 主键，创建成功后立即上传；上传失败时保留该对象并提供进入详情的入口。
const createdBug = ref<BugCreated | null>(null)
// 图片内容接口要求 Authorization 请求头；缓存为 Blob URL 后，Markdown 编辑器预览不会裸请求受保护地址。
const markdownImageUrls = new Map<string, string>()
const markdownImageTasks = new Map<string, Promise<string>>()
const availableTemplates = ref<BugTemplate[]>([])
const templatesLoading = ref(false)
// 类型与具体模板拆开保存，避免切换下拉类型时立即覆盖用户已填写的内容。
const selectedTemplateCategory = ref<TemplateCategory>('BLANK')
const selectedTemplateKey = ref<string | null>(null)
// 只有确认实际应用模板后才更新，用于在覆盖确认取消时恢复下拉选项。
const appliedTemplateKey = ref(BLANK_TEMPLATE_KEY)

const systemTemplates = computed(() =>
  availableTemplates.value.filter((template) => template.scope === 'SYSTEM'),
)
const personalTemplates = computed(() =>
  availableTemplates.value.filter(
    (template) => template.scope === 'PERSONAL' && template.creatorId === auth.user?.id,
  ),
)
const sharedTemplates = computed(() =>
  availableTemplates.value.filter(
    (template) => template.scope === 'PERSONAL' && template.creatorId !== auth.user?.id,
  ),
)
const templatesForSelectedCategory = computed(() => {
  switch (selectedTemplateCategory.value) {
    case 'SYSTEM':
      return systemTemplates.value
    case 'PERSONAL':
      return personalTemplates.value
    case 'SHARED':
      return sharedTemplates.value
    default:
      return []
  }
})
const templateByKey = computed(() => {
  const entries = availableTemplates.value.map((template): [string, BugTemplate] => [
    buildTemplateKey(template),
    template,
  ])
  return new Map<string, BugTemplate>(entries)
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

/** 工作空间切换时重新加载当前空间可使用的数据库模板，不能复用上一个空间的下拉选择。 */
watch(
  workspaceId,
  () => {
    selectedTemplateCategory.value = 'BLANK'
    selectedTemplateKey.value = null
    appliedTemplateKey.value = BLANK_TEMPLATE_KEY
    availableTemplates.value = []
    void loadTemplates()
  },
  { immediate: true },
)

/** 加载数据库模板失败不影响空白 Bug 创建，提示保留在当前创建页。 */
async function loadTemplates(): Promise<void> {
  templatesLoading.value = true
  try {
    availableTemplates.value = (await listBugTemplates(workspaceId.value)).sort(
      (left, right) => left.sortOrder - right.sortOrder,
    )
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '加载模板失败，请稍后重试'
  } finally {
    templatesLoading.value = false
  }
}

/** 为不同数据库范围生成稳定且不冲突的下拉选项键。 */
function buildTemplateKey(template: BugTemplate): string {
  return `${template.scope}:${template.id}`
}

/** 标题或 Markdown 已存在时，选择另一模板必须获得确认，避免一次点击静默覆盖用户输入。 */
function hasTemplateOverwritableContent(): boolean {
  return Boolean(form.title.trim() || form.descriptionMd.trim())
}

/**
 * 处理模板选择；切换到空白模板会清除模板填入的基础字段，负责人和验收人保持用户选择。
 *
 * @param selectedKey 当前下拉组件选择的模板键
 */
async function handleTemplateSelection(selectedKey: string | null | undefined): Promise<void> {
  if (!selectedKey) {
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
        restoreAppliedTemplateSelection()
        return
      }
    }
    // “空白”只重置模板可控制的基础字段，不能影响负责人和验收人。
    form.title = ''
    form.descriptionMd = ''
    form.priority = 'P2'
    appliedTemplateKey.value = BLANK_TEMPLATE_KEY
    selectedTemplateKey.value = null
    return
  }
  const template = templateByKey.value.get(selectedKey)
  if (!template) {
    // 模板列表异步刷新后不存在的旧选择必须回退，避免展示无法应用的错误状态。
    restoreAppliedTemplateSelection()
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
      restoreAppliedTemplateSelection()
      return
    }
  }
  // 仅回填产品定义允许的三个基础字段，绝不能通过模板改写责任人与验收人。
  form.title = template.title
  form.descriptionMd = template.descriptionMd
  form.priority = template.priority
  appliedTemplateKey.value = selectedKey
}

/**
 * 切换模板类型时只更新候选列表；切换到空白才会走清空确认，避免类型浏览误改表单。
 *
 * @param category 用户在类型下拉框中选择的范围
 */
async function handleTemplateCategorySelection(category: TemplateCategory): Promise<void> {
  selectedTemplateCategory.value = category
  selectedTemplateKey.value = null
  if (category === 'BLANK') {
    await handleTemplateSelection(BLANK_TEMPLATE_KEY)
  }
}

/** 覆盖确认取消或模板异步消失时，根据最后实际应用的模板恢复两个下拉框。 */
function restoreAppliedTemplateSelection(): void {
  if (appliedTemplateKey.value === BLANK_TEMPLATE_KEY) {
    selectedTemplateCategory.value = 'BLANK'
    selectedTemplateKey.value = null
    return
  }
  selectedTemplateCategory.value = appliedTemplateKey.value.split(':', 1)[0] as TemplateCategory
  selectedTemplateKey.value = appliedTemplateKey.value
}

/**
 * 上传编辑器选择的图片，先获取带鉴权的 Blob 缓存，再把稳定的后端地址交给编辑器写入 Markdown。
 *
 * @param files Markdown 编辑器选择的图片列表
 * @param callback 编辑器提供的图片 URL 回填函数
 */
const handleMarkdownImageUpload: UploadImgEvent = async (files, callback) => {
  if (!files.length) {
    return
  }
  errorMessage.value = ''
  try {
    const images = await Promise.all(
      files.map((file) => uploadBugDraftImage(workspaceId.value, file)),
    )
    // 先完成 Blob 缓存再回填 Markdown，避免编辑器预览用原生 img 请求时遗漏 Authorization 请求头。
    await Promise.all(images.map((image) => cacheMarkdownImage(image.url)))
    callback(images.map((image) => image.url))
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '正文图片上传失败，请稍后重试'
  }
}

/** 解析编辑器预览图片地址；只有已缓存的受保护图片替换为页面生命周期内的 Blob URL。 */
function transformMarkdownImageUrl(url: string): string {
  return markdownImageUrls.get(url) ?? url
}

/** 通过统一二进制客户端拉取图片，复用 Authorization 请求头并避免在 Markdown 内持久化登录凭证。 */
function cacheMarkdownImage(url: string): Promise<string> {
  const cached = markdownImageUrls.get(url)
  if (cached) {
    return Promise.resolve(cached)
  }
  const pending = markdownImageTasks.get(url)
  if (pending) {
    return pending
  }
  // downloadFile 的客户端基路径已是 /api；Markdown 需要保留 /api 前缀，下载时则必须避免再次拼接。
  const task = downloadFile(toBinaryApiPath(url))
    .then(({ blob }) => {
      const objectUrl = URL.createObjectURL(blob)
      markdownImageUrls.set(url, objectUrl)
      return objectUrl
    })
    .finally(() => markdownImageTasks.delete(url))
  markdownImageTasks.set(url, task)
  return task
}

/** 将持久化在 Markdown 内的 API 绝对路径转换为二进制客户端可用的相对路径。 */
function toBinaryApiPath(url: string): string {
  return url.startsWith('/api/') ? url.slice('/api'.length) : url
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

/** 页面关闭时释放临时 Blob URL，避免反复打开创建页导致浏览器内存累积。 */
onBeforeUnmount(() => {
  markdownImageUrls.forEach((objectUrl) => URL.revokeObjectURL(objectUrl))
  markdownImageUrls.clear()
})
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
          </div>

          <div class="bug-create__template-selects">
            <label class="bug-create__template-field">
              <span>模板类型</span>
              <el-select
                v-model="selectedTemplateCategory"
                data-template-category-select
                :disabled="templatesLoading"
                @change="handleTemplateCategorySelection"
              >
                <el-option label="空白" value="BLANK" />
                <el-option label="系统模板" value="SYSTEM" />
                <el-option label="我的模板" value="PERSONAL" />
                <el-option label="共享模板" value="SHARED" />
              </el-select>
            </label>

            <label v-if="selectedTemplateCategory !== 'BLANK'" class="bug-create__template-field">
              <span>选择模板</span>
              <el-select
                v-model="selectedTemplateKey"
                data-template-select
                :disabled="templatesLoading || !templatesForSelectedCategory.length"
                :placeholder="templatesForSelectedCategory.length ? '请选择模板' : '暂无可用模板'"
                clearable
                @change="handleTemplateSelection"
              >
                <el-option
                  v-for="template in templatesForSelectedCategory"
                  :key="buildTemplateKey(template)"
                  :label="template.name"
                  :value="buildTemplateKey(template)"
                />
              </el-select>
            </label>
          </div>
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
            :on-upload-img="handleMarkdownImageUpload"
            :transform-img-url="transformMarkdownImageUrl"
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
              <el-button
                link
                type="danger"
                class="bug-create__file-remove"
                @click="removeAttachment(index)"
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

.bug-create__template-selects {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
}

.bug-create__template-field {
  display: flex;
  flex: 1 1 230px;
  flex-direction: column;
  gap: 6px;
  color: var(--bl-muted);
  font-size: 12px;
}

.bug-create__template-field :deep(.el-select) {
  width: 100%;
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

  .bug-create__template-selects {
    flex-direction: column;
  }
}
</style>
