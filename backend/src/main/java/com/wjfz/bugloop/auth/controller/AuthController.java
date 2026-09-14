/**
 * 本文件暴露注册、登录和登出接口。
 */
package com.wjfz.bugloop.auth.controller;

import com.wjfz.bugloop.auth.dto.LoginRequest;
import com.wjfz.bugloop.auth.dto.RegisterRequest;
import com.wjfz.bugloop.auth.service.AuthService;
import com.wjfz.bugloop.auth.vo.LoginResponse;
import com.wjfz.bugloop.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口，登录与注册开放访问，登出要求已登录。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册账号并直接返回登录结果。
     *
     * @param request 注册请求
     * @return 登录结果
     */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    /**
     * 使用用户名和密码登录。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 退出登录并清除服务端会话。
     *
     * @return 空响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(null);
    }
}
