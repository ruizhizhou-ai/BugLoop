/** 本文件定义 Bug 详情的最近验收记录，反映实际操作者、意见和状态流转。 */
package com.wjfz.bugloop.bug.vo;

import com.wjfz.bugloop.bug.entity.BugStatus;
import java.time.LocalDateTime;

/** 最近验收记录，result 为 PASS 或 REJECT。 */
public record BugAcceptanceVO(
        Long id, Long acceptorId, String result, String commentMd,
        BugStatus fromStatus, BugStatus toStatus, LocalDateTime createdAt) {
}
