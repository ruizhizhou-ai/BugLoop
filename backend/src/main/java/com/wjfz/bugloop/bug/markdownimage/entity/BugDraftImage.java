/**
 * 本文件映射 bug_draft_image 表，保存 Bug 创建前的 Markdown 草稿图片及其绑定状态。
 * 真实文件由 file 模块存储，本实体仅保存受控的内部路径，绝不能直接返回给客户端。
 */
package com.wjfz.bugloop.bug.markdownimage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** Bug Markdown 草稿图片持久化实体。 */
@TableName("bug_draft_image")
public class BugDraftImage {
    // 正文图片主键。
    @TableId(type = IdType.AUTO)
    private Long id;
    // 所属工作空间 ID，用于隔离草稿图片。
    private Long workspaceId;
    // 图片上传人 ID，草稿阶段仅上传人可访问或绑定。
    private Long uploaderId;
    // 已绑定的 Bug ID；草稿阶段为空。
    private Long bugId;
    // 用户上传时的原始图片文件名。
    private String originalName;
    // 存储服务生成的唯一文件名。
    private String storageName;
    // 服务端内部相对存储路径。
    private String storagePath;
    // 图片文件大小，单位字节。
    private Long fileSize;
    // 浏览器声明并经白名单校验的图片 MIME 类型。
    private String contentType;
    // 草稿或已绑定的生命周期状态。
    private DraftImageStatus status;
    // 草稿图片到期时间；绑定后为空。
    private LocalDateTime expiresAt;
    // 上传时间。
    private LocalDateTime createdAt;
    // 最后更新时间。
    private LocalDateTime updatedAt;
    // 逻辑删除标识。
    @TableField("deleted")
    private Boolean deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
    public Long getBugId() { return bugId; }
    public void setBugId(Long bugId) { this.bugId = bugId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStorageName() { return storageName; }
    public void setStorageName(String storageName) { this.storageName = storageName; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public DraftImageStatus getStatus() { return status; }
    public void setStatus(DraftImageStatus status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
