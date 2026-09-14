/**
 * 本文件绑定管理员引导配置，控制首个注册用户是否自动成为 SYSTEM_ADMIN。
 */
package com.wjfz.bugloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理员引导配置。
 */
@Component
@ConfigurationProperties(prefix = "bugloop.admin")
public class AdminBootstrapProperties {

    private boolean bootstrapEnabled = true;

    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        this.bootstrapEnabled = bootstrapEnabled;
    }
}
