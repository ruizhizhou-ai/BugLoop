/**
 * 本文件复用全部 Bug API 契约测试到真实 MySQL，特别验证行锁、乐观锁、分页 SQL 和事务回滚。
 * 容器复用已有集成测试基础设施，不连接开发者业务数据库。
 */
package com.wjfz.bugloop;

import com.wjfz.bugloop.bug.controller.BugControllerTest;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;

/** MySQL Bug 集成测试，Docker 不可用时明确跳过。 */
@ActiveProfiles(profiles = "local", inheritProfiles = false)
@EnabledIf("dockerAvailable")
class BugApiMysqlIT extends BugControllerTest {
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = AbstractMysqlIntegrationTest.MYSQL;

    /** 复用全项目的 Docker 可用性判断。 */
    static boolean dockerAvailable() {
        return AbstractMysqlIntegrationTest.dockerAvailable();
    }
}
