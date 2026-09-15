/**
 * 本文件定义一级回复的受限请求体，仅允许提交 Markdown 内容。
 * 父评论与被回复用户均由路径参数和服务端查询决定，防止客户端伪造跨 Bug 回复关系。
 */
package com.wjfz.bugloop.bug.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 一级回复请求。 */
public record CreateBugCommentReplyRequest(
        @NotBlank(message = "回复内容不能为空")
        @Size(max = 5000, message = "回复内容最多 5000 个字符") String contentMd) {
}
