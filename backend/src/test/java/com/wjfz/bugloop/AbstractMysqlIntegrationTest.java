/**
 * 本文件为集成测试提供真实 MySQL 环境，Docker 不可用时整组用例自动跳过。
 */
package com.wjfz.bugloop;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;

/**
 * 真实 MySQL 集成测试基类：容器内执行与生产一致的 Flyway 迁移，其余环境变量沿用本地配置。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIf("dockerAvailable")
public abstract class AbstractMysqlIntegrationTest {

    /**
     * 容器在所有集成测试类之间共享，避免每个类各起一个实例导致连接不可用。
     */
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("bugloop")
            .withUsername("bugloop")
            .withPassword("bugloop");

    static {
        if (dockerAvailable()) {
            MYSQL.start();
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 判断本机是否可以启动测试容器，避免没有 Docker 的开发机执行失败。
     *
     * @return Docker 可用时为 true
     */
    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
