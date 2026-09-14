/**
 * 本文件定义添加工作空间成员的输入，目标用户必须是已启用的系统用户。
 */
package com.wjfz.bugloop.workspace.dto;

import com.wjfz.bugloop.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 添加成员请求。
 *
 * @param userId 目标用户主键
 * @param role 初始工作空间角色
 */
public record AddWorkspaceMemberRequest(
        @NotNull(message = "用户 ID 不能为空")
        @Positive(message = "用户 ID 必须为正数")
        Long userId,
        @NotNull(message = "成员角色不能为空")
        WorkspaceRole role) {
}
