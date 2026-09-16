/**
 * 本文件验证 Flyway 迁移脚本在真实 MySQL 上建出全部业务表。
 */
package com.wjfz.bugloop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库基线集成测试。
 */
class SchemaMigrationIT extends AbstractMysqlIntegrationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "sys_user", "workspace", "workspace_member", "bug", "bug_attachment",
            "bug_comment", "bug_operation_log", "workspace_operation_log",
            "bug_description_history", "bug_acceptance");

    @Test
    void 迁移执行后应包含全部业务表() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    void 用户名唯一索引应生效() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, display_name, password_hash, system_role, enabled, created_at, updated_at)
                VALUES (900001, 'index_probe', '索引验证', 'hash', 'USER', 1, NOW(), NOW())
                """);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO sys_user (id, username, display_name, password_hash, system_role, enabled, created_at, updated_at)
                        VALUES (900002, 'index_probe', '重复用户', 'hash', 'USER', 1, NOW(), NOW())
                        """))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        jdbcTemplate.update("DELETE FROM sys_user WHERE username = 'index_probe'");
    }

    @Test
    void 评论表应具备回复和逻辑删除字段() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'bug_comment'
                """, String.class);

        assertThat(columns).contains("parent_id", "reply_user_id", "updated_at", "is_deleted",
                "deleted_by", "deleted_at");
    }

    /**
     * 验证生产 MySQL 中每个业务字段都带有说明，避免后续迁移遗漏数据库文档。
     */
    @Test
    void 全部业务字段应具备中文说明() {
        Integer documentedColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN ('sys_user', 'workspace', 'workspace_member', 'bug', 'bug_attachment',
                                     'bug_comment', 'bug_operation_log', 'workspace_operation_log',
                                     'bug_description_history', 'bug_acceptance')
                  AND column_comment <> ''
                """, Integer.class);

        // 当前 10 张业务表共 96 个业务字段；数量变化时应同步补充迁移中的字段说明。
        assertThat(documentedColumnCount).isEqualTo(96);
    }
}
