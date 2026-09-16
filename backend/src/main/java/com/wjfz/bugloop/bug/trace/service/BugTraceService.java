/**
 * 本文件实现 Bug 操作日志、Markdown 历史和验收历史的只读查询。
 * 所有查询先按 Bug 所属工作空间执行统一授权，防止通过 Bug 主键跨空间读取追溯数据。
 */
package com.wjfz.bugloop.bug.trace.service;

import com.wjfz.bugloop.bug.entity.Bug;
import com.wjfz.bugloop.bug.attachment.mapper.BugAttachmentMapper;
import com.wjfz.bugloop.bug.mapper.BugAuditMapper;
import com.wjfz.bugloop.bug.mapper.BugMapper;
import com.wjfz.bugloop.bug.trace.vo.BugAcceptanceHistoryVO;
import com.wjfz.bugloop.bug.trace.vo.BugAcceptanceHistoryRow;
import com.wjfz.bugloop.bug.trace.vo.BugDescriptionHistoryDetailVO;
import com.wjfz.bugloop.bug.trace.vo.BugDescriptionHistoryVO;
import com.wjfz.bugloop.bug.trace.vo.BugOperationLogVO;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.workspace.service.WorkspaceAccessService;
import com.wjfz.bugloop.workspace.service.WorkspaceAccess;
import com.wjfz.bugloop.bug.vo.BugAttachmentVO;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bug 追溯信息只读业务服务。 */
@Service
public class BugTraceService {
    private final BugMapper bugs;
    private final BugAuditMapper audit;
    private final BugAttachmentMapper attachments;
    private final WorkspaceAccessService workspaces;

    /** 注入 Bug 定位、追溯查询和空间授权服务。 */
    public BugTraceService(BugMapper bugs, BugAuditMapper audit, BugAttachmentMapper attachments,
                           WorkspaceAccessService workspaces) {
        this.bugs = bugs;
        this.audit = audit;
        this.attachments = attachments;
        this.workspaces = workspaces;
    }

    /** 返回按创建时间倒序排列的操作日志。 */
    @Transactional(readOnly = true)
    public List<BugOperationLogVO> logs(Long bugId) {
        authorizeReadable(bugId);
        return audit.operationLogs(bugId);
    }

    /** 返回 Markdown 旧版本列表，正文须通过版本详情接口单独读取。 */
    @Transactional(readOnly = true)
    public List<BugDescriptionHistoryVO> descriptionHistory(Long bugId) {
        authorizeReadable(bugId);
        return audit.descriptionHistory(bugId);
    }

    /** 返回指定旧版本 Markdown 原文；当前版本不重复写入历史表。 */
    @Transactional(readOnly = true)
    public BugDescriptionHistoryDetailVO descriptionHistoryDetail(Long bugId, Integer versionNo) {
        authorizeReadable(bugId);
        BugDescriptionHistoryDetailVO history = audit.descriptionHistoryDetail(bugId, versionNo);
        if (history == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40405, "描述历史版本不存在");
        }
        return history;
    }

    /** 返回完整验收历史，按最新实际写入记录在前排序。 */
    @Transactional(readOnly = true)
    public List<BugAcceptanceHistoryVO> acceptances(Long bugId) {
        WorkspaceAccess access = authorizeReadable(bugId);
        List<BugAcceptanceHistoryRow> records = audit.acceptanceHistory(bugId);
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> recordIds = records.stream().map(BugAcceptanceHistoryRow::id).collect(Collectors.toSet());
        // 用一次 IN 查询取回全部验收附件，再按验收记录主键分组，避免历史列表产生 N+1 查询。
        Map<Long, List<BugAttachmentVO>> attachmentsByAcceptance = attachments
                .selectActiveAcceptanceAttachments(bugId, recordIds).stream()
                .map(item -> item.withCanDelete(canDeleteAttachment(item, access)))
                .collect(Collectors.groupingBy(BugAttachmentVO::bizId));
        return records.stream().map(record -> record.toView()
                .withAttachments(attachmentsByAcceptance.getOrDefault(record.id(), List.of())))
                .toList();
    }

    /** 校验 Bug 存在和所属工作空间读取权限。 */
    private WorkspaceAccess authorizeReadable(Long bugId) {
        Bug bug = bugs.selectById(bugId);
        if (bug == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "Bug 不存在");
        }
        return workspaces.requireReadable(bug.getWorkspaceId());
    }

    /** 附件删除权限由服务端统一计算，避免前端根据角色猜测导致越权入口。 */
    private boolean canDeleteAttachment(BugAttachmentVO attachment, WorkspaceAccess access) {
        return attachment.uploaderId().equals(access.currentUser().getId())
                || workspaces.isSystemAdmin(access.currentUser())
                || (access.currentRole() != null && access.currentRole().canManageMembers());
    }
}
