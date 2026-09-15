/**
 * 本文件集中维护工作空间前端权限提示规则，用于控制入口可见性；服务端仍是最终授权边界。
 */
import type { AuthUser } from '@/features/auth/authApi'
import type { Workspace } from './workspaceApi'

/**
 * 判断当前用户是否应看到“新建工作空间”入口。
 * 系统管理员直接允许，普通用户必须已在至少一个空间担任负责人或管理员。
 */
export function canCreateWorkspace(
  systemRole: AuthUser['systemRole'] | undefined,
  workspaces: readonly Workspace[],
): boolean {
  return (
    systemRole === 'SYSTEM_ADMIN' ||
    workspaces.some(
      (workspace) => workspace.currentUserRole === 'OWNER' || workspace.currentUserRole === 'ADMIN',
    )
  )
}
