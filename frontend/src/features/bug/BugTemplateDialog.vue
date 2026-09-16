<!-- 本文件实现 Bug 详情页“存为模板”弹窗：打开时回填来源 Bug 的标题、描述和优先级，保存前可修改。 -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { BUG_PRIORITY_OPTIONS } from './bugMeta'
import type { BugPriority } from './bugApi'
import { saveBugAsTemplate } from './bugTemplateApi'
import type { SaveBugAsTemplateRequest } from './bugTemplateApi'
import { isApiError } from '@/shared/api/types'

interface BugTemplateDialogProps {
  modelValue: boolean
  /** 来源 Bug 主键；是否有权保存由后端按该 Bug 校验。 */
  bugId: number
  /** 打开时回填的标题 */
  sourceTitle: string
  /** 打开时回填的 Markdown 描述 */
  sourceDescriptionMd: string
  /** 打开时回填的优先级 */
  sourcePriority: BugPriority
}

const props = defineProps<BugTemplateDialogProps>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive<SaveBugAsTemplateRequest>({
  name: '',
  title: '',
  descriptionMd: '',
  priority: 'P2',
})

const rules: FormRules = {
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

/** 每次打开都按当前来源 Bug 重新回填，模板名称始终从空开始，避免沿用上一次的名称。 */
watch(
  () => props.modelValue,
  (opened) => {
    if (!opened) {
      return
    }
    form.name = ''
    form.title = props.sourceTitle
    form.descriptionMd = props.sourceDescriptionMd
    form.priority = props.sourcePriority
    void nextTick(() => formRef.value?.clearValidate())
  },
  // 组件可能在已展开状态下挂载，首次也要完成回填。
  { immediate: true },
)

/** 校验后提交保存；成功即关闭弹窗，失败保留表单内容便于就地修改重试。 */
async function handleSave(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    await saveBugAsTemplate(props.bugId, {
      name: form.name.trim(),
      title: form.title.trim(),
      descriptionMd: form.descriptionMd,
      priority: form.priority,
    })
    visible.value = false
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '保存模板失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

/** 关闭后清理本次错误提示，下次打开只保留新回填的表单内容。 */
function resetState(): void {
  errorMessage.value = ''
  formRef.value?.clearValidate()
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="存为模板"
    width="min(92vw, 860px)"
    append-to-body
    @closed="resetState"
  >
    <el-alert
      v-if="errorMessage"
      class="bug-template-dialog__alert"
      :title="errorMessage"
      type="error"
      :closable="true"
      show-icon
      @close="errorMessage = ''"
    />
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleSave"
    >
      <el-form-item label="模板名称" prop="name">
        <el-input
          v-model="form.name"
          maxlength="100"
          show-word-limit
          placeholder="例如：登录失败排查模板"
        />
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="详细说明（Markdown）" prop="descriptionMd">
        <md-editor v-model="form.descriptionMd" />
      </el-form-item>
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
      <p class="bug-template-dialog__hint">
        模板仅保存模板名称、标题、详细说明和优先级，不会复制其他业务数据。
      </p>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.bug-template-dialog__alert {
  margin-bottom: 16px;
}

.bug-template-dialog__hint {
  margin: 0;
  color: var(--bl-muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
