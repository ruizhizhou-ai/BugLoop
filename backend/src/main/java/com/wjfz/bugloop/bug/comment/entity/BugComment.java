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
    // 评论主键。
    private Long id;
    // 评论所属 Bug ID。
    private Long bugId;
    // 创建该评论的用户 ID。
    private Long userId;
    // 评论 Markdown 正文；逻辑删除后不再向客户端输出。
    private String contentMd;
    // 评论树父节点 ID，顶级评论为空。
    private Long parentId;
    // 当前评论直接回复的用户 ID，用于展示回复关系和后续通知扩展。
    private Long replyUserId;
    // 评论创建时间。
    private LocalDateTime createdAt;
    // 评论最后更新时间。
    private LocalDateTime updatedAt;
    // 逻辑删除标记，保留节点以维持子评论关系。
    @TableField("is_deleted")
    private Boolean deleted;
    // 执行评论删除操作的用户 ID。
    private Long deletedBy;
    // 评论被逻辑删除的时间。
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
