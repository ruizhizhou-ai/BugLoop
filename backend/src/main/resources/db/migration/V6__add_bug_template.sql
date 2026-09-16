-- 本迁移新增 Bug 个人模板表，仅保存创建 Bug 时可复用的基础字段。
-- 工作空间与创建人共同构成模板的数据隔离边界，内置模板仍由前端静态配置维护。

CREATE TABLE bug_template
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板主键 ID',
    workspace_id   BIGINT       NOT NULL COMMENT '所属工作空间 ID，用于工作空间级数据隔离',
    creator_id     BIGINT       NOT NULL COMMENT '模板创建人用户 ID，个人模板仅创建人可见和管理',
    name           VARCHAR(100) NOT NULL COMMENT '模板名称，用于模板列表展示和选择',
    title          VARCHAR(200) NOT NULL COMMENT '创建 Bug 时预填的默认标题',
    description_md LONGTEXT     NOT NULL COMMENT '创建 Bug 时预填的 Markdown 描述内容',
    priority       VARCHAR(16)  NOT NULL DEFAULT 'P2' COMMENT '默认优先级，例如 P0、P1、P2、P3',
    source_bug_id  BIGINT       NULL COMMENT '来源 Bug ID，从已有 Bug 保存为模板时记录；手工创建时为空',
    sort_order     INT          NOT NULL DEFAULT 0 COMMENT '模板排序值，数值越小越靠前',
    created_at     DATETIME     NOT NULL COMMENT '模板创建时间',
    updated_at     DATETIME     NOT NULL COMMENT '模板最后更新时间',
    deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0 未删除，1 已删除',
    PRIMARY KEY (id),
    -- 支持按工作空间、创建人和删除状态隔离个人模板，并直接按排序值返回。
    KEY idx_template_workspace_creator (workspace_id, creator_id, deleted, sort_order),
    -- 支持从 Bug 保存模板后按来源 Bug 追溯模板。
    KEY idx_template_source_bug (source_bug_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Bug 模板表，用于存储用户在工作空间内创建的个人 Bug 模板。模板仅保存 Bug 创建时可复用的基础字段，例如标题、Markdown 描述和默认优先级，不包含负责人、验收人、状态、附件、评论、操作记录和验收记录等业务数据。';
