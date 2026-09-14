/**
 * 本文件暴露 Bug API Spec 第 48 节的全部接口，接收受限 DTO 并返回统一响应。
 * 权限、状态机和事务由 BugService 统一处理，控制器不能直接操作实体或 Mapper。
 */
package com.wjfz.bugloop.bug.controller;

import com.wjfz.bugloop.bug.dto.*;
import com.wjfz.bugloop.bug.service.BugService;
import com.wjfz.bugloop.bug.vo.*;
import com.wjfz.bugloop.common.api.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Bug REST 入口，所有路径均由既有 Sa-Token 拦截器保护。 */
@RestController
@RequestMapping("/api")
public class BugController {
    private final BugService service;

    /** 注入 Bug 业务服务。 */
    public BugController(BugService service) {
        this.service = service;
    }

    /** 在指定空间创建问题，返回主键和业务编号。 */
    @PostMapping("/workspaces/{workspaceId}/bugs")
    public ApiResponse<BugCreatedVO> create(@PathVariable Long workspaceId, @Valid @RequestBody CreateBugRequest request) {
        return ApiResponse.success(service.create(workspaceId, request));
    }

    /** 按空间、分页和可选筛选条件返回轻量问题列表。 */
    @GetMapping("/workspaces/{workspaceId}/bugs")
    public ApiResponse<PageResponse<BugSummaryVO>> list(@PathVariable Long workspaceId,
                                                       @Valid @ModelAttribute BugListQuery query) {
        return ApiResponse.success(service.list(workspaceId, query));
    }

    /** 校验所属空间后读取问题完整详情。 */
    @GetMapping("/bugs/{bugId}")
    public ApiResponse<BugDetailVO> get(@PathVariable Long bugId) {
        return ApiResponse.success(service.get(bugId));
    }

    /** 使用客户端读取的 version 更新标题、描述和优先级，返回新详情。 */
    @PutMapping("/bugs/{bugId}")
    public ApiResponse<BugDetailVO> update(@PathVariable Long bugId, @Valid @RequestBody UpdateBugRequest request) {
        return ApiResponse.success(service.update(bugId, request));
    }

    /** 指派有效空间成员为负责人，返回更新后的问题。 */
    @PostMapping("/bugs/{bugId}/assign")
    public ApiResponse<BugDetailVO> assign(@PathVariable Long bugId, @Valid @RequestBody AssignBugRequest request) {
        return ApiResponse.success(service.assign(bugId, request));
    }

    /** 设置有效空间成员为验收人，返回更新后的问题。 */
    @PostMapping("/bugs/{bugId}/acceptor")
    public ApiResponse<BugDetailVO> setAcceptor(@PathVariable Long bugId, @Valid @RequestBody SetBugAcceptorRequest request) {
        return ApiResponse.success(service.setAcceptor(bugId, request));
    }

    /** 当前负责人开始处理，无需请求体，返回新状态。 */
    @PostMapping("/bugs/{bugId}/start")
    public ApiResponse<BugDetailVO> start(@PathVariable Long bugId) {
        return ApiResponse.success(service.start(bugId));
    }

    /** 当前负责人保存 Markdown 修复草稿，返回更新后的问题。 */
    @PutMapping("/bugs/{bugId}/fix-description")
    public ApiResponse<BugDetailVO> saveFix(@PathVariable Long bugId, @Valid @RequestBody SaveFixDescriptionRequest request) {
        return ApiResponse.success(service.saveFixDescription(bugId, request));
    }

    /** 当前负责人提交修复验收，无需请求体，返回待验收状态。 */
    @PostMapping("/bugs/{bugId}/submit")
    public ApiResponse<BugDetailVO> submit(@PathVariable Long bugId) {
        return ApiResponse.success(service.submit(bugId));
    }

    /** 当前验收人通过验收，意见和请求体可省略，返回关闭状态及最近验收记录。 */
    @PostMapping("/bugs/{bugId}/accept")
    public ApiResponse<BugDetailVO> accept(@PathVariable Long bugId,
                                          @Valid @RequestBody(required = false) AcceptanceRequest request) {
        return ApiResponse.success(service.accept(bugId, request));
    }

    /** 当前验收人填写原因并驳回；空请求交由服务返回专用必填错误码。 */
    @PostMapping("/bugs/{bugId}/reject")
    public ApiResponse<BugDetailVO> reject(@PathVariable Long bugId,
                                          @Valid @RequestBody(required = false) AcceptanceRequest request) {
        return ApiResponse.success(service.reject(bugId, request));
    }
}
