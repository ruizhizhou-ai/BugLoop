/**
 * 本文件映射 bug_comment 表，保存不可编辑、不可删除的 Markdown 评论原文。
 * 评论的创建和工作空间权限由 CommentService 处理，实体不承载业务规则。
 */
package com.wjfz.bugloop.bug.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBugId() { return bugId; }
    public void setBugId(Long bugId) { this.bugId = bugId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContentMd() { return contentMd; }
    public void setContentMd(String contentMd) { this.contentMd = contentMd; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
