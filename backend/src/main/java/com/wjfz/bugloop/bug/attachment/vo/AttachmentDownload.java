/** 本文件在附件服务和控制器之间传递已授权下载流及安全响应元数据。 */
package com.wjfz.bugloop.bug.attachment.vo;

import org.springframework.core.io.Resource;

/** 已完成权限校验的附件下载数据。 */
public record AttachmentDownload(Resource resource, String originalName, String contentType, long fileSize) {
}
