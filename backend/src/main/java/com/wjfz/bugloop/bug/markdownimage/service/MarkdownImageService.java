/**
 * 本文件实现 Bug Markdown 正文图片的草稿上传、受控读取、创建绑定和过期清理。
 * 它与普通附件服务并行工作：普通附件必须已有 Bug，正文图片允许在创建 Bug 前上传并在同一事务内绑定。
 */
package com.wjfz.bugloop.bug.markdownimage.service;

import com.wjfz.bugloop.bug.attachment.vo.AttachmentDownload;
import com.wjfz.bugloop.bug.markdownimage.entity.BugDraftImage;
import com.wjfz.bugloop.bug.markdownimage.entity.DraftImageStatus;
import com.wjfz.bugloop.bug.markdownimage.mapper.BugDraftImageMapper;
import com.wjfz.bugloop.bug.markdownimage.vo.BugDraftImageVO;
import com.wjfz.bugloop.bug.vo.BugAttachmentVO;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.file.service.LocalFileStorageService;
import com.wjfz.bugloop.file.service.StoredFile;
import com.wjfz.bugloop.workspace.service.WorkspaceAccess;
import com.wjfz.bugloop.workspace.service.WorkspaceAccessService;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Bug Markdown 正文图片业务服务。 */
@Service
public class MarkdownImageService {
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_DRAFT_IMAGES = 20;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final Map<String, String> IMAGE_CONTENT_TYPES = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "gif", "image/gif", "webp", "image/webp");
    // 只识别本服务生成的相对 URL，禁止借由外链或伪造地址绑定其他用户的草稿图片。
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile(
            "!\\[[^\\]]*]\\(/api/bug-draft-images/(\\d+)/content\\)");

    private final BugDraftImageMapper images;
    private final WorkspaceAccessService workspaces;
    private final LocalFileStorageService storage;

    /** 注入草稿图片元数据、工作空间授权和本地文件存储服务。 */
    public MarkdownImageService(BugDraftImageMapper images, WorkspaceAccessService workspaces,
                                LocalFileStorageService storage) {
        this.images = images;
        this.workspaces = workspaces;
        this.storage = storage;
    }

    /**
     * 上传一张尚未绑定 Bug 的 Markdown 图片，并返回编辑器可直接插入的相对 URL。
     *
     * @param workspaceId 当前工作空间
     * @param file 用户在 Markdown 编辑器中选择的图片
     * @return 图片 ID、受控读取地址和 Markdown 片段
     */
    @Transactional
    public BugDraftImageVO upload(Long workspaceId, MultipartFile file) {
        WorkspaceAccess access = workspaces.requireWritableMemberForUpdate(workspaceId);
        if (images.countActiveDrafts(workspaceId, access.currentUser().getId()) >= MAX_DRAFT_IMAGES) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "最多保留20张待绑定正文图片");
        }
        ImageMetadata metadata = validateImage(file);
        StoredFile stored = storage.storeDraftImage(file, workspaceId, metadata.extension());
        registerRollbackCleanup(stored.relativePath());

        LocalDateTime now = LocalDateTime.now();
        BugDraftImage image = new BugDraftImage();
        image.setWorkspaceId(workspaceId);
        image.setUploaderId(access.currentUser().getId());
        image.setOriginalName(metadata.originalName());
        image.setStorageName(stored.storageName());
        image.setStoragePath(stored.relativePath());
        image.setFileSize(file.getSize());
        image.setContentType(metadata.contentType());
        image.setStatus(DraftImageStatus.DRAFT);
        image.setExpiresAt(now.plusHours(24));
        image.setCreatedAt(now);
        image.setUpdatedAt(now);
        image.setDeleted(false);
        images.insert(image);
        return toVO(image.getId());
    }

    /**
     * 读取 Markdown 图片文件流。草稿阶段仅上传人可读取，绑定后则按所属工作空间的查看权限读取。
     *
     * @param imageId 图片主键
     * @return 用于 inline 响应的文件资源和元数据
     */
    @Transactional(readOnly = true)
    public AttachmentDownload content(Long imageId) {
        BugDraftImage image = requireActiveImage(imageId);
        WorkspaceAccess access = workspaces.requireReadable(image.getWorkspaceId());
        if (image.getStatus() == DraftImageStatus.DRAFT
                && !access.currentUser().getId().equals(image.getUploaderId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "无权查看其他用户的草稿正文图片");
        }
        return new AttachmentDownload(storage.load(image.getStoragePath()), image.getOriginalName(),
                image.getContentType(), image.getFileSize());
    }

    /**
     * 返回已绑定正文图片的统一附件视图；调用方必须先完成 Bug 的工作空间读取授权。
     * 此处只负责投影，不提供删除能力，避免删除附件列表中的图片后留下失效 Markdown 引用。
     *
     * @param bugId 已完成授权校验的 Bug 主键
     * @return 可与普通附件合并展示的正文图片元数据
     */
    @Transactional(readOnly = true)
    public List<BugAttachmentVO> boundAttachmentViews(Long bugId) {
        return images.selectBoundAttachmentViews(bugId);
    }

    /**
     * 在创建 Bug 的事务内绑定 Markdown 中实际引用的草稿图片。
     * 任一图片不属于当前用户、当前空间或已过期时，整个 Bug 创建会回滚，避免正文产生失效图片。
     *
     * @param workspaceId 新建 Bug 所属空间
     * @param uploaderId 创建 Bug 的当前用户
     * @param bugId 已插入的 Bug 主键
     * @param descriptionMd 本次入库的 Markdown 描述
     */
    @Transactional
    public void bindReferencedImages(Long workspaceId, Long uploaderId, Long bugId, String descriptionMd) {
        List<Long> imageIds = extractImageIds(descriptionMd);
        if (imageIds.isEmpty()) {
            return;
        }
        List<BugDraftImage> referencedImages = images.selectActiveByIdsForUpdate(imageIds);
        if (referencedImages.size() != imageIds.size()) {
            throw invalid("正文引用了不存在的草稿图片");
        }
        LocalDateTime now = LocalDateTime.now();
        for (BugDraftImage image : referencedImages) {
            if (!workspaceId.equals(image.getWorkspaceId()) || !uploaderId.equals(image.getUploaderId())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "正文图片不属于当前用户或工作空间");
            }
            if (image.getStatus() != DraftImageStatus.DRAFT
                    || image.getExpiresAt() == null || !image.getExpiresAt().isAfter(now)) {
                throw invalid("正文图片已过期或已被绑定");
            }
            if (images.bindToBug(image.getId(), bugId, now) != 1) {
                throw new BusinessException(HttpStatus.CONFLICT, 40902, "正文图片状态已变更，请刷新后重试");
            }
        }
    }

    /**
     * 每小时清理一批未绑定且到期的草稿图片；数据库条件更新成功后才删除物理文件，避免并发创建误删。
     */
    @Scheduled(cron = "${bugloop.draft-image.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void cleanupExpiredDrafts() {
        LocalDateTime now = LocalDateTime.now();
        for (BugDraftImage image : images.selectExpiredDrafts(now, CLEANUP_BATCH_SIZE)) {
            if (images.markExpiredDraftDeleted(image.getId(), now) == 1) {
                registerCommitCleanup(image.getStoragePath());
            }
        }
    }

    /** 从 Markdown 中提取由本服务生成的去重图片 ID，未引用的草稿图片仍交给过期清理处理。 */
    private List<Long> extractImageIds(String markdown) {
        Set<Long> imageIds = new LinkedHashSet<>();
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            try {
                imageIds.add(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException exception) {
                throw invalid("正文图片地址不合法");
            }
        }
        return List.copyOf(imageIds);
    }

    /** 校验图片大小、文件名和扩展名，并由服务端根据扩展名确定安全的响应 MIME 类型。 */
    private ImageMetadata validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("请选择要上传的非空图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw invalid("单张正文图片不能超过5MB");
        }
        String originalName = safeFileName(file.getOriginalFilename());
        int dot = originalName.lastIndexOf('.');
        if (dot <= 0 || dot == originalName.length() - 1) {
            throw invalid("正文图片必须使用允许的文件扩展名");
        }
        String extension = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = IMAGE_CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw invalid("正文图片仅支持 PNG、JPG、GIF 或 WEBP 格式");
        }
        return new ImageMetadata(originalName, extension, contentType);
    }

    /** 仅允许普通文件名，拒绝路径穿越和控制字符进入 Markdown 或下载响应头。 */
    private String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("正文图片文件名不能为空");
        }
        String normalized = value.replace('\\', '/');
        String fileName = Path.of(normalized).getFileName().toString();
        if (!normalized.equals(fileName) || fileName.length() > 255
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw invalid("正文图片文件名不合法");
        }
        return fileName;
    }

    /** 读取未逻辑删除的图片，统一转换为稳定的 404 语义。 */
    private BugDraftImage requireActiveImage(Long imageId) {
        BugDraftImage image = images.selectActiveById(imageId);
        if (image == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40404, "正文图片不存在");
        }
        return image;
    }

    /** 将上传落盘与数据库插入绑定，事务回滚时移除无法追踪的草稿文件。 */
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

    /** 仅在清理记录成功提交后删除物理文件，数据库回滚时保留原文件供后续任务重试。 */
    private void registerCommitCleanup(String relativePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.deleteQuietly(relativePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteQuietly(relativePath);
            }
        });
    }

    /** 统一正文图片参数错误码。 */
    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, 40001, message);
    }

    /** 已校验的安全图片元数据。 */
    private record ImageMetadata(String originalName, String extension, String contentType) {
    }

    /** 将草稿图片 ID 组装为前端可插入的相对 URL。 */
    private BugDraftImageVO toVO(Long imageId) {
        String url = "/api/bug-draft-images/%d/content".formatted(imageId);
        return new BugDraftImageVO(imageId, url, "![图片](%s)".formatted(url));
    }
}
