/**
 * 本文件定义 Markdown 正文图片的生命周期状态，供草稿上传、Bug 创建绑定和过期清理共用。
 */
package com.wjfz.bugloop.bug.markdownimage.entity;

/** Markdown 正文图片状态。 */
public enum DraftImageStatus {
    /** 图片已上传但尚未随 Bug 创建绑定。 */
    DRAFT,
    /** 图片已绑定到 Bug 正文，按该 Bug 的读取权限访问。 */
    BOUND
}
