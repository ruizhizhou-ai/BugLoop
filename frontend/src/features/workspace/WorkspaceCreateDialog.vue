<!-- 本文件提供可复用的工作空间创建弹窗，供空状态引导页和工作台侧栏共享同一创建流程。 -->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'

import { isApiError } from '@/shared/api/types'
import { useWorkspaceStore } from './workspaceStore'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [workspaceId: number]
}>()

const workspaceStore = useWorkspaceStore()
const submitting = ref(false)
const errorMessage = ref('')
const createForm = reactive({ name: '', description: '' })

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

/** 校验输入并调用现有 Store 创建空间，成功后把新空间主键交给所在页面完成跳转。 */
async function handleCreateWorkspace(): Promise<void> {
  const name = createForm.name.trim()
  if (!name) {
    errorMessage.value = '请输入工作空间名称'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    await workspaceStore.create({ name, description: createForm.description.trim() || null })
    const workspaceId = workspaceStore.currentWorkspaceId
    visible.value = false
    if (workspaceId) {
      emit('created', workspaceId)
    }
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : '创建工作空间失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

/** 弹窗完全关闭后清理上一次输入，避免下次创建时残留名称或错误提示。 */
function resetForm(): void {
  createForm.name = ''
  createForm.description = ''
  errorMessage.value = ''
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="创建工作空间"
    width="min(92vw, 520px)"
    append-to-body
    @closed="resetForm"
  >
    <el-alert
      v-if="errorMessage"
      class="create-workspace-alert"
      :title="errorMessage"
      type="error"
      :closable="true"
      show-icon
      @close="errorMessage = ''"
    />
    <el-form label-position="top" @submit.prevent="handleCreateWorkspace">
      <el-form-item label="名称" required>
        <el-input v-model="createForm.name" maxlength="100" show-word-limit autofocus />
      </el-form-item>
      <el-form-item label="说明">
        <el-input
          v-model="createForm.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleCreateWorkspace">
        创建
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.create-workspace-alert {
  margin-bottom: 16px;
}
</style>
