/**
 * 本文件定义工作空间对外响应，附带当前用户角色以支持前端切换后的权限展示。
 */
package com.wjfz.bugloop.workspace.vo;

import com.wjfz.bugloop.workspace.entity.Workspace;
import com.wjfz.bugloop.workspace.entity.WorkspaceRole;
import com.wjfz.bugloop.workspace.entity.WorkspaceStatus;

import java.time.LocalDateTime;

/**
 * 工作空间响应对象。
 *
 * @param id 工作空间主键
 * @param name 名称
 * @param description 描述
 * @param ownerId 当前主要负责人
 * @param status 启停状态
 * @param currentUserRole 当前用户的工作空间角色，SYSTEM_ADMIN 未加入时为空
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record WorkspaceVO(
        Long id,
        String name,
        String description,
        Long ownerId,
        WorkspaceStatus status,
        WorkspaceRole currentUserRole,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * 将工作空间实体和当前成员角色转换为接口响应。
     *
     * @param workspace 工作空间实体
     * @param currentUserRole 当前用户角色
     * @return 工作空间响应
     */
    public static WorkspaceVO from(Workspace workspace, WorkspaceRole currentUserRole) {
        return new WorkspaceVO(workspace.getId(), workspace.getName(), workspace.getDescription(),
                workspace.getOwnerId(), workspace.getStatus(), currentUserRole,
                workspace.getCreatedAt(), workspace.getUpdatedAt());
    }
}
