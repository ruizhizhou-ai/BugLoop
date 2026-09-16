-- 本脚本为 MySQL 的既有业务表补齐表说明和字段中文注释。
-- 每条 DDL 使用 MySQL 可执行注释封装：MySQL 8.0 会执行，H2 测试库会安全跳过，
-- 因此生产和测试可使用同一份 Flyway 迁移版本，避免出现重复 V5 脚本。
-- 修改列定义时完整保留类型、空值约束、默认值与自增属性，避免注释迁移改变任何业务语义。

/*!80000 ALTER TABLE sys_user
    COMMENT = '系统用户表，保存登录账号、显示资料、系统角色和启用状态',
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '用户主键，由应用生成随机 ID',
    MODIFY COLUMN username VARCHAR(64) NOT NULL COMMENT '登录账号，全局唯一',
    MODIFY COLUMN display_name VARCHAR(64) NOT NULL COMMENT '用户显示名称',
    MODIFY COLUMN password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密后的密码摘要',
    MODIFY COLUMN system_role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '系统级角色，例如 USER、SYSTEM_ADMIN',
    MODIFY COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号是否启用：1 启用，0 停用',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '账号创建时间',
    MODIFY COLUMN updated_at DATETIME NOT NULL COMMENT '账号最后更新时间' */;

/*!80000 ALTER TABLE workspace
    COMMENT = '工作空间表，隔离不同项目或团队的 Bug 数据',
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '工作空间主键，由应用生成随机 ID',
    MODIFY COLUMN name VARCHAR(100) NOT NULL COMMENT '工作空间名称',
    MODIFY COLUMN description VARCHAR(500) NULL COMMENT '工作空间说明',
    MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '工作空间负责人用户 ID',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '空间状态：ENABLED 启用，DISABLED 停用',
    MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '创建该工作空间的用户 ID',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '工作空间创建时间',
    MODIFY COLUMN updated_at DATETIME NOT NULL COMMENT '工作空间最后更新时间' */;

/*!80000 ALTER TABLE workspace_member
    COMMENT = '工作空间成员关系表，保存成员在空间内的角色',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '成员关系主键',
    MODIFY COLUMN workspace_id BIGINT NOT NULL COMMENT '所属工作空间 ID',
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '成员用户 ID',
    MODIFY COLUMN role VARCHAR(16) NOT NULL COMMENT '空间内角色，例如 ADMIN、MEMBER',
    MODIFY COLUMN joined_at DATETIME NOT NULL COMMENT '加入工作空间时间' */;

/*!80000 ALTER TABLE bug
    COMMENT = 'Bug 主表，保存问题描述、处理状态、人员分工和版本信息',
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
    MODIFY COLUMN closed_at DATETIME NULL COMMENT 'Bug 关闭时间，未关闭时为空' */;

/*!80000 ALTER TABLE bug_attachment
    COMMENT = 'Bug 附件表，保存普通附件的文件元数据与业务归属',
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
    MODIFY COLUMN updated_at DATETIME NULL COMMENT '附件元数据最后更新时间' */;

/*!80000 ALTER TABLE bug_comment
    COMMENT = 'Bug 评论表，保存评论、回复和逻辑删除信息',
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
    MODIFY COLUMN deleted_at DATETIME NULL COMMENT '评论逻辑删除时间' */;

/*!80000 ALTER TABLE bug_operation_log
    COMMENT = 'Bug 操作日志表，记录问题状态、人员和字段变更审计',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作日志主键',
    MODIFY COLUMN bug_id BIGINT NOT NULL COMMENT '关联 Bug ID',
    MODIFY COLUMN workspace_id BIGINT NOT NULL COMMENT '关联工作空间 ID',
    MODIFY COLUMN operator_id BIGINT NOT NULL COMMENT '执行操作用户 ID',
    MODIFY COLUMN operation_type VARCHAR(64) NOT NULL COMMENT '操作类型标识',
    MODIFY COLUMN field_name VARCHAR(64) NULL COMMENT '发生变化的业务字段名',
    MODIFY COLUMN old_value TEXT NULL COMMENT '变更前审计快照',
    MODIFY COLUMN new_value TEXT NULL COMMENT '变更后审计快照',
    MODIFY COLUMN description VARCHAR(1000) NULL COMMENT '面向用户展示的操作说明',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '操作发生时间' */;

/*!80000 ALTER TABLE workspace_operation_log
    COMMENT = '工作空间操作日志表，记录空间配置和成员变更审计',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作日志主键',
    MODIFY COLUMN workspace_id BIGINT NOT NULL COMMENT '关联工作空间 ID',
    MODIFY COLUMN operator_id BIGINT NOT NULL COMMENT '执行操作用户 ID',
    MODIFY COLUMN operation_type VARCHAR(64) NOT NULL COMMENT '操作类型标识',
    MODIFY COLUMN field_name VARCHAR(64) NULL COMMENT '发生变化的业务字段名',
    MODIFY COLUMN old_value TEXT NULL COMMENT '变更前审计快照',
    MODIFY COLUMN new_value TEXT NULL COMMENT '变更后审计快照',
    MODIFY COLUMN description VARCHAR(1000) NULL COMMENT '面向用户展示的操作说明',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '操作发生时间' */;

/*!80000 ALTER TABLE bug_description_history
    COMMENT = 'Bug 描述历史表，保存问题描述修改前的版本快照',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '描述历史主键',
    MODIFY COLUMN bug_id BIGINT NOT NULL COMMENT '关联 Bug ID',
    MODIFY COLUMN version_no INT NOT NULL COMMENT '同一 Bug 内递增的描述版本号',
    MODIFY COLUMN content_md LONGTEXT NOT NULL COMMENT '该版本问题描述 Markdown 原文',
    MODIFY COLUMN operator_id BIGINT NOT NULL COMMENT '创建该版本用户 ID',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '历史版本创建时间' */;

/*!80000 ALTER TABLE bug_acceptance
    COMMENT = 'Bug 验收记录表，保存每次通过或驳回的结论',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '验收记录主键',
    MODIFY COLUMN bug_id BIGINT NOT NULL COMMENT '关联 Bug ID',
    MODIFY COLUMN acceptor_id BIGINT NOT NULL COMMENT '实际执行验收用户 ID',
    MODIFY COLUMN result VARCHAR(16) NOT NULL COMMENT '验收结论，例如 PASS、REJECT',
    MODIFY COLUMN comment_md TEXT NULL COMMENT '验收意见或驳回原因 Markdown 原文',
    MODIFY COLUMN from_status VARCHAR(32) NOT NULL COMMENT '验收前 Bug 状态',
    MODIFY COLUMN to_status VARCHAR(32) NOT NULL COMMENT '验收后 Bug 状态',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '验收发生时间' */;
