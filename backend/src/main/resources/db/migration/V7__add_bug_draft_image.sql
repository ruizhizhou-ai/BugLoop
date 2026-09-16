-- Markdown 正文图片在 Bug 创建前尚无 bug_id，单独存放草稿记录以避免污染普通附件表。
-- 草稿图片仅上传人可在创建前预览；创建成功后绑定 Bug，超时未绑定的记录由定时任务清理。

CREATE TABLE bug_draft_image
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '正文图片主键 ID',
    workspace_id  BIGINT       NOT NULL COMMENT '所属工作空间 ID，用于工作空间级数据隔离',
    uploader_id   BIGINT       NOT NULL COMMENT '图片上传人用户 ID，草稿期间仅该用户可访问',
    bug_id        BIGINT       NULL COMMENT '已绑定的 Bug ID；草稿状态为空，创建成功后写入',
    original_name VARCHAR(255) NOT NULL COMMENT '用户上传时的原始图片文件名',
    storage_name  VARCHAR(255) NOT NULL COMMENT '存储服务生成的唯一图片文件名',
    storage_path  VARCHAR(500) NOT NULL COMMENT '服务端内部相对存储路径，不对客户端直接暴露',
    file_size     BIGINT       NOT NULL COMMENT '图片文件大小，单位字节',
    content_type  VARCHAR(128) NOT NULL COMMENT '图片 MIME 类型，例如 image/png',
    status        VARCHAR(16)  NOT NULL COMMENT '图片状态：DRAFT 未绑定，BOUND 已绑定',
    expires_at    DATETIME     NULL COMMENT '草稿过期时间；绑定 Bug 后为空',
    created_at    DATETIME     NOT NULL COMMENT '图片上传时间',
    updated_at    DATETIME     NOT NULL COMMENT '图片元数据最后更新时间',
    deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0 未删除，1 已删除',
    PRIMARY KEY (id),
    -- 定时任务按空间、上传人、草稿状态和过期时间定位可清理图片。
    KEY idx_draft_image_cleanup (workspace_id, uploader_id, status, expires_at, deleted),
    -- 正文图片绑定后可按 Bug 查询和权限校验。
    KEY idx_draft_image_bug (bug_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Bug Markdown 正文图片表。创建 Bug 前保存个人草稿图片，创建成功后绑定到 Bug；过期草稿图片由定时任务清理。';
