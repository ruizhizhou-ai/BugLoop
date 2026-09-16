/**
 * 本文件映射 bug 表，保存当前问题内容、责任人和状态快照；只在服务内部使用。
 * version 由 Mapper 的条件更新统一递增，以便状态、历史和审计在同一事务中提交。
 */
package com.wjfz.bugloop.bug.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** Bug 持久化实体，业务规则由 BugService 执行。 */
@TableName("bug")
public class Bug {
    @TableId(type = IdType.AUTO)
    // Bug 主键。
    private Long id;

    // Bug 所属工作空间 ID，是数据隔离的基础边界。
    private Long workspaceId;

    // 面向用户展示的全局唯一 Bug 编号。
    private String bugNo;

    // Bug 的简短标题。
    private String title;

    // 问题描述的 Markdown 原文。
    private String descriptionMd;

    // 问题处理优先级。
    private BugPriority priority;

    // Bug 当前生命周期状态。
    private BugStatus status;

    // 提交 Bug 的创建人用户 ID。
    private Long creatorId;

    // 当前负责人用户 ID，未指派时为空。
    private Long assigneeId;

    // 负责验收处理结果的用户 ID。
    private Long acceptorId;

    // 负责人填写的处理说明 Markdown 原文。
    private String fixDescriptionMd;

    // 被驳回或重新打开的累计次数。
    private Integer reopenCount;

    // 条件更新使用的乐观锁版本号。
    private Integer version;

    // Bug 创建时间。
    private LocalDateTime createdAt;

    // Bug 当前快照最后更新时间。
    private LocalDateTime updatedAt;

    // Bug 关闭时间，未关闭时为空。
    private LocalDateTime closedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }

    public String getBugNo() { return bugNo; }
    public void setBugNo(String bugNo) { this.bugNo = bugNo; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescriptionMd() { return descriptionMd; }
    public void setDescriptionMd(String descriptionMd) { this.descriptionMd = descriptionMd; }

    public BugPriority getPriority() { return priority; }
    public void setPriority(BugPriority priority) { this.priority = priority; }

    public BugStatus getStatus() { return status; }
    public void setStatus(BugStatus status) { this.status = status; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }

    public Long getAcceptorId() { return acceptorId; }
    public void setAcceptorId(Long acceptorId) { this.acceptorId = acceptorId; }

    public String getFixDescriptionMd() { return fixDescriptionMd; }
    public void setFixDescriptionMd(String fixDescriptionMd) { this.fixDescriptionMd = fixDescriptionMd; }

    public Integer getReopenCount() { return reopenCount; }
    public void setReopenCount(Integer reopenCount) { this.reopenCount = reopenCount; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
