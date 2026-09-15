/**
 * 本文件保存工作空间列表、当前选择和成员列表，并负责在浏览器中恢复最近一次切换结果。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  addWorkspaceMember,
  createWorkspace,
  disableWorkspace,
  enableWorkspace,
  fetchMyWorkspaces,
  fetchWorkspace,
  fetchWorkspaceMembers,
  removeWorkspaceMember,
  updateWorkspace,
  updateWorkspaceMemberRole,
} from './workspaceApi'
import type {
  AddWorkspaceMemberPayload,
  Workspace,
  WorkspaceMember,
  WorkspacePayload,
  WorkspaceRole,
} from './workspaceApi'

const CURRENT_WORKSPACE_KEY = 'bugloop.currentWorkspaceId'

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaces = ref<Workspace[]>([])
  const currentWorkspace = ref<Workspace | null>(null)
  const members = ref<WorkspaceMember[]>([])
  const loading = ref(false)

  const currentWorkspaceId = computed(() => currentWorkspace.value?.id ?? null)
  const isEnabled = computed(() => currentWorkspace.value?.status === 'ENABLED')

  /**
   * 加载当前用户的工作空间，并优先恢复仍有权限访问的上一次选择。
   */
  async function loadWorkspaces(): Promise<void> {
    loading.value = true
    try {
      workspaces.value = await fetchMyWorkspaces()
      if (workspaces.value.length === 0) {
        clearCurrentWorkspace()
        return
      }

      const storedId = Number(localStorage.getItem(CURRENT_WORKSPACE_KEY))
      const target =
        workspaces.value.find((workspace) => workspace.id === storedId) ?? workspaces.value[0]
      if (target) {
        await selectWorkspace(target.id)
      }
    } finally {
      loading.value = false
    }
  }

  /**
   * 切换工作空间并同步刷新成员，确保页面不会继续展示前一个空间的数据。
   *
   * @param workspaceId 目标工作空间主键
   */
  async function selectWorkspace(workspaceId: number): Promise<void> {
    const [workspace, workspaceMembers] = await Promise.all([
      fetchWorkspace(workspaceId),
      fetchWorkspaceMembers(workspaceId),
    ])
    currentWorkspace.value = workspace
    members.value = workspaceMembers
    localStorage.setItem(CURRENT_WORKSPACE_KEY, String(workspaceId))
  }

  /** 创建空间后立即切换过去，使空状态和顶部选择器保持一致。 */
  async function create(payload: WorkspacePayload): Promise<void> {
    const workspace = await createWorkspace(payload)
    workspaces.value = [workspace, ...workspaces.value]
    await selectWorkspace(workspace.id)
  }

  /** 更新当前空间并同步替换选择器中的摘要数据。 */
  async function update(payload: WorkspacePayload): Promise<void> {
    if (!currentWorkspace.value) {
      return
    }
    const workspace = await updateWorkspace(currentWorkspace.value.id, payload)
    replaceWorkspace(workspace)
  }

  /** 添加成员并刷新成员列表，使用服务端排序作为唯一展示顺序。 */
  async function addMember(payload: AddWorkspaceMemberPayload): Promise<void> {
    if (!currentWorkspace.value) {
      return
    }
    await addWorkspaceMember(currentWorkspace.value.id, payload)
    members.value = await fetchWorkspaceMembers(currentWorkspace.value.id)
  }

  /** 修改成员角色并用服务端响应替换本地行。 */
  async function changeMemberRole(userId: number, role: WorkspaceRole): Promise<void> {
    if (!currentWorkspace.value) {
      return
    }
    const updated = await updateWorkspaceMemberRole(currentWorkspace.value.id, userId, role)
    members.value = members.value.map((member) => (member.userId === userId ? updated : member))

    // 当前用户角色可能被其他管理员修改，重新读取空间详情可避免按钮权限与服务端不一致。
    currentWorkspace.value = await fetchWorkspace(currentWorkspace.value.id)
    replaceWorkspace(currentWorkspace.value)
  }

  /**
   * 移除成员后重新加载可访问空间。这样管理员移除自己时会自动切换到其他空间，
   * 不会继续请求一个已经失去成员权限的成员列表。
   */
  async function removeMember(userId: number): Promise<void> {
    if (!currentWorkspace.value) {
      return
    }
    await removeWorkspaceMember(currentWorkspace.value.id, userId)
    await loadWorkspaces()
  }

  /** 停用当前工作空间，并保留在选择器中供历史只读访问。 */
  async function disable(): Promise<void> {
    if (!currentWorkspace.value) {
      return
    }
    replaceWorkspace(await disableWorkspace(currentWorkspace.value.id))
  }

  /** 重新启用当前空间，并保留已经加载的成员与当前选择。 */
  async function enable(): Promise<void> {
    if (!currentWorkspace.value) {
      return
    }
    replaceWorkspace(await enableWorkspace(currentWorkspace.value.id))
  }

  /** 清空与当前账号相关的工作空间状态。 */
  function reset(): void {
    workspaces.value = []
    clearCurrentWorkspace()
  }

  /** 用最新工作空间同时更新当前详情和列表摘要。 */
  function replaceWorkspace(workspace: Workspace): void {
    currentWorkspace.value = workspace
    workspaces.value = workspaces.value.map((item) => (item.id === workspace.id ? workspace : item))
  }

  /** 清除当前选择和成员，避免账号切换后短暂显示上一用户的数据。 */
  function clearCurrentWorkspace(): void {
    currentWorkspace.value = null
    members.value = []
    localStorage.removeItem(CURRENT_WORKSPACE_KEY)
  }

  return {
    workspaces,
    currentWorkspace,
    currentWorkspaceId,
    members,
    loading,
    isEnabled,
    loadWorkspaces,
    selectWorkspace,
    create,
    update,
    addMember,
    changeMemberRole,
    removeMember,
    disable,
    enable,
    reset,
  }
})
