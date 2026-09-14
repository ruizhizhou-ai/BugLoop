/**
 * 本文件定义工作空间成员列表响应，组合成员角色与系统用户展示信息。
 */
package com.wjfz.bugloop.workspace;

import com.wjfz.bugloop.user.User;

import java.time.LocalDateTime;

/**
 * 工作空间成员响应对象，不包含用户密码等敏感字段。
 *
 * @param userId 用户主键
 * @param username 用户名
 * @param displayName 展示名称
 * @param role 工作空间角色
 * @param enabled 系统账号是否启用
 * @param joinedAt 加入时间
 */
public record WorkspaceMemberVO(
        Long userId,
        String username,
        String displayName,
        WorkspaceRole role,
        boolean enabled,
        LocalDateTime joinedAt) {

    /**
     * 组合成员关系和用户信息。
     *
     * @param member 成员关系
     * @param user 系统用户
     * @return 成员响应
     */
    public static WorkspaceMemberVO from(WorkspaceMember member, User user) {
        return new WorkspaceMemberVO(user.getId(), user.getUsername(), user.getDisplayName(),
                member.getRole(), Boolean.TRUE.equals(user.getEnabled()), member.getJoinedAt());
    }
}
