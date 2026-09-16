/**
 * 本文件是 BugLoop 后端应用入口，负责启动 Spring Boot 容器并加载各业务模块。
 */
package com.wjfz.bugloop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BugLoop 后端启动类。
 */
@SpringBootApplication
// 启用草稿正文图片过期清理等受控后台任务，具体任务仍由各业务服务声明执行周期。
@EnableScheduling
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
