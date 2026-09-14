/** 本文件定义分页列表的轻量 Bug 摘要，不加载 Markdown 大字段和附件。 */
package com.wjfz.bugloop.bug.vo;

import com.wjfz.bugloop.bug.entity.*;
import java.time.LocalDateTime;

/** 列表记录包含责任人标识和可展示的用户摘要，version 用于后续基础信息更新。 */
public record BugSummaryVO(
        Long id, Long workspaceId, String bugNo, String title, BugPriority priority, BugStatus status,
        Long creatorId, Long assigneeId, Long acceptorId,
        BugUserVO creator, BugUserVO assignee, BugUserVO acceptor,
        int reopenCount, int version, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime closedAt) {
    /** 将已完成空间权限校验的实体和批量读取的人员摘要组合为列表记录。 */
    public static BugSummaryVO from(Bug bug, BugUserVO creator, BugUserVO assignee, BugUserVO acceptor) {
        return new BugSummaryVO(bug.getId(), bug.getWorkspaceId(), bug.getBugNo(), bug.getTitle(),
                bug.getPriority(), bug.getStatus(), bug.getCreatorId(), bug.getAssigneeId(), bug.getAcceptorId(),
                creator, assignee, acceptor, bug.getReopenCount(), bug.getVersion(),
                bug.getCreatedAt(), bug.getUpdatedAt(), bug.getClosedAt());
    }
}
