/** 本文件定义 Bug 操作日志的只读展示字段，保留操作前后值并携带操作者名称。 */
package com.wjfz.bugloop.bug.trace.vo;

import java.time.LocalDateTime;

/** Bug 操作日志展示记录。 */
public record BugOperationLogVO(
        Long id, Long operatorId, String operatorUsername, String operatorDisplayName,
        String operationType, String fieldName, String oldValue, String newValue,
        String description, LocalDateTime createdAt) {
}
