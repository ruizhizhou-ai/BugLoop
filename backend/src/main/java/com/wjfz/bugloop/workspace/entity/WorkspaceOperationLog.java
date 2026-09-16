/**
 * 本文件映射 workspace_operation_log 表，记录工作空间与成员变更的审计信息。
 */
package com.wjfz.bugloop.workspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 工作空间操作日志实体，仅由工作空间服务在业务事务内写入。
 */
@TableName("workspace_operation_log")
public class WorkspaceOperationLog {

    @TableId(type = IdType.AUTO)
    // 工作空间操作日志主键。
    private Long id;

    // 被操作的工作空间 ID。
    private Long workspaceId;

    // 实际执行操作的用户 ID。
    private Long operatorId;

    // 用于区分业务行为的操作类型编码。
    private String operationType;

    // 被修改的字段名；非字段变更操作时为空。
    private String fieldName;

    // 变更前值的审计快照。
    private String oldValue;

    // 变更后值的审计快照。
    private String newValue;

    // 供页面展示的可读操作说明。
    private String description;

    // 操作发生时间。
    private LocalDateTime createdAt;

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

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
