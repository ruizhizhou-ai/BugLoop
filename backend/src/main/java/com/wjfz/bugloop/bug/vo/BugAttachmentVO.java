/** 本文件定义详情中的未删除附件摘要，隐藏磁盘存储路径与真实文件名。 */
package com.wjfz.bugloop.bug.vo;

import java.time.LocalDateTime;

/** 附件展示元数据；文件上传下载仍由独立附件 API 负责。 */
public record BugAttachmentVO(
        Long id, String originalName, Long fileSize, String contentType, Long uploaderId, LocalDateTime createdAt) {
}
