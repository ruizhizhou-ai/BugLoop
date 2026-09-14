/**
 * 本文件定义成员角色修改输入，角色范围固定为 OWNER、ADMIN 和 MEMBER。
 */
package com.wjfz.bugloop.workspace.dto;

import com.wjfz.bugloop.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

/**
 * 修改成员角色请求。
 *
 * @param role 新工作空间角色
 */
public record UpdateWorkspaceMemberRoleRequest(
        @NotNull(message = "成员角色不能为空") WorkspaceRole role) {
}
