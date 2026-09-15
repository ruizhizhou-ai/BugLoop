/** 本文件定义评论列表和创建结果，携带操作者展示信息而不暴露用户认证字段。 */
package com.wjfz.bugloop.bug.comment.vo;

import java.time.LocalDateTime;

/**
 * Bug 评论对外视图，包含直接回复关系和逻辑删除占位状态。
 * avatar 当前预留给未来用户头像资料；一期前端会在其为空时使用显示名称首字母作为稳定回退。
 */
public record BugCommentVO(
        Long commentId, Long bugId, Long userId, String username, String displayName, String avatar,
        String contentMd, Long parentId, Long replyUserId, String replyUsername,
        boolean parentDeleted, boolean deleted, LocalDateTime createdAt) {
}
