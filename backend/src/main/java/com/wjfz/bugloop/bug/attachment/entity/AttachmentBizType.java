/**
 * 本文件定义统一附件的业务来源，用同一张 bug_attachment 表关联创建、处理、验收和评论等业务记录。
 * 具体业务服务必须校验 bizId 确实属于当前 Bug，不能仅信任客户端传入的来源信息。
 */
package com.wjfz.bugloop.bug.attachment.entity;

/** 附件所属业务动作类型。 */
public enum AttachmentBizType {
    /** Bug Markdown 问题描述中引用的图片；仅用于详情聚合展示，不允许通过普通附件接口写入。 */
    BUG_DESCRIPTION,
    /** 创建 Bug 时随提单补传的附件。 */
    BUG_CREATE,
    /** Bug 处理过程补充的附件，当前以 Bug 本身作为业务上下文。 */
    BUG_PROCESS,
    /** 验收驳回记录的证据附件。 */
    ACCEPT_REJECT,
    /** 验收通过记录的证据附件。 */
    ACCEPT_PASS,
    /** 评论或回复下的附件。 */
    COMMENT
}
