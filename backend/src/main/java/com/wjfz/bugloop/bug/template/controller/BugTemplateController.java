/**
 * 本文件暴露个人 Bug 模板的创建、列表、编辑和逻辑删除接口。
 * 控制器只负责 HTTP 协议转换，工作空间与创建人隔离规则均由 BugTemplateService 执行。
 */
package com.wjfz.bugloop.bug.template.controller;

import com.wjfz.bugloop.bug.template.dto.CreateBugTemplateRequest;
import com.wjfz.bugloop.bug.template.dto.UpdateBugTemplateRequest;
import com.wjfz.bugloop.bug.template.service.BugTemplateService;
import com.wjfz.bugloop.bug.template.vo.BugTemplateVO;
import com.wjfz.bugloop.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bug 个人模板 REST 入口，所有接口均要求登录并由服务校验模板个人归属。
 */
@RestController
@RequestMapping("/api")
public class BugTemplateController {

    private final BugTemplateService templateService;

    /**
     * 注入个人模板业务服务。
     *
     * @param templateService 模板业务服务
     */
    public BugTemplateController(BugTemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * 查询当前用户在指定工作空间内创建且未删除的个人模板。
     *
     * @param workspaceId 当前工作空间主键
     * @return 当前用户的个人模板列表
     */
    @GetMapping("/workspaces/{workspaceId}/bug-templates")
    public ApiResponse<List<BugTemplateVO>> list(@PathVariable Long workspaceId) {
        return ApiResponse.success(templateService.list(workspaceId));
    }

    /**
     * 在指定工作空间创建当前用户自己的个人模板。
     *
     * @param workspaceId 当前工作空间主键
     * @param request 模板基础内容
     * @return 新建模板
     */
    @PostMapping("/workspaces/{workspaceId}/bug-templates")
    public ApiResponse<BugTemplateVO> create(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateBugTemplateRequest request) {
        return ApiResponse.success(templateService.create(workspaceId, request));
    }

    /**
     * 修改当前用户自己创建的个人模板。
     *
     * @param templateId 模板主键
     * @param request 允许更新的模板字段
     * @return 更新后的模板
     */
    @PutMapping("/bug-templates/{templateId}")
    public ApiResponse<BugTemplateVO> update(
            @PathVariable Long templateId,
            @Valid @RequestBody UpdateBugTemplateRequest request) {
        return ApiResponse.success(templateService.update(templateId, request));
    }

    /**
     * 逻辑删除当前用户自己创建的个人模板。
     *
     * @param templateId 模板主键
     * @return 空成功响应
     */
    @DeleteMapping("/bug-templates/{templateId}")
    public ApiResponse<Void> delete(@PathVariable Long templateId) {
        templateService.delete(templateId);
        return ApiResponse.success(null);
    }
}
