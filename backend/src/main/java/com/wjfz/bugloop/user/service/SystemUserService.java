/**
 * 本文件实现系统管理员的用户查询与创建流程，并在服务层建立不可绕过的平台角色边界。
 */
package com.wjfz.bugloop.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.dto.CreateUserRequest;
import com.wjfz.bugloop.user.entity.User;
import com.wjfz.bugloop.user.vo.UserVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统用户管理服务，仅允许 SYSTEM_ADMIN 查询全量账号并创建普通用户。
 */
@Service
public class SystemUserService {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String STANDARD_USER = "USER";

    private final UserService userService;
    private final UserAccountService accountService;

    public SystemUserService(UserService userService, UserAccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    /**
     * 查询系统全部用户，按创建时间倒序返回且不包含密码哈希。
     *
     * @return 用户列表
     */
    @Transactional(readOnly = true)
    public List<UserVO> listUsers() {
        requireSystemAdmin();
        return userService.findAll().stream().map(UserVO::from).toList();
    }

    /**
     * 由系统管理员创建普通用户。该流程不会改变管理员当前会话，也不参与首用户管理员引导。
     *
     * @param request 新用户资料和初始密码
     * @return 新建用户
     */
    @Transactional
    public UserVO createUser(CreateUserRequest request) {
        requireSystemAdmin();
        return UserVO.from(accountService.create(
                request.username(), request.displayName(), request.password(), STANDARD_USER));
    }

    /** 校验当前会话对应启用的系统管理员，防止仅靠前端隐藏入口造成越权。 */
    private User requireSystemAdmin() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED, 40101, "登录状态已失效，请重新登录"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40305, "用户已被禁用");
        }
        if (!SYSTEM_ADMIN.equals(user.getSystemRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "仅系统管理员可以管理系统用户");
        }
        return user;
    }
}
