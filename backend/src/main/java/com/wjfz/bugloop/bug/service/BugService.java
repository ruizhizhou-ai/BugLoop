/**
 * 本文件实现 Bug API 第 48 节的查询、编辑、保存个人模板、人员指派与处理验收闭环。
 * 每次写入先按“工作空间 → Bug”顺序校验和读取，再使用版本条件更新；历史、验收和审计共用事务。
 */
package com.wjfz.bugloop.bug.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wjfz.bugloop.bug.dto.*;
import com.wjfz.bugloop.bug.attachment.entity.AttachmentBizType;
import com.wjfz.bugloop.bug.entity.*;
import com.wjfz.bugloop.bug.mapper.*;
import com.wjfz.bugloop.bug.markdownimage.service.MarkdownImageService;
import com.wjfz.bugloop.bug.template.dto.SaveBugAsTemplateRequest;
import com.wjfz.bugloop.bug.template.service.BugTemplateService;
import com.wjfz.bugloop.bug.template.vo.BugTemplateVO;
import com.wjfz.bugloop.bug.vo.*;
import com.wjfz.bugloop.common.api.PageResponse;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.entity.User;
import com.wjfz.bugloop.user.service.UserService;
import com.wjfz.bugloop.workspace.service.*;
import com.wjfz.bugloop.workspace.vo.WorkspaceVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Bug 业务入口；控制器只转换协议，不能直接持久化或修改状态。 */
@Service
public class BugService {
    private final BugMapper bugs;
    private final BugAuditMapper audit;
    private final BugAccessService permissions;
    private final WorkspaceAccessService workspaces;
    private final UserService users;
    private final BugTemplateService templates;
    private final MarkdownImageService markdownImages;

    /** 注入持久化、审计、空间授权和用户服务。 */
    public BugService(BugMapper bugs, BugAuditMapper audit, BugAccessService permissions,
                      WorkspaceAccessService workspaces, UserService users, BugTemplateService templates,
                      MarkdownImageService markdownImages) {
        this.bugs = bugs;
        this.audit = audit;
        this.permissions = permissions;
        this.workspaces = workspaces;
        this.users = users;
        this.templates = templates;
        this.markdownImages = markdownImages;
    }

    /**
     * 创建 Bug，补全工作空间标题前缀、默认值并生成全局编号。
     * @param workspaceId 所属工作空间
     * @param request 标题、Markdown、优先级和可选人员
     * @return 主键和业务编号
     */
    @Transactional
    public BugCreatedVO create(Long workspaceId, CreateBugRequest request) {
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(workspaceId);
        // 创建者必须实际属于空间，SYSTEM_ADMIN 的跨空间查看权限不代替成员关系。
        permissions.requireActiveMember(workspaceId, access.currentUser().getId());
        validateMarkdown(request.descriptionMd(), 100 * 1024, "问题描述");
        if (request.assigneeId() != null) {
            permissions.requireActiveMember(workspaceId, request.assigneeId());
        }
        Long acceptorId = request.acceptorId() == null ? access.currentUser().getId() : request.acceptorId();
        permissions.requireActiveMember(workspaceId, acceptorId);
        Bug bug = new Bug();
        bug.setWorkspaceId(workspaceId);
        // 自增主键由数据库跨空间分配；临时编号仅存在于未提交事务中，不对外暴露。
        bug.setBugNo(UUID.randomUUID().toString().replace("-", ""));
        // 标题中的项目名只能由已校验的工作空间上下文确定，防止客户端伪造或遗漏归属前缀。
        bug.setTitle(buildTitleWithWorkspacePrefix(access.workspace().getName(), request.title()));
        bug.setDescriptionMd(request.descriptionMd());
        bug.setPriority(request.priority() == null ? BugPriority.P2 : request.priority());
        bug.setStatus(BugStatus.TODO);
        bug.setCreatorId(access.currentUser().getId());
        bug.setAssigneeId(request.assigneeId());
        bug.setAcceptorId(acceptorId);
        bug.setReopenCount(0);
        bug.setVersion(0);
        bug.setCreatedAt(LocalDateTime.now());
        bug.setUpdatedAt(bug.getCreatedAt());
        bugs.insert(bug);
        bug.setBugNo(String.format(Locale.ROOT, "BUG-%06d", bug.getId()));
        bugs.setBugNo(bug.getId(), bug.getBugNo());
        // 正文仅能绑定当前用户在当前空间上传且仍有效的草稿图片，任一异常都会回滚本次创建。
        markdownImages.bindReferencedImages(workspaceId, access.currentUser().getId(), bug.getId(), request.descriptionMd());
        log(bug, access, "CREATE_BUG", null, null, bug.getBugNo(), "创建了 " + bug.getBugNo());
        return new BugCreatedVO(bug.getId(), bug.getBugNo());
    }

    /**
     * 按空间边界分页筛选 Bug，批量组装相关人员，避免 N+1 查询及加载 Markdown 大字段。
     * @param workspaceId 当前工作空间
     * @param query 分页、关键字、人员、状态、优先级和创建日期
     * @return 统一分页响应
     */
    @Transactional(readOnly = true)
    public PageResponse<BugSummaryVO> list(Long workspaceId, BugListQuery query) {
        workspaces.requireReadable(workspaceId);
        int page = query.page() == null ? 1 : query.page();
        int pageSize = query.pageSize() == null ? 20 : query.pageSize();
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw invalid("页码必须为正数，每页数量必须在 1～100 之间");
        }
        if (query.startDate() != null && query.endDate() != null
                && query.startDate().isAfter(query.endDate())) {
            throw invalid("开始日期不能晚于结束日期");
        }
        if (LocalDate.MAX.equals(query.endDate())) {
            throw invalid("结束日期超出支持范围");
        }
        LambdaQueryWrapper<Bug> filter = Wrappers.<Bug>lambdaQuery()
                .eq(Bug::getWorkspaceId, workspaceId)
                .eq(query.status() != null, Bug::getStatus, query.status())
                .eq(query.priority() != null, Bug::getPriority, query.priority())
                .eq(query.assigneeId() != null, Bug::getAssigneeId, query.assigneeId())
                .eq(query.creatorId() != null, Bug::getCreatorId, query.creatorId())
                .eq(query.acceptorId() != null, Bug::getAcceptorId, query.acceptorId());
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = query.keyword().trim();
            filter.and(part -> part.like(Bug::getBugNo, keyword).or().like(Bug::getTitle, keyword));
        }
        if (query.startDate() != null) {
            filter.ge(Bug::getCreatedAt, query.startDate().atStartOfDay());
        }
        if (query.endDate() != null) {
            filter.lt(Bug::getCreatedAt, query.endDate().plusDays(1).atStartOfDay());
        }
        long total = bugs.selectCount(filter);
        // LIMIT/OFFSET 仅拼接校验后的数值，其余筛选条件均通过 MyBatis 绑定参数。
        long offset = ((long) page - 1) * pageSize;
        List<Bug> records = bugs.selectList(filter
                .select(Bug::getId, Bug::getWorkspaceId, Bug::getBugNo, Bug::getTitle, Bug::getPriority,
                        Bug::getStatus, Bug::getCreatorId, Bug::getAssigneeId, Bug::getAcceptorId,
                        Bug::getReopenCount, Bug::getVersion, Bug::getCreatedAt, Bug::getUpdatedAt, Bug::getClosedAt)
                .orderByDesc(Bug::getUpdatedAt, Bug::getId)
                .last("LIMIT " + pageSize + " OFFSET " + offset));
        Map<Long, User> relatedUsers = relatedUsers(records);
        return new PageResponse<>(records.stream().map(b -> BugSummaryVO.from(b,
                BugUserVO.from(relatedUsers.get(b.getCreatorId())),
                BugUserVO.from(relatedUsers.get(b.getAssigneeId())),
                BugUserVO.from(relatedUsers.get(b.getAcceptorId())))).toList(), total, page, pageSize);
    }

    /** 根据 Bug 所属空间校验权限，再返回当前快照、人员、未删除附件和最近验收记录。 */
    @Transactional(readOnly = true)
    public BugDetailVO get(Long bugId) {
        Bug bug = requireBug(bugId);
        return detail(bug, workspaces.requireReadable(bug.getWorkspaceId()));
    }

    /**
     * 将当前空间中的 Bug 保存为当前操作者的个人模板。
     * 普通成员只能保存自己创建的 Bug；系统管理员和空间管理员可保存当前空间任意 Bug，
     * 但始终只复制模板字段白名单，并把新模板归属到当前操作者。
     *
     * @param bugId 来源 Bug 主键
     * @param request 模板名称及允许保存的基础字段
     * @return 新建个人模板
     */
    @Transactional
    public BugTemplateVO saveAsTemplate(Long bugId, SaveBugAsTemplateRequest request) {
        Bug reference = requireBug(bugId);
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(reference.getWorkspaceId());
        // 取得空间写锁后重新读取 Bug，避免等待锁期间保存到已变化或已删除的来源快照。
        Bug sourceBug = bugs.selectByIdForUpdate(bugId);
        if (sourceBug == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "Bug 不存在");
        }
        if (!Objects.equals(sourceBug.getWorkspaceId(), access.workspace().getId())) {
            throw versionConflict();
        }
        permissions.requireCanSaveAsTemplate(sourceBug, access);
        return templates.createFromBug(sourceBug, access.currentUser().getId(), request);
    }

    /**
     * 修改基础信息，同时保存旧描述与字段审计；旧 version 请求即使内容相同也应返回冲突。
     * @param bugId 目标 Bug
     * @param request 三个允许修改的字段和旧版本号
     * @return 修改后的详情和新版本号
     */
    @Transactional
    public BugDetailVO update(Long bugId, UpdateBugRequest request) {
        WriteContext context = writable(bugId);
        Bug bug = context.bug();
        permissions.requireEditor(bug, context.access());
        requireOpen(bug);
        if (!Objects.equals(bug.getVersion(), request.version())) {
            throw versionConflict();
        }
        validateMarkdown(request.descriptionMd(), 100 * 1024, "问题描述");
        boolean changed = false;
        String title = request.title().trim();
        if (Objects.equals(title, bug.getTitle())
                && Objects.equals(request.descriptionMd(), bug.getDescriptionMd())
                && request.priority() == bug.getPriority()) {
            return detail(bug, context.access());
        }
        bug.setUpdatedAt(LocalDateTime.now());
        if (!Objects.equals(title, bug.getTitle())) {
            log(bug, context.access(), "UPDATE_TITLE", "title", bug.getTitle(), title, "将标题修改为“" + title + "”");
            bug.setTitle(title);
            changed = true;
        }
        if (!Objects.equals(request.descriptionMd(), bug.getDescriptionMd())) {
            audit.insertHistory(bug, audit.nextDescriptionVersion(bugId), bug.getDescriptionMd(),
                    context.access().currentUser().getId());
            // Markdown 可达 100KB，原文存 LONGTEXT 历史表，不重复塞入 TEXT 审计字段。
            log(bug, context.access(), "UPDATE_DESCRIPTION", "descriptionMd", null, null, "更新了问题描述并保留旧版本");
            bug.setDescriptionMd(request.descriptionMd());
            changed = true;
        }
        if (request.priority() != bug.getPriority()) {
            log(bug, context.access(), "CHANGE_PRIORITY", "priority", bug.getPriority().name(),
                    request.priority().name(), "将优先级从 " + bug.getPriority() + " 修改为 " + request.priority());
            bug.setPriority(request.priority());
            changed = true;
        }
        if (changed) {
            save(bug);
        }
        return detail(bug, context.access());
    }

    /**
     * 指派或转派负责人，仅管理员可在待处理或重新打开时执行。
     * 开始处理后负责人即承担当前修复链路，禁止中途转派以避免修复说明、提交验收与责任人脱节。
     */
    @Transactional
    public BugDetailVO assign(Long bugId, AssignBugRequest request) {
        return changePerson(bugId, request.assigneeId(), true, BugStatus.TODO, BugStatus.REOPENED);
    }

    /**
     * 调整验收人，仅管理员可在提交验收前执行。
     * 处理中允许替换验收人以应对排班变化；进入待验收后必须锁定，保证验收责任清晰且不可被绕过。
     */
    @Transactional
    public BugDetailVO setAcceptor(Long bugId, SetBugAcceptorRequest request) {
        return changePerson(bugId, request.acceptorId(), false,
                BugStatus.TODO, BugStatus.PROCESSING, BugStatus.REOPENED);
    }

    /** 当前负责人从待处理或重新打开状态开始处理。 */
    @Transactional
    public BugDetailVO start(Long bugId) {
        WriteContext context = writable(bugId);
        permissions.requireAssignee(context.bug(), context.access());
        requireStatus(context.bug(), BugStatus.TODO, BugStatus.REOPENED);
        transition(context, BugStatus.PROCESSING, "START_PROCESS");
        return detail(context.bug(), context.access());
    }

    /** 当前负责人保存或清空修复草稿，只在处理中及重新打开状态允许。 */
    @Transactional
    public BugDetailVO saveFixDescription(Long bugId, SaveFixDescriptionRequest request) {
        WriteContext context = writable(bugId);
        Bug bug = context.bug();
        permissions.requireAssignee(bug, context.access());
        requireStatus(bug, BugStatus.PROCESSING, BugStatus.REOPENED);
        validateMarkdown(request.fixDescriptionMd(), 100 * 1024, "修复说明");
        if (!Objects.equals(bug.getFixDescriptionMd(), request.fixDescriptionMd())) {
            bug.setFixDescriptionMd(request.fixDescriptionMd());
            save(bug);
            log(bug, context.access(), "UPDATE_FIX_DESCRIPTION", "fixDescriptionMd", null, null, "更新了修复说明");
        }
        return detail(bug, context.access());
    }

    /** 当前负责人提交验收，要求修复说明非空并重新校验验收人仍为有效空间成员。 */
    @Transactional
    public BugDetailVO submit(Long bugId) {
        WriteContext context = writable(bugId);
        Bug bug = context.bug();
        permissions.requireAssignee(bug, context.access());
        requireStatus(bug, BugStatus.PROCESSING);
        if (bug.getFixDescriptionMd() == null || bug.getFixDescriptionMd().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 42201, "提交验收前请填写修复说明");
        }
        if (bug.getAcceptorId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 42204, "Bug 尚未指定验收人");
        }
        permissions.requireActiveMember(bug.getWorkspaceId(), bug.getAcceptorId());
        transition(context, BugStatus.WAIT_ACCEPTANCE, "SUBMIT_ACCEPTANCE");
        return detail(bug, context.access());
    }

    /** 指定验收人通过验收；重复请求在状态校验处拒绝，不重复写验收和审计。 */
    @Transactional
    public BugDetailVO accept(Long bugId, AcceptanceRequest request) {
        return completeAcceptance(bugId, request == null ? null : request.commentMd(), true);
    }

    /** 指定验收人填写驳回原因，原子递增重新打开次数并保留本次验收记录。 */
    @Transactional
    public BugDetailVO reject(Long bugId, AcceptanceRequest request) {
        return completeAcceptance(bugId, request == null ? null : request.commentMd(), false);
    }

    /**
     * 在已开启的公共事务内统一处理人员变更，并为相同人员的重复保存返回原数据。
     * @param allowedStatuses 对应职责允许调整的状态；负责人和验收人的冻结时机不同，不能共用同一状态边界
     */
    private BugDetailVO changePerson(Long bugId, Long userId, boolean assignee, BugStatus... allowedStatuses) {
        WriteContext context = writable(bugId);
        Bug bug = context.bug();
        permissions.requireManager(context.access());
        requireStatus(bug, allowedStatuses);
        User target = permissions.requireActiveMember(bug.getWorkspaceId(), userId);
        Long previousId = assignee ? bug.getAssigneeId() : bug.getAcceptorId();
        if (!Objects.equals(previousId, userId)) {
            User previous = previousId == null ? null : users.findById(previousId).orElse(null);
            if (assignee) {
                bug.setAssigneeId(userId);
            } else {
                bug.setAcceptorId(userId);
            }
            save(bug);
            log(bug, context.access(), assignee ? "ASSIGN_USER" : "CHANGE_ACCEPTOR",
                    assignee ? "assigneeId" : "acceptorId", previousId == null ? null : previousId.toString(),
                    userId.toString(), "将" + (assignee ? "负责人" : "验收人") + "从“"
                            + (previous == null ? "未指定" : previous.getDisplayName()) + "”修改为“" + target.getDisplayName() + "”");
        }
        return detail(bug, context.access());
    }

    /** 在同一事务中完成状态更新、验收记录和日志追加，空驳回原因使用专用业务错误码。 */
    private BugDetailVO completeAcceptance(Long bugId, String comment, boolean pass) {
        WriteContext context = writable(bugId);
        Bug bug = context.bug();
        permissions.requireAcceptor(bug, context.access());
        requireStatus(bug, BugStatus.WAIT_ACCEPTANCE);
        if (!pass && (comment == null || comment.isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 42202, "验收驳回原因不能为空");
        }
        // 验收意见列为 MySQL TEXT，按 UTF-8 字节限制防止中文内容在数据库端溢出。
        validateMarkdown(comment, 60 * 1024, "验收意见");
        if (pass) {
            bug.setClosedAt(LocalDateTime.now());
        } else {
            bug.setClosedAt(null);
            bug.setReopenCount(bug.getReopenCount() + 1);
        }
        transition(context, pass ? BugStatus.CLOSED : BugStatus.REOPENED, pass ? "ACCEPT_BUG" : "REJECT_BUG");
        audit.insertAcceptance(bug, context.access().currentUser().getId(), pass ? "PASS" : "REJECT", comment);
        return detail(bug, context.access());
    }

    /** 读取 Bug 定位所属空间，按既有成员管理锁顺序锁空间后重新读取最新 Bug 快照。 */
    private WriteContext writable(Long bugId) {
        Bug reference = requireBug(bugId);
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(reference.getWorkspaceId());
        // 锁前读取仅用于定位空间；取得空间锁后必须重新读取，避免处理排队前的旧状态。
        Bug current = bugs.selectByIdForUpdate(bugId);
        if (current == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "Bug 不存在");
        }
        if (!Objects.equals(current.getWorkspaceId(), access.workspace().getId())) {
            throw versionConflict();
        }
        return new WriteContext(current, access);
    }

    /** 原子更新并递增内存版本；失败会使本事务已经插入的历史和日志一起回滚。 */
    private void save(Bug bug) {
        bug.setUpdatedAt(LocalDateTime.now());
        if (bugs.updateIfVersionMatches(bug) != 1) {
            throw versionConflict();
        }
        bug.setVersion(bug.getVersion() + 1);
    }

    /** 校验成功后修改状态并记录可读状态名称，所有调用者必须先检查业务身份与允许来源。 */
    private void transition(WriteContext context, BugStatus target, String operation) {
        Bug bug = context.bug();
        BugStatus previous = bug.getStatus();
        bug.setStatus(target);
        save(bug);
        log(bug, context.access(), operation, "status", previous.name(), target.name(),
                "将状态从“" + statusLabel(previous) + "”修改为“" + statusLabel(target) + "”");
    }

    /** 读取实体但不对外返回，随后必须执行所属空间授权。 */
    private Bug requireBug(Long bugId) {
        Bug bug = bugs.selectById(bugId);
        if (bug == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "Bug 不存在");
        }
        return bug;
    }

    /** 已关闭问题禁止基础信息修改，状态操作则使用各自的白名单。 */
    private void requireOpen(Bug bug) {
        if (bug.getStatus() == BugStatus.CLOSED) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "Bug 已关闭，不允许修改");
        }
    }

    /** 仅接受显式列出的来源状态，非法转换与重复转换均返回状态冲突。 */
    private void requireStatus(Bug bug, BugStatus... allowed) {
        if (!Arrays.asList(allowed).contains(bug.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "Bug 当前状态不允许此操作");
        }
    }

    /** 批量读取本页相关用户，null 负责人不会传入数据库查询。 */
    private Map<Long, User> relatedUsers(List<Bug> records) {
        List<Long> ids = records.stream().flatMap(b -> Stream.of(b.getCreatorId(), b.getAssigneeId(), b.getAcceptorId()))
                .filter(Objects::nonNull).distinct().toList();
        return users.findByIds(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 在已授权的空间上下文中组装详情，关联查询只使用该 Bug 主键。 */
    private BugDetailVO detail(Bug bug, WorkspaceAccess access) {
        Map<Long, User> related = relatedUsers(List.of(bug));
        List<BugAttachmentVO> attachments = Stream.concat(
                        audit.attachments(bug.getId()).stream(), markdownImages.boundAttachmentViews(bug.getId()).stream())
                // 附件列表按上传时间稳定排序，避免两张表合并后页面顺序随数据库执行计划波动。
                .sorted(Comparator.comparing(BugAttachmentVO::createdAt).thenComparing(BugAttachmentVO::id))
                // 普通附件可按上传人与空间角色删除；正文图片仅展示和下载，不能破坏 Markdown 引用。
                .map(attachment -> attachment.bizType() == AttachmentBizType.BUG_DESCRIPTION
                        ? attachment : attachment.withCanDelete(canDeleteAttachment(attachment, access)))
                .toList();
        return BugDetailVO.from(bug, WorkspaceVO.from(access.workspace(), access.currentRole()),
                BugUserVO.from(related.get(bug.getCreatorId())), BugUserVO.from(related.get(bug.getAssigneeId())),
                BugUserVO.from(related.get(bug.getAcceptorId())), attachments,
                audit.latestAcceptance(bug.getId()));
    }

    /** 判断当前成员是否可删除附件：上传人、空间管理员或系统管理员均可执行逻辑删除。 */
    private boolean canDeleteAttachment(BugAttachmentVO attachment, WorkspaceAccess access) {
        return Objects.equals(attachment.uploaderId(), access.currentUser().getId())
                || workspaces.isSystemAdmin(access.currentUser())
                || (access.currentRole() != null && access.currentRole().canManageMembers());
    }

    /** 追加业务日志，操作者来自服务端登录上下文。 */
    private void log(Bug bug, WorkspaceAccess access, String type, String field,
                     String oldValue, String newValue, String description) {
        audit.insertLog(bug, access.currentUser().getId(), type, field, oldValue, newValue,
                access.currentUser().getDisplayName() + " " + description);
    }

    /** 按 UTF-8 实际字节限制 Markdown，保留原文而不在写入时生成 HTML。 */
    private void validateMarkdown(String content, int maxBytes, String label) {
        if (content != null && content.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw invalid(label + "超过允许大小（" + maxBytes / 1024 + "KB）");
        }
    }

    /** 统一版本冲突语义。 */
    private BusinessException versionConflict() {
        return new BusinessException(HttpStatus.CONFLICT, 40902, "数据已被其他用户修改，请刷新后重试");
    }

    /** 统一参数错误语义。 */
    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, 40001, message);
    }

    /**
     * 组装对外展示的 Bug 标题，统一使用“工作空间名-问题描述”格式。
     * 保留用户填写的问题描述原文（仅去除首尾空白），并在入库前校验最终标题不会超过表字段上限。
     *
     * @param workspaceName 当前工作空间名称，作为项目标题头
     * @param issueTitle 用户填写的问题描述
     * @return 带工作空间标题头的完整 Bug 标题
     */
    private String buildTitleWithWorkspacePrefix(String workspaceName, String issueTitle) {
        String title = workspaceName.trim() + "-" + issueTitle.trim();
        if (title.length() > 200) {
            throw invalid("工作空间名称与问题描述组合后的标题不能超过 200 个字符");
        }
        return title;
    }

    /** 为操作日志提供面向用户的状态名称。 */
    private String statusLabel(BugStatus status) {
        return switch (status) {
            case TODO -> "待处理";
            case PROCESSING -> "处理中";
            case WAIT_ACCEPTANCE -> "待验收";
            case REOPENED -> "重新打开";
            case CLOSED -> "已关闭";
        };
    }

    /** 写事务内绑定 Bug 快照和已校验的空间权限，防止两者来源混用。 */
    private record WriteContext(Bug bug, WorkspaceAccess access) {
    }
}
