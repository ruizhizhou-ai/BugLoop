/** 本文件暴露 API Spec 49.3～49.5 及补充的验收历史只读接口。 */
package com.wjfz.bugloop.bug.trace.controller;

import com.wjfz.bugloop.bug.trace.service.BugTraceService;
import com.wjfz.bugloop.bug.trace.vo.BugAcceptanceHistoryVO;
import com.wjfz.bugloop.bug.trace.vo.BugDescriptionHistoryDetailVO;
import com.wjfz.bugloop.bug.trace.vo.BugDescriptionHistoryVO;
import com.wjfz.bugloop.bug.trace.vo.BugOperationLogVO;
import com.wjfz.bugloop.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bug 历史与审计 REST 入口。 */
@RestController
@RequestMapping("/api/bugs/{bugId}")
public class BugTraceController {
    private final BugTraceService service;

    /** 注入追溯查询服务。 */
    public BugTraceController(BugTraceService service) {
        this.service = service;
    }

    /** 读取操作日志，默认按创建时间倒序。 */
    @GetMapping("/logs")
    public ApiResponse<List<BugOperationLogVO>> logs(@PathVariable Long bugId) {
        return ApiResponse.success(service.logs(bugId));
    }

    /** 读取 Markdown 历史版本列表。 */
    @GetMapping("/description-history")
    public ApiResponse<List<BugDescriptionHistoryVO>> descriptionHistory(@PathVariable Long bugId) {
        return ApiResponse.success(service.descriptionHistory(bugId));
    }

    /** 读取指定 Markdown 历史版本的正文，不提供恢复入口。 */
    @GetMapping("/description-history/{versionNo}")
    public ApiResponse<BugDescriptionHistoryDetailVO> descriptionHistoryDetail(
            @PathVariable Long bugId, @PathVariable Integer versionNo) {
        return ApiResponse.success(service.descriptionHistoryDetail(bugId, versionNo));
    }

    /** 读取完整验收历史，补充详情接口中的最近一次验收记录。 */
    @GetMapping("/acceptances")
    public ApiResponse<List<BugAcceptanceHistoryVO>> acceptances(@PathVariable Long bugId) {
        return ApiResponse.success(service.acceptances(bugId));
    }
}
