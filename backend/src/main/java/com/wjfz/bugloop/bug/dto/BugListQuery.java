/** 本文件定义 Bug 列表的分页及筛选条件，日期按服务器本地日历日查询创建时间。 */
package com.wjfz.bugloop.bug.dto;

import com.wjfz.bugloop.bug.entity.BugPriority;
import com.wjfz.bugloop.bug.entity.BugStatus;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

/**
 * 列表查询条件，未指定分页时默认第一页、每页 20 条。
 * startDate/endDate 均包含当天；非法日期范围在服务层返回参数错误。
 */
public record BugListQuery(
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize,
        @Size(max = 200) String keyword,
        BugStatus status,
        BugPriority priority,
        @Positive Long assigneeId,
        @Positive Long creatorId,
        @Positive Long acceptorId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
}
