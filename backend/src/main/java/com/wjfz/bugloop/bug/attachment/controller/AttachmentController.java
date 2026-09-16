/** 本文件暴露 API Spec 50.1、50.3、50.4 的附件上传、下载和逻辑删除接口。 */
package com.wjfz.bugloop.bug.attachment.controller;

import com.wjfz.bugloop.bug.attachment.service.AttachmentService;
import com.wjfz.bugloop.bug.attachment.entity.AttachmentBizType;
import com.wjfz.bugloop.bug.attachment.vo.AttachmentDownload;
import com.wjfz.bugloop.bug.vo.BugAttachmentVO;
import com.wjfz.bugloop.bug.markdownimage.service.MarkdownImageService;
import com.wjfz.bugloop.bug.markdownimage.vo.BugDraftImageVO;
import com.wjfz.bugloop.common.api.ApiResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 附件 REST 入口；下载接口返回二进制流，其余接口使用统一 JSON 响应。 */
@RestController
@RequestMapping("/api")
public class AttachmentController {
    private final AttachmentService service;
    private final MarkdownImageService markdownImages;

    /** 注入附件业务服务。 */
    public AttachmentController(AttachmentService service, MarkdownImageService markdownImages) {
        this.service = service;
        this.markdownImages = markdownImages;
    }

    /** 上传一个符合白名单和数量限制的附件，并绑定到创建、验收、评论等真实业务记录。 */
    @PostMapping(path = "/bugs/{bugId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BugAttachmentVO> upload(@PathVariable Long bugId, @RequestPart("file") MultipartFile file,
                                                @RequestParam(required = false) AttachmentBizType bizType,
                                                @RequestParam(required = false) Long bizId) {
        return ApiResponse.success(service.upload(bugId, file, bizType, bizId));
    }

    /** 上传创建 Bug 前的 Markdown 草稿图片，返回编辑器可插入的受控图片地址。 */
    @PostMapping(path = "/workspaces/{workspaceId}/bug-draft-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BugDraftImageVO> uploadMarkdownImage(@PathVariable Long workspaceId,
                                                             @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(markdownImages.upload(workspaceId, file));
    }

    /** 以内联方式返回 Markdown 正文图片，读取权限由草稿或绑定 Bug 的空间边界决定。 */
    @GetMapping("/bug-draft-images/{imageId}/content")
    public ResponseEntity<Resource> markdownImageContent(@PathVariable Long imageId) {
        AttachmentDownload content = markdownImages.content(imageId);
        return ResponseEntity.ok()
                .contentType(parseMediaType(content.contentType()))
                .contentLength(content.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(content.resource());
    }

    /** 下载已经过工作空间权限校验的附件。 */
    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId) {
        AttachmentDownload download = service.download(attachmentId);
        MediaType mediaType = parseMediaType(download.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(download.resource());
    }

    /** 逻辑删除附件并保留操作审计记录。 */
    @DeleteMapping("/attachments/{attachmentId}")
    public ApiResponse<Void> delete(@PathVariable Long attachmentId) {
        service.delete(attachmentId);
        return ApiResponse.success(null);
    }

    /** 浏览器上传的 Content-Type 不可信，格式非法时回退为通用二进制流。 */
    private MediaType parseMediaType(String contentType) {
        try {
            return contentType == null || contentType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
