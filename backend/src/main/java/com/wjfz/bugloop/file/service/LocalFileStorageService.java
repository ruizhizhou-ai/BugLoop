/**
 * 本文件提供附件本地文件系统存储，所有文件以 UUID 命名并限制在配置根目录内。
 * 附件业务负责白名单和权限，本服务只负责安全落盘、读取及回滚清理。
 */
package com.wjfz.bugloop.file.service;

import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.file.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 本地附件文件存储服务。 */
@Service
public class LocalFileStorageService {
    private final Path root;

    /** 解析并规范化根目录，后续每次解析都校验不能逃逸该目录。 */
    public LocalFileStorageService(StorageProperties properties) {
        if (properties.getRoot() == null || properties.getRoot().isBlank()) {
            throw new IllegalStateException("未配置附件存储根目录");
        }
        this.root = Path.of(properties.getRoot()).toAbsolutePath().normalize();
    }

    /**
     * 将上传文件存入 workspace/bug/年月目录，真实文件名使用 UUID 避免同名覆盖。
     * @param file 已完成业务校验的上传文件
     * @param workspaceId 所属工作空间
     * @param bugId 所属 Bug
     * @param extension 已校验的小写扩展名
     * @return 仅供数据库保存的内部存储信息
     */
    public StoredFile store(MultipartFile file, Long workspaceId, Long bugId, String extension) {
        LocalDate today = LocalDate.now();
        String storageName = UUID.randomUUID() + "." + extension;
        Path relativePath = Path.of(workspaceId.toString(), bugId.toString(),
                String.valueOf(today.getYear()), "%02d".formatted(today.getMonthValue()), storageName);
        Path target = resolve(relativePath.toString());
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw storageFailure("附件保存失败", exception);
        }
        return new StoredFile(storageName, relativePath.toString().replace('\\', '/'));
    }

    /**
     * 将尚未绑定 Bug 的 Markdown 图片存入工作空间草稿目录。
     * 草稿目录与普通附件目录隔离，避免创建失败或用户放弃编辑时伪造不存在的 Bug 归属。
     *
     * @param file 已完成图片校验的上传文件
     * @param workspaceId 所属工作空间
     * @param extension 已校验的小写图片扩展名
     * @return 仅供草稿图片元数据保存的内部存储信息
     */
    public StoredFile storeDraftImage(MultipartFile file, Long workspaceId, String extension) {
        LocalDate today = LocalDate.now();
        String storageName = UUID.randomUUID() + "." + extension;
        Path relativePath = Path.of(workspaceId.toString(), "draft-images",
                String.valueOf(today.getYear()), "%02d".formatted(today.getMonthValue()), storageName);
        Path target = resolve(relativePath.toString());
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw storageFailure("正文图片保存失败", exception);
        }
        return new StoredFile(storageName, relativePath.toString().replace('\\', '/'));
    }

    /** 根据数据库保存的相对路径打开文件，文件缺失时返回业务性 404。 */
    public Resource load(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40404, "附件文件不存在");
        }
        return new FileSystemResource(path);
    }

    /** 事务回滚时尝试清理刚写入但未能落库的文件，清理失败不覆盖原始业务异常。 */
    public void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
            // 仅用于回滚补偿，保留原始异常并由运维按存储目录定期巡检残留文件。
        }
    }

    /** 解析相对路径并防止数据库异常值或路径穿越访问根目录外文件。 */
    private Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "附件存储路径不合法");
        }
        return path;
    }

    /** 将底层 IO 异常转换为不泄露文件系统细节的统一业务错误。 */
    private BusinessException storageFailure(String message, IOException exception) {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50000, message);
    }
}
