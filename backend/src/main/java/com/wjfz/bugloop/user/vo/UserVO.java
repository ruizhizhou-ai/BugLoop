/**
 * 本文件定义对外返回的用户信息，避免密码哈希等敏感字段进入响应。
 */
package com.wjfz.bugloop.user.vo;

import com.wjfz.bugloop.user.entity.User;

import java.time.LocalDateTime;

/**
 * 用户信息响应对象。
 *
 * @param id 用户主键
 * @param username 登录用户名
 * @param displayName 展示名称
 * @param systemRole 系统角色，取值为 SYSTEM_ADMIN 或 USER
 * @param enabled 是否启用
 * @param createdAt 注册时间
 */
public record UserVO(Long id, String username, String displayName, String systemRole,
                     Boolean enabled, LocalDateTime createdAt) {

    /**
     * 由实体转换为响应对象。
     *
     * @param user 系统用户实体
     * @return 不含敏感字段的用户信息
     */
    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getDisplayName(), user.getSystemRole(),
                user.getEnabled(), user.getCreatedAt());
    }
}
