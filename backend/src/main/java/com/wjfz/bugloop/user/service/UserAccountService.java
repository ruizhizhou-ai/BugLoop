/**
 * 本文件集中创建系统账号并处理密码哈希、默认状态和用户名冲突，供公开注册与管理员创建入口复用。
 */
package com.wjfz.bugloop.user.service;

import cn.dev33.satoken.secure.BCrypt;
import com.wjfz.bugloop.common.exception.BusinessException;
import com.wjfz.bugloop.user.entity.User;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 用户账号创建服务，保证所有入口使用相同的密码存储和唯一性错误语义。
 */
@Service
public class UserAccountService {

    private final UserService userService;

    public UserAccountService(UserService userService) {
        this.userService = userService;
    }

    /**
     * 创建一个启用状态的系统账号，密码只保存 BCrypt 哈希。
     *
     * @param username 登录用户名
     * @param displayName 展示名称
     * @param password 原始密码
     * @param systemRole 由可信业务入口决定的系统角色
     * @return 创建完成的用户实体
     */
    public User create(String username, String displayName, String password, String systemRole) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setSystemRole(systemRole);
        user.setEnabled(true);

        try {
            return userService.save(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, 40905, "用户名已存在");
        }
    }
}
