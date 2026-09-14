/** 本文件为 Bug 列表和详情提供脱敏用户摘要，防止用户实体中的认证信息进入 API。 */
package com.wjfz.bugloop.bug.vo;

import com.wjfz.bugloop.user.entity.User;

/**
 * Bug 相关人员。
 * @param id 用户主键
 * @param username 登录用户名
 * @param displayName 显示名称
 */
public record BugUserVO(Long id, String username, String displayName) {
    /** 将已查出的用户转换为摘要，未指定负责人时返回 null。 */
    public static BugUserVO from(User user) {
        return user == null ? null : new BugUserVO(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
