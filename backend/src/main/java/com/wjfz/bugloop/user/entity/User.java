/**
 * 本文件映射 sys_user 表，保存系统用户的账号、角色与启用状态。
 */
package com.wjfz.bugloop.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 系统用户实体，禁止直接作为接口请求或响应对象返回。
 */
@TableName("sys_user")
public class User {

    // 用户 ID 由应用生成随机值，避免通过自增值推测用户数量和创建顺序。
    @TableId(type = IdType.INPUT)
    // 用户主键，由应用生成随机值，避免通过 ID 推测账号数量和注册顺序。
    private Long id;

    // 用户登录时使用的唯一账号。
    private String username;

    // 界面和业务记录中展示的用户名称。
    private String displayName;

    // BCrypt 加密后的密码摘要，禁止向接口响应透出。
    private String passwordHash;

    // 系统级权限角色，例如 USER、SYSTEM_ADMIN。
    private String systemRole;

    // 账号启停标记，停用账号不能继续认证。
    private Boolean enabled;

    // 账号首次创建时间，由 MyBatis-Plus 自动填充。
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // 账号资料最后修改时间，由 MyBatis-Plus 自动维护。
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
