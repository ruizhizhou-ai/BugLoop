/**
 * 本文件在应用启动时创建内置系统管理员，保证全新部署一定有可登录的管理账号。
 * 账号已存在时跳过，重复启动和首次注册都不受影响。
 */
package com.wjfz.bugloop.user.service;

import com.wjfz.bugloop.config.AdminBootstrapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** 内置系统管理员初始化器。 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private static final String ADMIN_DISPLAY_NAME = "系统管理员";
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final AdminBootstrapProperties properties;
    private final UserService userService;
    private final UserAccountService accountService;

    /** 注入内置管理员配置与账号服务。 */
    public AdminAccountInitializer(AdminBootstrapProperties properties, UserService userService,
                                   UserAccountService accountService) {
        this.properties = properties;
        this.userService = userService;
        this.accountService = accountService;
    }

    /** 按配置创建内置管理员；同名账号已存在时直接跳过。 */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isBootstrapEnabled()) {
            return;
        }
        String username = properties.getUsername();
        if (userService.findByUsername(username).isPresent()) {
            return;
        }
        try {
            accountService.create(username, ADMIN_DISPLAY_NAME, properties.getPassword(), SYSTEM_ADMIN);
            log.info("已创建内置系统管理员账号：{}", username);
        } catch (DuplicateKeyException exception) {
            // 多实例同时启动时由用户名唯一索引兜底，后到的实例跳过即可。
            log.info("内置系统管理员账号已存在：{}", username);
        }
    }
}
