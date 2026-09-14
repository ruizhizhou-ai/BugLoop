/** 本文件组合 Bug 当前快照、空间、相关人员和关联元数据，供详情及更新结果使用。 */
package com.wjfz.bugloop.bug.vo;

import com.wjfz.bugloop.bug.entity.*;
import com.wjfz.bugloop.workspace.vo.WorkspaceVO;
import java.time.LocalDateTime;
import java.util.List;

/** 完整 Bug 响应；Markdown 保留原文，渲染方必须使用安全渲染器。 */
public record BugDetailVO(
        Long id, Long workspaceId, String bugNo, String title, String descriptionMd,
        BugPriority priority, BugStatus status,
        Long creatorId, Long assigneeId, Long acceptorId, String fixDescriptionMd,
        int reopenCount, int version, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime closedAt,
        WorkspaceVO workspace, BugUserVO creator, BugUserVO assignee, BugUserVO acceptor,
        List<BugAttachmentVO> attachments, BugAcceptanceVO latestAcceptance) {
    /** 将已授权的实体、空间和关联数据组合成详情，避免 Controller 暴露持久化实体。 */
    public static BugDetailVO from(
            Bug b, WorkspaceVO workspace, BugUserVO creator, BugUserVO assignee, BugUserVO acceptor,
            List<BugAttachmentVO> attachments, BugAcceptanceVO latestAcceptance) {
        return new BugDetailVO(b.getId(), b.getWorkspaceId(), b.getBugNo(), b.getTitle(), b.getDescriptionMd(),
                b.getPriority(), b.getStatus(), b.getCreatorId(), b.getAssigneeId(), b.getAcceptorId(),
                b.getFixDescriptionMd(), b.getReopenCount(), b.getVersion(), b.getCreatedAt(), b.getUpdatedAt(),
                b.getClosedAt(), workspace, creator, assignee, acceptor, attachments, latestAcceptance);
    }
}
