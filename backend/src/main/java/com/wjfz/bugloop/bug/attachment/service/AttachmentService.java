/**
 * 本文件实现附件上传、下载和逻辑删除闭环。
 * 写操作先获取工作空间锁再计数或更新元数据，文件落盘失败和事务回滚均不会留下可访问的附件记录。
 */
package com.wjfz.bugloop.bug.attachment.service;

import com.wjfz.bugloop.bug.attachment.entity.BugAttachment;
import com.wjfz.bugloop.bug.attachment.entity.AttachmentBizType;
import com.wjfz.bugloop.bug.attachment.mapper.BugAttachmentMapper;
import com.wjfz.bugloop.bug.attachment.vo.AttachmentDownload;
import com.wjfz.bugloop.bug.comment.entity.BugComment;
import com.wjfz.bugloop.bug.comment.mapper.BugCommentMapper;
import com.wjfz.bugloop.bug.entity.Bug;
import com.wjfz.bugloop.bug.entity.BugStatus;
import com.wjfz.bugloop.bug.mapper.BugAuditMapper;
import com.wjfz.bugloop.bug.mapper.BugMapper;
import com.wjfz.bugloop.bug.vo.BugAttachmentVO;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.file.service.LocalFileStorageService;
import com.wjfz.bugloop.file.service.StoredFile;
import com.wjfz.bugloop.workspace.service.WorkspaceAccess;
import com.wjfz.bugloop.workspace.service.WorkspaceAccessService;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Bug 附件业务服务。 */
@Service
public class AttachmentService {
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS_PER_BUG = 20;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "pdf", "txt", "log");

    private final BugMapper bugs;
    private final BugAttachmentMapper attachments;
    private final BugAuditMapper audit;
    private final BugCommentMapper comments;
    private final WorkspaceAccessService workspaces;
    private final LocalFileStorageService storage;

    /** 注入附件元数据、Bug 授权、审计和本地文件存储服务。 */
    public AttachmentService(BugMapper bugs, BugAttachmentMapper attachments, BugAuditMapper audit,
                             BugCommentMapper comments, WorkspaceAccessService workspaces,
                             LocalFileStorageService storage) {
        this.bugs = bugs;
        this.attachments = attachments;
        this.audit = audit;
        this.comments = comments;
        this.workspaces = workspaces;
        this.storage = storage;
    }

    /**
     * 上传白名单内文件并绑定到真实业务记录；停用空间一律拒绝，关闭 Bug 仅允许验收通过和评论记录补传。
     *
     * @param bugId 目标 Bug 主键
     * @param file 上传文件
     * @param requestedBizType 客户端声明的附件来源，可为空以兼容旧客户端
     * @param requestedBizId 对应业务记录主键，可为空以兼容旧客户端的处理附件
     * @return 不含内部存储路径的附件摘要
     */
    @Transactional
    public BugAttachmentVO upload(Long bugId, MultipartFile file, AttachmentBizType requestedBizType, Long requestedBizId) {
        Bug reference = requireBug(bugId);
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(reference.getWorkspaceId());
        Bug bug = requireBugAfterWorkspaceLock(bugId, access);
        AttachmentContext context = resolveContext(bug, access, requestedBizType, requestedBizId);
        requireAttachmentWritable(bug, context.type());
        FileMetadata metadata = validateFile(file);
        if (attachments.countActiveByBugId(bugId) >= MAX_ATTACHMENTS_PER_BUG) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "单个 Bug 最多上传 20 个附件");
        }
        StoredFile stored = storage.store(file, bug.getWorkspaceId(), bugId, metadata.extension());
        registerRollbackCleanup(stored.relativePath());
        LocalDateTime now = LocalDateTime.now();
        BugAttachment attachment = new BugAttachment();
        attachment.setBugId(bugId);
        attachment.setBizType(context.type());
        attachment.setBizId(context.id());
        attachment.setOriginalName(metadata.originalName());
        attachment.setStorageName(stored.storageName());
        attachment.setStoragePath(stored.relativePath());
        attachment.setFileSize(file.getSize());
        attachment.setContentType(metadata.contentType());
        attachment.setUploaderId(access.currentUser().getId());
        attachment.setDeleted(false);
        attachment.setCreatedAt(now);
        attachment.setUpdatedAt(now);
        attachments.insert(attachment);
        audit.insertLogAt(bug.getId(), bug.getWorkspaceId(), access.currentUser().getId(),
                "ADD_ATTACHMENT", "attachment", null, metadata.originalName(),
                access.currentUser().getDisplayName() + " 上传了" + context.label() + "“" + metadata.originalName() + "”", now);
        return new BugAttachmentVO(attachment.getId(), attachment.getBugId(), attachment.getBizType(),
                attachment.getBizId(), attachment.getOriginalName(), attachment.getFileSize(), attachment.getContentType(),
                attachment.getUploaderId(), access.currentUser().getDisplayName(), null, attachment.getCreatedAt(), true);
    }

    /**
     * 校验附件所属 Bug 的空间读取权限后提供下载流；逻辑删除附件不会再暴露给下载接口。
     * @param attachmentId 附件主键
     * @return 下载所需的资源和响应头元数据
     */
    @Transactional(readOnly = true)
    public AttachmentDownload download(Long attachmentId) {
        BugAttachment attachment = requireActiveAttachment(attachmentId);
        Bug bug = requireBug(attachment.getBugId());
        workspaces.requireReadable(bug.getWorkspaceId());
        return new AttachmentDownload(storage.load(attachment.getStoragePath()), attachment.getOriginalName(),
                attachment.getContentType(), attachment.getFileSize());
    }

    /**
     * 对附件做逻辑删除并记录操作者；物理文件保留在受控目录，便于审计和故障排查。
     * @param attachmentId 附件主键
     */
    @Transactional
    public void delete(Long attachmentId) {
        BugAttachment reference = requireActiveAttachment(attachmentId);
        Bug referenceBug = requireBug(reference.getBugId());
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(referenceBug.getWorkspaceId());
        BugAttachment attachment = attachments.selectByIdForUpdate(attachmentId);
        if (attachment == null || Boolean.TRUE.equals(attachment.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40404, "附件不存在");
        }
        Bug bug = requireBugAfterWorkspaceLock(attachment.getBugId(), access);
        requireDeletePermission(attachment, access);
        LocalDateTime now = LocalDateTime.now();
        if (attachments.markDeleted(attachmentId, access.currentUser().getId(), now) != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "数据已被其他用户修改，请刷新后重试");
        }
        audit.insertLogAt(bug.getId(), bug.getWorkspaceId(), access.currentUser().getId(),
                "DELETE_ATTACHMENT", "attachment", attachment.getOriginalName(), null,
                access.currentUser().getDisplayName() + " 删除了附件“" + attachment.getOriginalName() + "”", now);
    }

    /**
     * 将客户端来源参数绑定到真实业务记录。兼容旧调用时将未声明来源的手工附件归为处理附件并关联当前 Bug。
     *
     * @param bug 已锁定并校验空间边界的 Bug
     * @param access 当前写入用户及其空间角色
     * @param requestedType 客户端声明的业务来源，可为空以兼容旧客户端
     * @param requestedId 客户端声明的业务记录主键，可为空以兼容处理附件
     * @return 已完成归属与上传人校验的业务上下文
     */
    private AttachmentContext resolveContext(Bug bug, WorkspaceAccess access, AttachmentBizType requestedType,
                                             Long requestedId) {
        AttachmentBizType type = requestedType == null ? AttachmentBizType.BUG_PROCESS : requestedType;
        Long businessId = requestedId == null ? bug.getId() : requestedId;
        switch (type) {
            case BUG_CREATE -> {
                requireSameBusinessId(businessId, bug.getId(), "提单附件必须关联当前 Bug");
                requireSameUploader(access.currentUser().getId(), bug.getCreatorId(), "只有 Bug 提交人可以补传提单附件");
            }
            case BUG_PROCESS -> requireSameBusinessId(businessId, bug.getId(), "处理附件必须关联当前 Bug");
            case ACCEPT_REJECT, ACCEPT_PASS -> validateAcceptanceContext(bug, access, type, businessId);
            case COMMENT -> validateCommentContext(bug, access, businessId);
        }
        return new AttachmentContext(type, businessId, labelFor(type));
    }

    /** 校验验收附件的记录归属、结果类型和操作者，防止把附件伪造绑定到其他验收结论。 */
    private void validateAcceptanceContext(Bug bug, WorkspaceAccess access, AttachmentBizType type, Long acceptanceId) {
        var acceptance = audit.acceptanceById(acceptanceId);
        if (acceptance == null || !bug.getId().equals(acceptance.bugId())) {
            throw invalid("验收记录不属于当前 Bug");
        }
        String expectedResult = type == AttachmentBizType.ACCEPT_REJECT ? "REJECT" : "PASS";
        if (!expectedResult.equals(acceptance.result())) {
            throw invalid("附件来源与验收结果不匹配");
        }
        requireSameUploader(access.currentUser().getId(), acceptance.acceptorId(), "只有本次验收人可以上传验收附件");
    }

    /** 校验评论附件只能被评论作者挂载到自己在当前 Bug 下的评论。 */
    private void validateCommentContext(Bug bug, WorkspaceAccess access, Long commentId) {
        BugComment comment = comments.selectById(commentId);
        if (comment == null || !bug.getId().equals(comment.getBugId())) {
            throw invalid("评论不属于当前 Bug");
        }
        requireSameUploader(access.currentUser().getId(), comment.getUserId(), "只有评论作者可以上传评论附件");
    }

    /** 上传人身份不匹配时统一返回禁止操作，避免调用方绕过来源记录的责任归属。 */
    private void requireSameUploader(Long currentUserId, Long expectedUserId, String message) {
        if (!currentUserId.equals(expectedUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, message);
        }
    }

    /** 业务来源主键必须精确匹配，避免客户端把来源类型正确但记录 ID 指向其他 Bug。 */
    private void requireSameBusinessId(Long actual, Long expected, String message) {
        if (!expected.equals(actual)) {
            throw invalid(message);
        }
    }

    /** 返回用于操作日志的来源名称，保持审计记录对业务用户可读。 */
    private String labelFor(AttachmentBizType type) {
        return switch (type) {
            case BUG_CREATE -> "提单附件";
            case BUG_PROCESS -> "处理附件";
            case ACCEPT_REJECT -> "验收驳回附件";
            case ACCEPT_PASS -> "验收附件";
            case COMMENT -> "评论附件";
        };
    }

    /** 校验文件大小、名称和扩展名白名单，扩展名白名单可同时拒绝可执行文件。 */
    private FileMetadata validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("请选择要上传的非空附件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw invalid("单个附件不能超过20MB");
        }
        String originalName = safeFileName(file.getOriginalFilename());
        int dot = originalName.lastIndexOf('.');
        if (dot <= 0 || dot == originalName.length() - 1) {
            throw invalid("附件必须使用允许的文件扩展名");
        }
        String extension = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw invalid("不支持该附件类型");
        }
        String contentType = file.getContentType();
        return new FileMetadata(originalName, extension, contentType == null ? null : contentType.trim());
    }

    /** 仅接受文件名本身，拒绝路径片段和控制字符，避免下载响应头及存储元数据被污染。 */
    private String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("附件文件名不能为空");
        }
        String normalized = value.replace('\\', '/');
        String fileName = Path.of(normalized).getFileName().toString();
        if (!normalized.equals(fileName) || fileName.length() > 255 || fileName.chars().anyMatch(Character::isISOControl)) {
            throw invalid("附件文件名不合法");
        }
        return fileName;
    }

    /** 回滚时删除刚落盘但未成功提交元数据的文件，避免产生不可追踪的存储孤儿。 */
    private void registerRollbackCleanup(String relativePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.deleteQuietly(relativePath);
                }
            }
        });
    }

    /**
     * 关闭 Bug 后普通处理、提单附件不可再改变；通过验收和评论附件属于已发生记录，允许记录责任人补传。
     */
    private void requireAttachmentWritable(Bug bug, AttachmentBizType type) {
        if (bug.getStatus() == BugStatus.CLOSED
                && type != AttachmentBizType.ACCEPT_PASS && type != AttachmentBizType.COMMENT) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "Bug 已关闭，不允许修改附件");
        }
    }

    /** 删除附件仅允许上传者或具备空间成员管理权限的管理员，逻辑删除不会影响其原始业务记录。 */
    private void requireDeletePermission(BugAttachment attachment, WorkspaceAccess access) {
        boolean manager = workspaces.isSystemAdmin(access.currentUser())
                || (access.currentRole() != null && access.currentRole().canManageMembers());
        if (!access.currentUser().getId().equals(attachment.getUploaderId()) && !manager) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "只能删除自己上传的附件");
        }
    }

    /** 读取仍未逻辑删除的附件。 */
    private BugAttachment requireActiveAttachment(Long attachmentId) {
        BugAttachment attachment = attachments.selectById(attachmentId);
        if (attachment == null || Boolean.TRUE.equals(attachment.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40404, "附件不存在");
        }
        return attachment;
    }

    /** 读取 Bug 并返回稳定不存在错误。 */
    private Bug requireBug(Long bugId) {
        Bug bug = bugs.selectById(bugId);
        if (bug == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "Bug 不存在");
        }
        return bug;
    }

    /** 工作空间加锁后重新读取 Bug，确保附件仍属于已授权空间。 */
    private Bug requireBugAfterWorkspaceLock(Long bugId, WorkspaceAccess access) {
        Bug bug = requireBug(bugId);
        if (!bug.getWorkspaceId().equals(access.workspace().getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "数据已被其他用户修改，请刷新后重试");
        }
        return bug;
    }

    /** 统一输入错误响应。 */
    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, 40001, message);
    }

    /** 已校验文件的安全展示名、扩展名和浏览器声明类型。 */
    private record FileMetadata(String originalName, String extension, String contentType) {
    }

    /** 已校验来源类型、业务主键及用于审计展示的中文名称。 */
    private record AttachmentContext(AttachmentBizType type, Long id, String label) {
    }
}
