/** 本文件定义新增 Bug 评论的受限请求体，避免客户端传入操作者和创建时间等审计字段。 */
package com.wjfz.bugloop.bug.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增评论请求，仅接收 Markdown 原文。 */
public record CreateBugCommentRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 5000, message = "评论内容最多 5000 个字符") String contentMd) {
}
