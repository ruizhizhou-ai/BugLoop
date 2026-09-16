/**
 * 本文件映射 bug_attachment 表，保存附件元数据和逻辑删除状态。
 * 文件字节由 file 模块存储；本实体的 storagePath 仅供服务端下载使用，绝不直接输出。
 */
package com.wjfz.bugloop.bug.attachment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** Bug 附件持久化实体。 */
@TableName("bug_attachment")
public class BugAttachment {
    @TableId(type = IdType.AUTO)
    // 附件主键。
    private Long id;
    // 附件所属 Bug ID。
    private Long bugId;
    // 用户上传时保留的原始文件名。
    private String originalName;
    // 存储模块生成的唯一文件名，避免同名文件冲突。
    private String storageName;
    // 服务端文件存储路径，仅供下载服务定位文件。
    private String storagePath;
    // 文件字节大小，用于展示与上传限制校验。
    private Long fileSize;
    // 浏览器提交的 MIME 类型。
    private String contentType;
    // 上传该附件的用户 ID。
    private Long uploaderId;
    // 附件所属业务来源，决定详情页中的分组展示方式。
    private AttachmentBizType bizType;
    // 业务来源记录 ID，例如验收记录 ID。
    private Long bizId;
    // 附件逻辑删除标记，已删除文件不在常规详情中展示。
    @TableField("is_deleted")
    private Boolean deleted;
    // 执行附件删除操作的用户 ID。
    private Long deletedBy;
    // 附件被逻辑删除的时间。
    private LocalDateTime deletedAt;
    // 附件上传时间。
    private LocalDateTime createdAt;
    // 附件元数据最后更新时间。
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
    public AttachmentBizType getBizType() { return bizType; }
    public void setBizType(AttachmentBizType bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
