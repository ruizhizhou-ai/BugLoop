/** 本文件定义完整验收历史记录，补齐详情页只返回最近一条验收记录的限制。 */
package com.wjfz.bugloop.bug.trace.vo;

import com.wjfz.bugloop.bug.entity.BugStatus;
import com.wjfz.bugloop.bug.vo.BugAttachmentVO;
import java.time.LocalDateTime;
import java.util.List;

/** Bug 验收历史展示记录。 */
public record BugAcceptanceHistoryVO(
        Long id, Long acceptorId, String acceptorUsername, String acceptorDisplayName,
        String result, String commentMd, BugStatus fromStatus, BugStatus toStatus, LocalDateTime createdAt,
        List<BugAttachmentVO> attachments) {
    /** 将批量查出的附件绑定到对应验收记录，保持验收主信息不可变。 */
    public BugAcceptanceHistoryVO withAttachments(List<BugAttachmentVO> values) {
        return new BugAcceptanceHistoryVO(id, acceptorId, acceptorUsername, acceptorDisplayName,
                result, commentMd, fromStatus, toStatus, createdAt, values);
    }
}
