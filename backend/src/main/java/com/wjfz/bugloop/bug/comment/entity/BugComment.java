/**
 * 本文件映射 bug_comment 表，保存 Markdown 评论、一级回复和逻辑删除审计字段。
 * 评论的创建、回复、删除和工作空间权限由 CommentService 处理，实体不承载业务规则。
 */
package com.wjfz.bugloop.bug.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** Bug 评论持久化实体。 */
@TableName("bug_comment")
public class BugComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bugId;
    private Long userId;
    private String contentMd;
    private Long parentId;
    private Long replyUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField("is_deleted")
    private Boolean deleted;
    private Long deletedBy;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBugId() { return bugId; }
    public void setBugId(Long bugId) { this.bugId = bugId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContentMd() { return contentMd; }
    public void setContentMd(String contentMd) { this.contentMd = contentMd; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getReplyUserId() { return replyUserId; }
    public void setReplyUserId(Long replyUserId) { this.replyUserId = replyUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
