/**
 * 本文件定义一次工作空间权限校验后的上下文，供工作空间与后续 Bug 服务复用校验结果。
 */
package com.wjfz.bugloop.workspace;

import com.wjfz.bugloop.user.User;

/**
 * 工作空间访问上下文。
 *
 * @param workspace 已确认存在的工作空间
 * @param currentUser 当前登录用户
 * @param membership 当前用户的成员关系；SYSTEM_ADMIN 未加入目标工作空间时可以为空
 */
public record WorkspaceAccess(Workspace workspace, User currentUser, WorkspaceMember membership) {

    /**
     * 返回当前用户的工作空间角色。
     *
     * @return 未加入的 SYSTEM_ADMIN 返回 null，普通成员返回其角色
     */
    public WorkspaceRole currentRole() {
        return membership == null ? null : membership.getRole();
    }
}
