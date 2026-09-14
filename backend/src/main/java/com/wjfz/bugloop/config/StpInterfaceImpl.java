/**
 * 本文件向 Sa-Token 提供当前用户的系统角色，供后续工作空间与系统权限校验使用。
 */
package com.wjfz.bugloop.config;

import cn.dev33.satoken.stp.StpInterface;
import com.wjfz.bugloop.user.User;
import com.wjfz.bugloop.user.UserService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 角色数据源实现，一期只提供系统角色，工作空间角色在对应模块内单独校验。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final UserService userService;

    public StpInterfaceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return userService.findById(Long.valueOf(loginId.toString()))
                .map(User::getSystemRole)
                .map(List::of)
                .orElseGet(List::of);
    }
}
