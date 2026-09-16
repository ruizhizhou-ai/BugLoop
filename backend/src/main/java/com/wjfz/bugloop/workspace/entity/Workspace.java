/**
 * 本文件映射 workspace 表，保存工作空间基础信息、负责人和启停状态。
 */
package com.wjfz.bugloop.workspace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 工作空间实体，是成员关系和 Bug 数据隔离的上层边界。
 */
@TableName("workspace")
public class Workspace {

    // 工作空间 ID 由应用生成随机值，避免通过自增值枚举工作空间。
    @TableId(type = IdType.INPUT)
    // 工作空间主键，由应用生成随机值，避免被连续 ID 枚举。
    private Long id;

    // 工作空间名称。
    private String name;

    // 工作空间的补充说明。
    private String description;

    // 空间负责人用户 ID，承担空间管理责任。
    private Long ownerId;

    // 空间启停状态，停用后不再允许常规业务操作。
    private WorkspaceStatus status;

    // 创建工作空间的用户 ID，用于审计创建来源。
    private Long createdBy;

    // 工作空间创建时间，由 MyBatis-Plus 自动填充。
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // 工作空间最后更新时间，由 MyBatis-Plus 自动维护。
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public WorkspaceStatus getStatus() {
        return status;
    }

    public void setStatus(WorkspaceStatus status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
