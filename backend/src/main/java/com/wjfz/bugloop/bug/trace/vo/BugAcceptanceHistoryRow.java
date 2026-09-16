/**
 * 本文件承接验收历史基础查询结果，避免 MyBatis 在构造带附件集合的对外 VO 时发生嵌套集合映射。
 * BugTraceService 会使用批量附件查询把本记录转换成 BugAcceptanceHistoryVO。
 */
package com.wjfz.bugloop.bug.trace.vo;

import com.wjfz.bugloop.bug.entity.BugStatus;
import java.time.LocalDateTime;

/** 验收历史的数据库查询行，不直接暴露给接口。 */
public record BugAcceptanceHistoryRow(
        Long id, Long acceptorId, String acceptorUsername, String acceptorDisplayName,
        String result, String commentMd, BugStatus fromStatus, BugStatus toStatus, LocalDateTime createdAt) {
    /** 创建尚未附加附件的对外验收历史记录。 */
    public BugAcceptanceHistoryVO toView() {
        return new BugAcceptanceHistoryVO(id, acceptorId, acceptorUsername, acceptorDisplayName,
                result, commentMd, fromStatus, toStatus, createdAt, java.util.List.of());
    }
}
