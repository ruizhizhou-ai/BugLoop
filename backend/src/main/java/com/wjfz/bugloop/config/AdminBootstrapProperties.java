/**
 * 本文件绑定内置系统管理员初始化配置，控制全新部署时是否自动创建管理员账号。
 */
package com.wjfz.bugloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内置管理员初始化配置，账号与密码可通过环境变量覆盖。
 */
@Component
@ConfigurationProperties(prefix = "bugloop.admin")
public class AdminBootstrapProperties {

    private boolean bootstrapEnabled = true;
    private String username = "admin";
    private String password = "admin@123";

    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        this.bootstrapEnabled = bootstrapEnabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
