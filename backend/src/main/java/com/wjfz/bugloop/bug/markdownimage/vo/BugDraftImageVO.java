/**
 * 本文件定义 Markdown 草稿图片上传接口的安全响应，不包含任何内部存储路径。
 */
package com.wjfz.bugloop.bug.markdownimage.vo;

/** Markdown 草稿图片上传结果。 */
public record BugDraftImageVO(
        Long id,
        String url,
        String markdown) {
}
