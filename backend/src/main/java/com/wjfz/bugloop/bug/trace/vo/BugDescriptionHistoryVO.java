/** 本文件定义 Markdown 历史版本列表的轻量元数据，列表不返回正文以控制响应体大小。 */
package com.wjfz.bugloop.bug.trace.vo;

import java.time.LocalDateTime;

/** Markdown 历史版本摘要。 */
public record BugDescriptionHistoryVO(
        Long id, Integer versionNo, Long operatorId, String operatorUsername,
        String operatorDisplayName, LocalDateTime createdAt) {
}
