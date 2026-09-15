/**
 * 本文件实现评论的读取与追加，并复用工作空间锁保证停用状态下不会产生新评论。
 * 关闭 Bug 仍允许评论，符合一期详情页“只保留查看、评论”的业务约定。
 */
package com.wjfz.bugloop.bug.comment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wjfz.bugloop.bug.comment.dto.CreateBugCommentRequest;
import com.wjfz.bugloop.bug.comment.entity.BugComment;
import com.wjfz.bugloop.bug.comment.mapper.BugCommentMapper;
import com.wjfz.bugloop.bug.comment.vo.BugCommentVO;
import com.wjfz.bugloop.bug.entity.Bug;
import com.wjfz.bugloop.bug.mapper.BugAuditMapper;
import com.wjfz.bugloop.bug.mapper.BugMapper;
import com.wjfz.bugloop.common.api.PageResponse;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.workspace.service.WorkspaceAccess;
import com.wjfz.bugloop.workspace.service.WorkspaceAccessService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bug 评论业务服务。 */
@Service
public class CommentService {
    private static final int MAX_COMMENT_BYTES = 60 * 1024;

    private final BugMapper bugs;
    private final BugCommentMapper comments;
    private final BugAuditMapper audit;
    private final WorkspaceAccessService workspaces;

    /** 注入 Bug 定位、评论持久化、操作日志和空间授权服务。 */
    public CommentService(BugMapper bugs, BugCommentMapper comments, BugAuditMapper audit,
                          WorkspaceAccessService workspaces) {
        this.bugs = bugs;
        this.comments = comments;
        this.audit = audit;
        this.workspaces = workspaces;
    }

    /**
     * 分页读取已授权 Bug 的评论，当前空间停用时仍可查看历史评论。
     * @param bugId 目标 Bug 主键
     * @param page 从 1 开始的页码，可省略
     * @param pageSize 每页条数，可省略
     * @return 评论分页结果
     */
    @Transactional(readOnly = true)
    public PageResponse<BugCommentVO> list(Long bugId, Integer page, Integer pageSize) {
        Bug bug = requireBug(bugId);
        workspaces.requireReadable(bug.getWorkspaceId());
        int actualPage = page == null ? 1 : page;
        int actualPageSize = pageSize == null ? 20 : pageSize;
        if (actualPage < 1 || actualPageSize < 1 || actualPageSize > 100) {
            throw invalid("页码必须为正数，每页数量必须在 1～100 之间");
        }
        long total = comments.selectCount(Wrappers.<BugComment>lambdaQuery()
                .eq(BugComment::getBugId, bugId));
        long offset = (long) (actualPage - 1) * actualPageSize;
        return new PageResponse<>(comments.selectPageByBugId(bugId, actualPageSize, offset),
                total, actualPage, actualPageSize);
    }

    /**
     * 创建评论并记录可追溯操作日志；空间锁串行化停用和写入，避免停用后仍产生评论。
     * @param bugId 目标 Bug 主键
     * @param request Markdown 评论内容
     * @return 新增评论的展示数据
     */
    @Transactional
    public BugCommentVO create(Long bugId, CreateBugCommentRequest request) {
        Bug reference = requireBug(bugId);
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(reference.getWorkspaceId());
        Bug bug = requireBugAfterWorkspaceLock(bugId, access);
        String content = request.contentMd().trim();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_COMMENT_BYTES) {
            throw invalid("评论内容超过允许大小（60KB）");
        }
        LocalDateTime now = LocalDateTime.now();
        BugComment comment = new BugComment();
        comment.setBugId(bugId);
        comment.setUserId(access.currentUser().getId());
        comment.setContentMd(content);
        comment.setCreatedAt(now);
        comments.insert(comment);
        audit.insertLogAt(bug.getId(), bug.getWorkspaceId(), access.currentUser().getId(),
                "ADD_COMMENT", null, null, null,
                access.currentUser().getDisplayName() + " 发表了评论", now);
        return comments.selectViewById(comment.getId());
    }

    /** 读取 Bug 并将不存在情况转换为稳定业务错误码。 */
    private Bug requireBug(Long bugId) {
        Bug bug = bugs.selectById(bugId);
        if (bug == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "Bug 不存在");
        }
        return bug;
    }

    /** 空间加锁后重新读取 Bug，确保不会把跨空间的旧快照用于写入。 */
    private Bug requireBugAfterWorkspaceLock(Long bugId, WorkspaceAccess access) {
        Bug bug = requireBug(bugId);
        if (!bug.getWorkspaceId().equals(access.workspace().getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "数据已被其他用户修改，请刷新后重试");
        }
        return bug;
    }

    /** 返回统一参数错误。 */
    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, 40001, message);
    }
}
