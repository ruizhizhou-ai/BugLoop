/**
 * 本文件承接附件上传时对验收记录的最小校验数据。
 * 它只在 AttachmentService 中验证 bizId 归属和验收结果，不作为外部接口响应使用。
 */
package com.wjfz.bugloop.bug.attachment.vo;

/** 验收附件绑定所需的验收记录摘要。 */
public record AcceptanceAttachmentTarget(Long id, Long bugId, Long acceptorId, String result) {
}
