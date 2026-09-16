/** 本文件定义详情中的未删除附件摘要，隐藏磁盘存储路径与真实文件名。 */
package com.wjfz.bugloop.bug.vo;

import com.wjfz.bugloop.bug.attachment.entity.AttachmentBizType;
import java.time.LocalDateTime;

/** 附件展示元数据；文件上传下载仍由独立附件 API 负责。 */
public record BugAttachmentVO(
        Long id, Long bugId, AttachmentBizType bizType, Long bizId,
        String originalName, Long fileSize, String contentType,
        Long uploaderId, String uploaderName, String uploaderAvatar,
        LocalDateTime createdAt, boolean canDelete) {
    /** 按当前访问人的权限复制附件视图，避免前端自行推断上传人或空间角色。 */
    public BugAttachmentVO withCanDelete(boolean permitted) {
        return new BugAttachmentVO(id, bugId, bizType, bizId, originalName, fileSize, contentType,
                uploaderId, uploaderName, uploaderAvatar, createdAt, permitted);
    }
}
