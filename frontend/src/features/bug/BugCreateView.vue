<!-- 本文件实现 Bug 创建页，描述使用 Markdown 编辑器，默认验收人为当前用户。 -->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/popper/style/css'
import 'element-plus/es/components/select/style/css'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { useBugStore } from './bugStore'
import { BUG_PRIORITY_OPTIONS } from './bugMeta'
import type { BugPriority } from './bugApi'
import { useAuthStore } from '@/features/auth/authStore'
import { useWorkspaceStore } from '@/features/workspace/workspaceStore'
import { isApiError } from '@/shared/api/types'

const route = useRoute()
const router = useRouter()
const bugStore = useBugStore()
const auth = useAuthStore()
const workspaceStore = useWorkspaceStore()

const workspaceId = computed(() => Number(route.params.workspaceId))
const formRef = ref<FormInstance>()
const errorMessage = ref('')

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
    await router.push({
      name: 'bug-detail',
      params: { workspaceId: workspaceId.value, bugId: created.id },
    })
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '创建 Bug 失败，请稍后重试'
  }
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

      <el-alert
        v-if="errorMessage"
        class="bug-create__alert"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            maxlength="200"
            show-word-limit
            placeholder="简要描述问题现象"
          />
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

        <el-button type="primary" :loading="bugStore.submitting" @click="handleSubmit"
          >创建</el-button
        >
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

.bug-create__alert {
  margin-bottom: 16px;
}

.bug-create__editor {
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
</style>
