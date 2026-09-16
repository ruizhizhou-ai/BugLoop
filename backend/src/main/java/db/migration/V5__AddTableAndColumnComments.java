/**
 * 本迁移为生产 MySQL/MariaDB 中已有业务表补齐字段中文注释。
 * H2 测试数据库不支持 MySQL 的字段 COMMENT 语法，因此在 H2 上安全跳过；
 * 业务字段、默认值、索引和历史数据均不会被改变。
 */
package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 为数据库管理工具提供与实体字段一致的中文字段说明。
 */
public class V5__AddTableAndColumnComments extends BaseJavaMigration {

    /**
     * 在支持 MySQL COMMENT 语法的数据库执行字段注释迁移。
     *
     * @param context Flyway 当前迁移上下文，包含目标数据库连接
     * @throws SQLException 执行数据库 DDL 失败时向 Flyway 透传，阻止产生不完整迁移记录
     */
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        String databaseProductName = connection.getMetaData().getDatabaseProductName();

        // H2 仅用于自动化测试，不能识别 MySQL 的 MODIFY COLUMN ... COMMENT 语法。
        if (!supportsColumnComment(databaseProductName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            for (String sql : COMMENT_STATEMENTS) {
                statement.execute(sql);
            }
        }
    }

    /**
     * 判断目标数据库是否支持本迁移采用的 MySQL 列注释语法。
     */
    private boolean supportsColumnComment(String databaseProductName) {
        return databaseProductName != null
                && (databaseProductName.contains("MySQL") || databaseProductName.contains("MariaDB"));
    }

    /**
     * 逐表修改列定义时必须完整保留类型、空值约束和默认值，避免注释迁移意外改变业务语义。
     */
    private static final List<String> COMMENT_STATEMENTS = List.of(
            """
            ALTER TABLE sys_user
                MODIFY COLUMN id BIGINT NOT NULL COMMENT '用户主键，由应用生成随机 ID',
                MODIFY COLUMN username VARCHAR(64) NOT NULL COMMENT '登录账号，全局唯一',
                MODIFY COLUMN display_name VARCHAR(64) NOT NULL COMMENT '用户显示名称',
                MODIFY COLUMN password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密后的密码摘要',
                MODIFY COLUMN system_role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '系统级角色，例如 USER、SYSTEM_ADMIN',
                MODIFY COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号是否启用：1 启用，0 停用',
                MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '账号创建时间',
                MODIFY COLUMN updated_at DATETIME NOT NULL COMMENT '账号最后更新时间'
            """,
            """
            ALTER TABLE workspace
                MODIFY COLUMN id BIGINT NOT NULL COMMENT '工作空间主键，由应用生成随机 ID',
                MODIFY COLUMN name VARCHAR(100) NOT NULL COMMENT '工作空间名称',
                MODIFY COLUMN description VARCHAR(500) NULL COMMENT '工作空间说明',
                MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '工作空间负责人用户 ID',
                MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '空间状态：ENABLED 启用，DISABLED 停用',
                MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '创建该工作空间的用户 ID',
                MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '工作空间创建时间',
                MODIFY COLUMN updated_at DATETIME NOT NULL COMMENT '工作空间最后更新时间'
            """,
            """
            ALTER TABLE workspace_member
                MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '成员关系主键',
                MODIFY COLUMN workspace_id BIGINT NOT NULL COMMENT '所属工作空间 ID',
                MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '成员用户 ID',
                MODIFY COLUMN role VARCHAR(16) NOT NULL COMMENT '空间内角色，例如 ADMIN、MEMBER',
                MODIFY COLUMN joined_at DATETIME NOT NULL COMMENT '加入工作空间的时间'
            """,
            """
            ALTER TABLE bug
                MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Bug 主键',
                MODIFY COLUMN workspace_id BIGINT NOT NULL COMMENT '所属工作空间 ID',
                MODIFY COLUMN bug_no VARCHAR(32) NOT NULL COMMENT '面向用户展示的 Bug 编号，全局唯一',
                MODIFY COLUMN title VARCHAR(200) NOT NULL COMMENT 'Bug 标题',
                MODIFY COLUMN description_md LONGTEXT NOT NULL COMMENT '问题描述 Markdown 原文',
                MODIFY COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'P2' COMMENT '优先级，例如 P0、P1、P2、P3',
                MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'TODO' COMMENT '当前处理状态',
                MODIFY COLUMN creator_id BIGINT NOT NULL COMMENT 'Bug 提交人用户 ID',
                MODIFY COLUMN assignee_id BIGINT NULL COMMENT '当前负责人用户 ID，未指派时为空',
                MODIFY COLUMN acceptor_id BIGINT NOT NULL COMMENT '验收人用户 ID',
                MODIFY COLUMN fix_description_md LONGTEXT NULL COMMENT '处理说明 Markdown 原文',
                MODIFY COLUMN reopen_count INT NOT NULL DEFAULT 0 COMMENT '验收驳回或重新打开次数',
                MODIFY COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                MODIFY COLUMN created_at DATETIME NOT NULL COMMENT 'Bug 创建时间',
                MODIFY COLUMN updated_at DATETIME NOT NULL COMMENT 'Bug 最后更新时间',
                MODIFY COLUMN closed_at DATETIME NULL COMMENT 'Bug 关闭时间，未关闭时为空'
            """,
            """
            ALTER TABLE bug_attachment
                MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '附件主键',
                MODIFY COLUMN bug_id BIGINT NOT NULL COMMENT '附件所属 Bug ID',
                MODIFY COLUMN original_name VARCHAR(255) NOT NULL COMMENT '用户上传时的原始文件名',
                MODIFY COLUMN storage_name VARCHAR(255) NOT NULL COMMENT '存储服务生成的唯一文件名',
                MODIFY COLUMN storage_path VARCHAR(500) NOT NULL COMMENT '服务端文件存储相对路径，不对外暴露',
                MODIFY COLUMN file_size BIGINT NOT NULL COMMENT '文件大小，单位字节',
                MODIFY COLUMN content_type VARCHAR(128) NULL COMMENT '文件 MIME 类型',
                MODIFY COLUMN uploader_id BIGINT NOT NULL COMMENT '上传人用户 ID',
                MODIFY COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
                MODIFY COLUMN deleted_by BIGINT NULL COMMENT '执行删除操作的用户 ID',
                MODIFY COLUMN deleted_at DATETIME NULL COMMENT '附件逻辑删除时间',
                MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '附件上传时间',
                MODIFY COLUMN biz_type VARCHAR(32) NOT NULL DEFAULT 'BUG_CREATE' COMMENT '附件业务来源，例如 BUG_CREATE、ACCEPT_REJECT',
                MODIFY COLUMN biz_id BIGINT NULL COMMENT '来源业务记录 ID，例如验收记录 ID',
                MODIFY COLUMN updated_at DATETIME NULL COMMENT '附件元数据最后更新时间'
            """,
            """
            ALTER TABLE bug_comment
                MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论主键',
                MODIFY COLUMN bug_id BIGINT NOT NULL COMMENT '评论所属 Bug ID',
                MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '评论创建人用户 ID',
                MODIFY COLUMN content_md TEXT NOT NULL COMMENT '评论 Markdown 正文',
                MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '评论创建时间',
                MODIFY COLUMN parent_id BIGINT NULL COMMENT '父评论 ID，顶级评论为空',
                MODIFY COLUMN reply_user_id BIGINT NULL COMMENT '被回复用户 ID',
                MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论最后更新时间',
                MODIFY COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
                MODIFY COLUMN deleted_by BIGINT NULL COMMENT '执行删除操作的用户 ID',
                MODIFY COLUMN deleted_at DATETIME NULL COMMENT '评论逻辑删除时间'
            """,
            """
            ALTER TABLE bug_operation_log
                MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作日志主键',
                MODIFY COLUMN bug_id BIGINT NOT NULL COMMENT '关联 Bug ID',
                MODIFY COLUMN workspace_id BIGINT NOT NULL COMMENT '关联工作空间 ID',
                MODIFY COLUMN operator_id BIGINT NOT NULL COMMENT '执行操作的用户 ID',
                MODIFY COLUMN operation_type VARCHAR(64) NOT NULL COMMENT '操作类型标识',
                MODIFY COLUMN field_name VARCHAR(64) NULL COMMENT '发生变化的业务字段名',
                MODIFY COLUMN old_value TEXT NULL COMMENT '变更前值的审计快照',
                MODIFY COLUMN new_value TEXT NULL COMMENT '变更后值的审计快照',
                MODIFY COLUMN description VARCHAR(1000) NULL COMMENT '面向用户展示的操作说明',
                MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '操作发生时间'
            """,
            """
            ALTER TABLE workspace_operation_log
                MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作日志主键',
                MODIFY COLUMN workspace_id BIGINT NOT NULL COMMENT '关联工作空间 ID',
                MODIFY COLUMN operator_id BIGINT NOT NULL COMMENT '执行操作的用户 ID',
                MODIFY COLUMN operation_type VARCHAR(64) NOT NULL COMMENT '操作类型标识',
                MODIFY COLUMN field_name VARCHAR(64) NULL COMMENT '发生变化的业务字段名',
                MODIFY COLUMN old_value TEXT NULL COMMENT '变更前值的审计快照',
                MODIFY COLUMN new_value TEXT NULL COMMENT '变更后值的审计快照',
                MODIFY COLUMN description VARCHAR(1000) NULL COMMENT '面向用户展示的操作说明',
                MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '操作发生时间'
            """,
            """
            ALTER TABLE bug_description_history
                MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '描述历史主键',
                MODIFY COLUMN bug_id BIGINT NOT NULL COMMENT '关联 Bug ID',
                MODIFY COLUMN version_no INT NOT NULL COMMENT '同一 Bug 内递增的描述版本号',
                MODIFY COLUMN content_md LONGTEXT NOT NULL COMMENT '该版本的 Markdown 描述原文',
                MODIFY COLUMN operator_id BIGINT NOT NULL COMMENT '创建该版本的用户 ID',
                MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '历史版本创建时间'
            """,
            """
            ALTER TABLE bug_acceptance
                MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '验收记录主键',
                MODIFY COLUMN bug_id BIGINT NOT NULL COMMENT '关联 Bug ID',
                MODIFY COLUMN acceptor_id BIGINT NOT NULL COMMENT '实际执行验收的用户 ID',
                MODIFY COLUMN result VARCHAR(16) NOT NULL COMMENT '验收结论，例如 PASS、REJECT',
                MODIFY COLUMN comment_md TEXT NULL COMMENT '验收意见或驳回原因 Markdown 原文',
                MODIFY COLUMN from_status VARCHAR(32) NOT NULL COMMENT '验收前的 Bug 状态',
                MODIFY COLUMN to_status VARCHAR(32) NOT NULL COMMENT '验收后的 Bug 状态',
                MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '验收发生时间'
            """);
}
