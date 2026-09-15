/** 本文件暴露 API Spec 49.1、49.2 的评论列表和新增评论接口。 */
package com.wjfz.bugloop.bug.comment.controller;

import com.wjfz.bugloop.bug.comment.dto.CreateBugCommentRequest;
import com.wjfz.bugloop.bug.comment.service.CommentService;
import com.wjfz.bugloop.bug.comment.vo.BugCommentVO;
import com.wjfz.bugloop.common.api.ApiResponse;
import com.wjfz.bugloop.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bug 评论 REST 入口。 */
@RestController
@RequestMapping("/api/bugs/{bugId}/comments")
public class BugCommentController {
    private final CommentService service;

    /** 注入评论业务服务。 */
    public BugCommentController(CommentService service) {
        this.service = service;
    }

    /** 分页读取当前用户有权查看的 Bug 评论。 */
    @GetMapping
    public ApiResponse<PageResponse<BugCommentVO>> list(@PathVariable Long bugId,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(service.list(bugId, page, pageSize));
    }

    /** 为当前用户有写权限的工作空间内 Bug 追加一条评论。 */
    @PostMapping
    public ApiResponse<BugCommentVO> create(@PathVariable Long bugId,
                                             @Valid @RequestBody CreateBugCommentRequest request) {
        return ApiResponse.success(service.create(bugId, request));
    }
}
