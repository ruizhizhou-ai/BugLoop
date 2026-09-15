/** 本文件定义单个 Markdown 历史版本详情，第一期仅支持读取而不提供恢复操作。 */
package com.wjfz.bugloop.bug.trace.vo;

import java.time.LocalDateTime;

/** Markdown 历史版本详情。 */
public record BugDescriptionHistoryDetailVO(
        Long id, Integer versionNo, String contentMd, Long operatorId, String operatorUsername,
        String operatorDisplayName, LocalDateTime createdAt) {
}
