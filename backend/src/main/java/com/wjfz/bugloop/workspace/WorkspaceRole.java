/**
 * 本文件定义工作空间固定角色，作为成员管理和权限判断的统一取值来源。
 */
package com.wjfz.bugloop.workspace;

/**
 * 工作空间角色，一期不支持自定义角色。
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER;

    /**
     * 判断当前角色是否具备成员管理权限。
     *
     * @return OWNER 或 ADMIN 返回 true
     */
    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }
}
