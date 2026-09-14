/**
 * 本文件暴露 Workspace API Spec 定义的工作空间、成员、角色和停用接口。
 */
package com.wjfz.bugloop.workspace;

import com.wjfz.bugloop.common.api.ApiResponse;
import com.wjfz.bugloop.workspace.dto.AddWorkspaceMemberRequest;
import com.wjfz.bugloop.workspace.dto.CreateWorkspaceRequest;
import com.wjfz.bugloop.workspace.dto.UpdateWorkspaceMemberRoleRequest;
import com.wjfz.bugloop.workspace.dto.UpdateWorkspaceRequest;
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
 * 工作空间 REST 接口，所有业务规则委托给 WorkspaceService。
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * 创建工作空间，当前用户自动成为 OWNER。
     *
     * @param request 创建参数
     * @return 新工作空间
     */
    @PostMapping
    public ApiResponse<WorkspaceVO> create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return ApiResponse.success(workspaceService.create(request));
    }

    /**
     * 查询当前用户可切换的工作空间。
     *
     * @return 我的工作空间列表
     */
    @GetMapping
    public ApiResponse<List<WorkspaceVO>> listMine() {
        return ApiResponse.success(workspaceService.listMine());
    }

    /**
     * 查询并切换到目标工作空间，调用方应把返回的 workspaceId 保存在前端 Store 或 URL。
     *
     * @param workspaceId 工作空间主键
     * @return 工作空间详情
     */
    @GetMapping("/{workspaceId}")
    public ApiResponse<WorkspaceVO> get(@PathVariable Long workspaceId) {
        return ApiResponse.success(workspaceService.get(workspaceId));
    }

    /**
     * 修改工作空间名称和描述，OWNER、ADMIN 或 SYSTEM_ADMIN 可执行。
     *
     * @param workspaceId 工作空间主键
     * @param request 修改参数
     * @return 修改后的工作空间
     */
    @PutMapping("/{workspaceId}")
    public ApiResponse<WorkspaceVO> update(
            @PathVariable Long workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        return ApiResponse.success(workspaceService.update(workspaceId, request));
    }

    /**
     * 查询工作空间成员。
     *
     * @param workspaceId 工作空间主键
     * @return 成员列表
     */
    @GetMapping("/{workspaceId}/members")
    public ApiResponse<List<WorkspaceMemberVO>> listMembers(@PathVariable Long workspaceId) {
        return ApiResponse.success(workspaceService.listMembers(workspaceId));
    }

    /**
     * 添加工作空间成员。
     *
     * @param workspaceId 工作空间主键
     * @param request 成员信息
     * @return 新成员
     */
    @PostMapping("/{workspaceId}/members")
    public ApiResponse<WorkspaceMemberVO> addMember(
            @PathVariable Long workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request) {
        return ApiResponse.success(workspaceService.addMember(workspaceId, request));
    }

    /**
     * 修改工作空间成员角色。
     *
     * @param workspaceId 工作空间主键
     * @param userId 目标用户主键
     * @param request 新角色
     * @return 修改后的成员
     */
    @PutMapping("/{workspaceId}/members/{userId}/role")
    public ApiResponse<WorkspaceMemberVO> updateMemberRole(
            @PathVariable Long workspaceId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateWorkspaceMemberRoleRequest request) {
        return ApiResponse.success(workspaceService.updateMemberRole(workspaceId, userId, request));
    }

    /**
     * 移除工作空间成员。
     *
     * @param workspaceId 工作空间主键
     * @param userId 目标用户主键
     * @return 空响应
     */
    @DeleteMapping("/{workspaceId}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long workspaceId, @PathVariable Long userId) {
        workspaceService.removeMember(workspaceId, userId);
        return ApiResponse.success(null);
    }

    /**
     * 停用工作空间并保留历史数据。
     *
     * @param workspaceId 工作空间主键
     * @return 停用后的工作空间
     */
    @PostMapping("/{workspaceId}/disable")
    public ApiResponse<WorkspaceVO> disable(@PathVariable Long workspaceId) {
        return ApiResponse.success(workspaceService.disable(workspaceId));
    }
}
