/** 本文件定义完整验收历史记录，补齐详情页只返回最近一条验收记录的限制。 */
package com.wjfz.bugloop.bug.trace.vo;

import com.wjfz.bugloop.bug.entity.BugStatus;
import java.time.LocalDateTime;

/** Bug 验收历史展示记录。 */
public record BugAcceptanceHistoryVO(
        Long id, Long acceptorId, String acceptorUsername, String acceptorDisplayName,
        String result, String commentMd, BugStatus fromStatus, BugStatus toStatus, LocalDateTime createdAt) {
}
