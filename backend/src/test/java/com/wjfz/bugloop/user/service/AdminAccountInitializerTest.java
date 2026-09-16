/**
 * 本文件验证内置系统管理员初始化：全新部署创建可登录管理员，重复启动幂等，开关可关闭。
 */
package com.wjfz.bugloop.user.service;

import cn.dev33.satoken.secure.BCrypt;
import com.wjfz.bugloop.config.AdminBootstrapProperties;
import com.wjfz.bugloop.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置管理员初始化测试，直接复用生产账号服务与真实数据库。
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminAccountInitializerTest {

    @Autowired
    private AdminAccountInitializer initializer;

    @Autowired
    private AdminBootstrapProperties properties;

    @Autowired
    private UserService userService;

    @Autowired
    private UserAccountService accountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUsers() {
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    @Test
    void 全新部署时创建可登录的内置管理员() {
        initializer.run(null);

        User admin = userService.findByUsername(properties.getUsername()).orElseThrow();
        assertThat(admin.getSystemRole()).isEqualTo("SYSTEM_ADMIN");
        assertThat(admin.getEnabled()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(BCrypt.checkpw(properties.getPassword(), admin.getPasswordHash())).isTrue();
    }

    @Test
    void 重复启动保持幂等() {
        initializer.run(null);
        initializer.run(null);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class, properties.getUsername());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void 关闭初始化开关时不创建管理员() {
        AdminBootstrapProperties disabled = new AdminBootstrapProperties();
        disabled.setBootstrapEnabled(false);

        new AdminAccountInitializer(disabled, userService, accountService).run(null);

        assertThat(userService.findByUsername(properties.getUsername())).isEmpty();
    }
}
