/**
 * 本文件映射 bug_template 表，保存数据库内置模板与用户在工作空间内创建的个人模板。
 * 模板只承载创建 Bug 时可复用的基础字段，范围、共享状态和管理权限由模板服务统一校验。
 */
package com.wjfz.bugloop.bug.template.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wjfz.bugloop.bug.entity.BugPriority;

import java.time.LocalDateTime;

/**
 * Bug 个人模板持久化实体，用于保存单个用户在指定工作空间内可复用的 Bug 基础内容。
 */
@TableName("bug_template")
public class BugTemplate {

    // 模板主键，由数据库自增生成。
    @TableId(type = IdType.AUTO)
    private Long id;

    // 模板所属工作空间 ID，与创建人 ID 共同构成数据隔离边界。
    private Long workspaceId;

    // 模板创建人用户 ID，个人模板仅创建人可管理；系统模板使用内部保留值 0。
    private Long creatorId;

    // 模板范围：SYSTEM 表示全局内置模板，PERSONAL 表示工作空间个人模板。
    private String scope;

    // 个人模板是否已主动共享给同工作空间成员；系统模板固定为 false。
    private Boolean shared;

    // 模板列表中展示和选择的名称。
    private String name;

    // 使用模板创建 Bug 时预填的默认标题。
    private String title;

    // 使用模板创建 Bug 时预填的 Markdown 描述原文。
    private String descriptionMd;

    // 使用模板创建 Bug 时预填的默认优先级，复用 Bug 领域统一枚举。
    private BugPriority priority;

    // 来源 Bug ID；从已有 Bug 保存时记录，手工创建模板时为空。
    private Long sourceBugId;

    // 模板排序值，数值越小在个人模板列表中越靠前。
    private Integer sortOrder;

    // 模板创建时间，由 MyBatis-Plus 自动填充。
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // 模板最后更新时间，由 MyBatis-Plus 在新增和修改时自动维护。
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // 逻辑删除标记；删除后保留记录，但 MyBatis-Plus 基础查询会自动排除。
    @TableLogic(value = "0", delval = "1")
    private Boolean deleted;

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

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Boolean getShared() {
        return shared;
    }

    public void setShared(Boolean shared) {
        this.shared = shared;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescriptionMd() {
        return descriptionMd;
    }

    public void setDescriptionMd(String descriptionMd) {
        this.descriptionMd = descriptionMd;
    }

    public BugPriority getPriority() {
        return priority;
    }

    public void setPriority(BugPriority priority) {
        this.priority = priority;
    }

    public Long getSourceBugId() {
        return sourceBugId;
    }

    public void setSourceBugId(Long sourceBugId) {
        this.sourceBugId = sourceBugId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
