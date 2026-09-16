/**
 * 本文件实现注册、登录和登出业务，是登录会话的唯一入口。
 */
package com.wjfz.bugloop.auth.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.wjfz.bugloop.auth.dto.LoginRequest;
import com.wjfz.bugloop.auth.dto.RegisterRequest;
import com.wjfz.bugloop.auth.vo.LoginResponse;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.entity.User;
import com.wjfz.bugloop.user.service.UserAccountService;
import com.wjfz.bugloop.user.service.UserService;
import com.wjfz.bugloop.user.vo.UserVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务，负责账号注册、登录校验与登录会话管理。
 */
@Service
public class AuthService {

    private static final String ROLE_USER = "USER";

    private final UserService userService;
    private final UserAccountService accountService;

    public AuthService(UserService userService, UserAccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    /**
     * 注册新用户并直接建立登录会话。自助注册一律是普通用户，
     * 系统管理员由内置账号初始化或管理员创建提供。
     *
     * @param request 注册请求，参数校验已由 Controller 完成
     * @return 登录结果，包含 token 与用户信息
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        User user = accountService.create(
                request.username(), request.displayName(), request.password(), ROLE_USER);

        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), UserVO.from(user));
    }

    /**
     * 校验用户名与密码并建立登录会话。
     *
     * @param request 登录请求
     * @return 登录结果，包含 token 与用户信息
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userService.findByUsername(request.username()).orElseThrow(this::credentialsError);
        if (!BCrypt.checkpw(request.password(), user.getPasswordHash())) {
            throw credentialsError();
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40305, "用户已被禁用");
        }

        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), UserVO.from(user));
    }

    /**
     * 清除当前登录会话，Token 立即失效。
     */
    public void logout() {
        StpUtil.logout();
    }

    private BusinessException credentialsError() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, 40102, "用户名或密码错误");
    }
}
