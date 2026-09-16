/**
 * 本文件映射 workspace_member 表，维护用户与工作空间之间唯一的成员和角色关系。
 */
package com.wjfz.bugloop.workspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 工作空间成员实体，后续 Bug 权限判断以该关系作为第一层数据边界。
 */
@TableName("workspace_member")
public class WorkspaceMember {

    @TableId(type = IdType.AUTO)
    // 成员关系主键。
    private Long id;

    // 成员所属工作空间 ID。
    private Long workspaceId;

    // 加入该工作空间的用户 ID。
    private Long userId;

    // 用户在当前工作空间内的角色和权限范围。
    private WorkspaceRole role;

    // 成员关系创建，即加入工作空间的时间。
    private LocalDateTime joinedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public void setRole(WorkspaceRole role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
