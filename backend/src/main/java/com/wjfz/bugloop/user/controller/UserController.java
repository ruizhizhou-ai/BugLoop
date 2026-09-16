/**
 * 本文件暴露当前登录用户信息接口。
 */
package com.wjfz.bugloop.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.wjfz.bugloop.common.api.ApiResponse;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.entity.User;
import com.wjfz.bugloop.user.dto.CreateUserRequest;
import com.wjfz.bugloop.user.service.SystemUserService;
import com.wjfz.bugloop.user.service.UserService;
import com.wjfz.bugloop.user.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户接口。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final SystemUserService systemUserService;

    public UserController(UserService userService, SystemUserService systemUserService) {
        this.userService = userService;
        this.systemUserService = systemUserService;
    }

    /**
     * 返回当前登录用户信息，前端刷新页面后用于恢复登录状态。
     *
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserVO> currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, 40101, "登录状态已失效，请重新登录"));
        return ApiResponse.success(UserVO.from(user));
    }

    /**
     * 查询系统用户列表，仅供 SYSTEM_ADMIN 使用。
     *
     * @return 不包含密码信息的用户列表
     */
    @GetMapping
    public ApiResponse<List<UserVO>> listUsers() {
        return ApiResponse.success(systemUserService.listUsers());
    }

    /**
     * 创建一个普通用户，不改变当前系统管理员的登录会话。
     *
     * @param request 用户资料和初始密码
     * @return 新建用户
     */
    @PostMapping
    public ApiResponse<UserVO> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(systemUserService.createUser(request));
    }

    /**
     * 停用普通用户账号，停用后无法登录且在线会话立即失效。
     *
     * @param userId 目标用户主键
     * @return 更新后的用户
     */
    @PostMapping("/{userId}/disable")
    public ApiResponse<UserVO> disableUser(@PathVariable Long userId) {
        return ApiResponse.success(systemUserService.disable(userId));
    }

    /**
     * 重新启用普通用户账号，恢复登录能力。
     *
     * @param userId 目标用户主键
     * @return 更新后的用户
     */
    @PostMapping("/{userId}/enable")
    public ApiResponse<UserVO> enableUser(@PathVariable Long userId) {
        return ApiResponse.success(systemUserService.enable(userId));
    }
}
