-- 本脚本建立一期全部业务表，字段与索引严格对应 docs/04-domain-rules.md 第四十三节。
-- 时间字段统一使用 DATETIME，由应用层写入，不依赖数据库默认值，保证 MySQL 与测试环境行为一致。
-- 表之间不建立外键约束，关联完整性由应用层校验，避免影响后续数据维护。

CREATE TABLE sys_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    system_role   VARCHAR(32)  NOT NULL DEFAULT 'USER',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE workspace
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    owner_id    BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by  BIGINT       NOT NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE workspace_member
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    role         VARCHAR(16) NOT NULL,
    joined_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workspace_user (workspace_id, user_id),
    KEY idx_workspace_member_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bug
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    workspace_id       BIGINT      NOT NULL,
    bug_no             VARCHAR(32) NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description_md     LONGTEXT    NOT NULL,
    priority           VARCHAR(16) NOT NULL DEFAULT 'P2',
    status             VARCHAR(32) NOT NULL DEFAULT 'TODO',
    creator_id         BIGINT      NOT NULL,
    assignee_id        BIGINT       NULL,
    acceptor_id        BIGINT      NOT NULL,
    fix_description_md LONGTEXT     NULL,
    reopen_count       INT         NOT NULL DEFAULT 0,
    version            INT         NOT NULL DEFAULT 0,
    created_at         DATETIME    NOT NULL,
    updated_at         DATETIME    NOT NULL,
    closed_at          DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bug_no (bug_no),
    KEY idx_bug_workspace_status (workspace_id, status),
    KEY idx_bug_workspace_priority (workspace_id, priority),
    KEY idx_bug_assignee (assignee_id),
    KEY idx_bug_creator (creator_id),
    KEY idx_bug_acceptor (acceptor_id),
    KEY idx_bug_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bug_attachment
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    bug_id        BIGINT       NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_name  VARCHAR(255) NOT NULL,
    storage_path  VARCHAR(500) NOT NULL,
    file_size     BIGINT       NOT NULL,
    content_type  VARCHAR(128) NULL,
    uploader_id   BIGINT       NOT NULL,
    is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_by    BIGINT       NULL,
    deleted_at    DATETIME     NULL,
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_attachment_bug (bug_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bug_comment
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    bug_id     BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    content_md TEXT     NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_comment_bug_time (bug_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bug_operation_log
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    bug_id         BIGINT        NOT NULL,
    workspace_id   BIGINT        NOT NULL,
    operator_id    BIGINT        NOT NULL,
    operation_type VARCHAR(64)   NOT NULL,
    field_name     VARCHAR(64)   NULL,
    old_value      TEXT          NULL,
    new_value      TEXT          NULL,
    description    VARCHAR(1000) NULL,
    created_at     DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_bug_log_bug_time (bug_id, created_at),
    KEY idx_bug_log_workspace (workspace_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE workspace_operation_log
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    workspace_id   BIGINT        NOT NULL,
    operator_id    BIGINT        NOT NULL,
    operation_type VARCHAR(64)   NOT NULL,
    field_name     VARCHAR(64)   NULL,
    old_value      TEXT          NULL,
    new_value      TEXT          NULL,
    description    VARCHAR(1000) NULL,
    created_at     DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_workspace_log_workspace_time (workspace_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bug_description_history
(
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    bug_id      BIGINT   NOT NULL,
    version_no  INT      NOT NULL,
    content_md  LONGTEXT NOT NULL,
    operator_id BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bug_description_version (bug_id, version_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bug_acceptance
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    bug_id      BIGINT      NOT NULL,
    acceptor_id BIGINT      NOT NULL,
    result      VARCHAR(16) NOT NULL,
    comment_md  TEXT        NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status   VARCHAR(32) NOT NULL,
    created_at  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_acceptance_bug (bug_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
