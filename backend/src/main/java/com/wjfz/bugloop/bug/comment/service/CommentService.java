/**
 * 本文件实现 Bug 评论的读取、新增、一级回复与逻辑删除。
 * 它复用工作空间授权、用户批量查询和 Bug 操作日志，保证评论不越过空间边界且不会产生 N+1 用户查询。
 */
package com.wjfz.bugloop.bug.comment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wjfz.bugloop.bug.comment.dto.CreateBugCommentReplyRequest;
import com.wjfz.bugloop.bug.comment.dto.CreateBugCommentRequest;
import com.wjfz.bugloop.bug.comment.entity.BugComment;
import com.wjfz.bugloop.bug.comment.mapper.BugCommentMapper;
import com.wjfz.bugloop.bug.comment.vo.BugCommentVO;
import com.wjfz.bugloop.bug.entity.Bug;
import com.wjfz.bugloop.bug.mapper.BugAuditMapper;
import com.wjfz.bugloop.bug.mapper.BugMapper;
import com.wjfz.bugloop.common.api.PageResponse;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.entity.User;
import com.wjfz.bugloop.user.service.UserService;
import com.wjfz.bugloop.workspace.service.WorkspaceAccess;
import com.wjfz.bugloop.workspace.service.WorkspaceAccessService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
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
    private final UserService users;
    private final WorkspaceAccessService workspaces;

    /** 注入评论需要复用的 Bug、用户、权限和审计能力。 */
    public CommentService(BugMapper bugs, BugCommentMapper comments, BugAuditMapper audit,
                          UserService users, WorkspaceAccessService workspaces) {
        this.bugs = bugs;
        this.comments = comments;
        this.audit = audit;
        this.users = users;
        this.workspaces = workspaces;
    }

    /**
     * 分页读取已授权 Bug 的评论树节点，并批量补全评论人、被回复人和父评论删除状态。
     * 已删除评论只作为脱敏占位返回，保证子评论不会失去父子关系。
     *
     * @param bugId 目标 Bug 主键
     * @param page 从 1 开始的页码，可省略
     * @param pageSize 每页条数，可省略
     * @return 按创建时间正序排列的评论分页结果
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

        var filter = Wrappers.<BugComment>lambdaQuery().eq(BugComment::getBugId, bugId);
        long total = comments.selectCount(filter);
        long offset = (long) (actualPage - 1) * actualPageSize;
        // LIMIT/OFFSET 只拼接已经完成边界校验的整数，其余条件始终采用参数绑定。
        List<BugComment> records = comments.selectList(filter.orderByAsc(BugComment::getCreatedAt, BugComment::getId)
                .last("LIMIT " + actualPageSize + " OFFSET " + offset));
        Map<Long, User> relatedUsers = relatedUsers(records);
        Map<Long, BugComment> parents = parentComments(records);
        return new PageResponse<>(records.stream()
                .map(comment -> toView(comment, relatedUsers, parents))
                .toList(), total, actualPage, actualPageSize);
    }

    /**
     * 创建顶级评论。空间停用后禁止写入，已关闭 Bug 仍可补充讨论，与既有评论规则保持一致。
     *
     * @param bugId 目标 Bug 主键
     * @param request 顶级评论内容
     * @return 新增评论的展示数据
     */
    @Transactional
    public BugCommentVO create(Long bugId, CreateBugCommentRequest request) {
        return createComment(bugId, request.contentMd(), null, null, "ADD_COMMENT", "发表了评论");
    }

    /**
     * 对任意未删除评论创建回复。parentId 始终记录直接被回复的评论，支持多层讨论树；
     * 前端可按产品策略压缩展示层级，但服务端不丢失完整关系。
     *
     * @param bugId 目标 Bug 主键
     * @param parentCommentId 被回复的直接父评论主键
     * @param request 回复内容
     * @return 新增回复的展示数据
     */
    @Transactional
    public BugCommentVO reply(Long bugId, Long parentCommentId, CreateBugCommentReplyRequest request) {
        Bug reference = requireBug(bugId);
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(reference.getWorkspaceId());
        Bug bug = requireBugAfterWorkspaceLock(bugId, access);
        BugComment target = requireReplyTarget(parentCommentId, bugId);
        return saveComment(bug, access, request.contentMd(), target.getId(), target.getUserId(),
                "REPLY_COMMENT", "回复了评论");
    }

    /**
     * 逻辑删除评论。仅评论作者或平台 SYSTEM_ADMIN 可删除；空间 OWNER / ADMIN 不拥有代删权限，
     * 避免工作空间管理能力意外扩大为对其他成员发言的删除权。删除后回复记录仍会保留。
     *
     * @param bugId 目标 Bug 主键
     * @param commentId 评论主键
     */
    @Transactional
    public void delete(Long bugId, Long commentId) {
        Bug reference = requireBug(bugId);
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(reference.getWorkspaceId());
        Bug bug = requireBugAfterWorkspaceLock(bugId, access);
        BugComment comment = comments.selectByIdForUpdate(commentId);
        if (comment == null || !Objects.equals(comment.getBugId(), bugId) || Boolean.TRUE.equals(comment.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40406, "评论不存在或已删除");
        }
        if (!Objects.equals(comment.getUserId(), access.currentUser().getId())) {
            // 评论是用户内容，工作空间成员管理角色不应覆盖内容归属；仅平台级管理员可做全局治理。
            if (!workspaces.isSystemAdmin(access.currentUser())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "只能删除自己创建的评论");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if (comments.markDeleted(commentId, access.currentUser().getId(), now) != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "评论已被其他用户修改，请刷新后重试");
        }
        audit.insertLogAt(bug.getId(), bug.getWorkspaceId(), access.currentUser().getId(),
                "DELETE_COMMENT", null, null, null,
                access.currentUser().getDisplayName() + " 删除了评论", now);
    }

    /** 在获取工作空间锁后保存顶级评论，确保停用与写入之间不存在竞争窗口。 */
    private BugCommentVO createComment(Long bugId, String content, Long parentId, Long replyUserId,
                                       String auditType, String auditDescription) {
        Bug reference = requireBug(bugId);
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(reference.getWorkspaceId());
        Bug bug = requireBugAfterWorkspaceLock(bugId, access);
        return saveComment(bug, access, content, parentId, replyUserId, auditType, auditDescription);
    }

    /** 统一执行内容清洗、写入、审计和展示数据组装，防止顶级评论和回复出现规则漂移。 */
    private BugCommentVO saveComment(Bug bug, WorkspaceAccess access, String rawContent,
                                     Long parentId, Long replyUserId, String auditType, String auditDescription) {
        String content = normalizeContent(rawContent);
        LocalDateTime now = LocalDateTime.now();
        BugComment comment = new BugComment();
        comment.setBugId(bug.getId());
        comment.setUserId(access.currentUser().getId());
        comment.setContentMd(content);
        comment.setParentId(parentId);
        comment.setReplyUserId(replyUserId);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        comment.setDeleted(false);
        comments.insert(comment);
        audit.insertLogAt(bug.getId(), bug.getWorkspaceId(), access.currentUser().getId(),
                auditType, null, null, null,
                access.currentUser().getDisplayName() + " " + auditDescription, now);
        // 新增回复的返回体同样需要完整被回复用户名与父评论状态，不能只在后续列表刷新时才补齐。
        return toView(comment, relatedUsers(List.of(comment)), parentComments(List.of(comment)));
    }

    /** 校验直接回复目标的归属和删除状态；允许目标自身为任意层级的子评论。 */
    private BugComment requireReplyTarget(Long targetCommentId, Long bugId) {
        BugComment target = comments.selectById(targetCommentId);
        if (target == null || !Objects.equals(target.getBugId(), bugId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 42206, "被回复评论不属于当前 Bug");
        }
        if (Boolean.TRUE.equals(target.getDeleted())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "被回复评论已删除，无法继续回复");
        }
        return target;
    }

    /** 批量加载评论人与被回复人，避免评论数量增长时逐条查用户造成 N+1 查询。 */
    private Map<Long, User> relatedUsers(Collection<BugComment> commentRecords) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (BugComment comment : commentRecords) {
            userIds.add(comment.getUserId());
            if (comment.getReplyUserId() != null) {
                userIds.add(comment.getReplyUserId());
            }
        }
        return users.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 批量读取父评论（含已删除记录），供回复项展示“原评论已删除”而非逐条回查。 */
    private Map<Long, BugComment> parentComments(Collection<BugComment> commentRecords) {
        Set<Long> parentIds = commentRecords.stream()
                .map(BugComment::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return comments.selectBatchIds(parentIds).stream()
                .collect(Collectors.toMap(BugComment::getId, Function.identity()));
    }

    /** 将实体与批量读取的用户、父评论数据转换为对外视图。 */
    private BugCommentVO toView(BugComment comment, Map<Long, User> relatedUsers,
                                Map<Long, BugComment> parents) {
        User author = relatedUsers.get(comment.getUserId());
        User replyUser = relatedUsers.get(comment.getReplyUserId());
        BugComment parent = comment.getParentId() == null ? null : parents.get(comment.getParentId());
        boolean parentDeleted = comment.getParentId() != null
                && (parent == null || Boolean.TRUE.equals(parent.getDeleted()));
        boolean deleted = Boolean.TRUE.equals(comment.getDeleted());
        return new BugCommentVO(comment.getId(), comment.getBugId(), comment.getUserId(),
                author == null ? null : author.getUsername(),
                author == null ? "已注销用户" : author.getDisplayName(), null,
                // 删除评论不回传原正文，既保留树结构又避免前端意外展示已删除内容。
                deleted ? null : comment.getContentMd(), comment.getParentId(), comment.getReplyUserId(),
                replyUser == null ? null : replyUser.getUsername(), parentDeleted, deleted, comment.getCreatedAt());
    }

    /** 去除两端空白、限制字节数，并拒绝所有原始 HTML，避免 Markdown 预览承载脚本或事件属性。 */
    private String normalizeContent(String rawContent) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty()) {
            throw invalid("评论内容不能为空");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_COMMENT_BYTES) {
            throw invalid("评论内容超过允许大小（60KB）");
        }
        if (!Jsoup.clean(content, Safelist.none()).equals(content)) {
            throw invalid("评论不支持原始 HTML 标签");
        }
        return content;
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
