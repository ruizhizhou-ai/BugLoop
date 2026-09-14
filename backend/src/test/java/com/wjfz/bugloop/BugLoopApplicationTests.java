/**
 * 本文件验证 BugLoop 最小 Spring 容器能够启动，为后续模块开发提供基础回归检查。
 */
package com.wjfz.bugloop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用启动冒烟测试。
 */
@ActiveProfiles("test")
@SpringBootTest
class BugLoopApplicationTests {

    /**
     * 验证基础自动配置和测试配置之间不存在启动冲突。
     */
    @Test
    void contextLoads() {
        // Spring 容器成功创建即代表测试通过，无需额外断言。
    }
}

