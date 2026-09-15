/** 本文件定义评论列表和创建结果，携带操作者展示信息而不暴露用户认证字段。 */
package com.wjfz.bugloop.bug.comment.vo;

import java.time.LocalDateTime;

/** Bug 评论对外视图。 */
public record BugCommentVO(
        Long id, Long userId, String username, String displayName, String contentMd, LocalDateTime createdAt) {
}
