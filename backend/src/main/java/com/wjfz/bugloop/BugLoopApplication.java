/**
 * 本文件是 BugLoop 后端应用入口，负责启动 Spring Boot 容器并加载各业务模块。
 */
package com.wjfz.bugloop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BugLoop 后端启动类。
 */
@SpringBootApplication
public class BugLoopApplication {

    /**
     * 启动 BugLoop 后端服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(BugLoopApplication.class, args);
    }
}

