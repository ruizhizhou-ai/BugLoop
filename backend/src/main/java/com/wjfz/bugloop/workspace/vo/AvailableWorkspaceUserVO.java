/**
 * 本文件定义添加工作空间成员时的用户候选项，供前端搜索并选择系统用户。
 * 它仅承载必要的标识和展示信息，不暴露密码哈希、系统角色等无关字段。
 */
package com.wjfz.bugloop.workspace.vo;

import com.wjfz.bugloop.user.entity.User;

/**
 * 可添加到工作空间的用户选项。
 *
 * @param id 用户主键
 * @param username 登录用户名
 * @param displayName 展示名称
 */
public record AvailableWorkspaceUserVO(Long id, String username, String displayName) {

    /**
     * 将启用的系统用户转换为成员选择项。
     *
     * @param user 系统用户实体
     * @return 供下拉选择展示的安全用户信息
     */
    public static AvailableWorkspaceUserVO from(User user) {
        return new AvailableWorkspaceUserVO(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
