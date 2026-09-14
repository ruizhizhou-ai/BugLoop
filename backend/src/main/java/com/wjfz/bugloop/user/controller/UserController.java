/**
 * 本文件暴露当前登录用户信息接口。
 */
package com.wjfz.bugloop.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.wjfz.bugloop.common.api.ApiResponse;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.entity.User;
import com.wjfz.bugloop.user.service.UserService;
import com.wjfz.bugloop.user.vo.UserVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户接口。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
}
