-- 本迁移将原本由前端静态维护的内置 Bug 模板迁移到数据库，
-- 同时为个人模板增加共享开关，使同一工作空间的成员可复用被主动共享的模板。

ALTER TABLE bug_template
    ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'PERSONAL' COMMENT '模板范围：SYSTEM 为全局内置模板，PERSONAL 为工作空间个人模板';

-- 不使用 MySQL 专有的 AFTER 语法，确保开发和测试使用的 H2 数据库同样可以执行迁移。
ALTER TABLE bug_template
    ADD COLUMN shared TINYINT(1) NOT NULL DEFAULT 0 COMMENT '个人模板共享开关：1 表示同工作空间成员可使用；系统模板固定为 0';

-- 系统模板不绑定具体工作空间或用户，使用 0 作为内部保留归属值；权限由服务层仅允许 SYSTEM_ADMIN 管理。
INSERT INTO bug_template (workspace_id, creator_id, scope, shared, name, title, description_md, priority, source_bug_id, sort_order, created_at, updated_at, deleted)
VALUES (0, 0, 'SYSTEM', 0, '常规 Bug', '请简要描述问题现象', '## 问题现象\n\n## 复现步骤\n1. \n\n## 期望结果\n\n## 实际结果\n', 'P2', NULL, 0, NOW(), NOW(), 0),
       (0, 0, 'SYSTEM', 0, '页面异常', '页面出现异常', '## 异常页面\n\n## 操作步骤\n1. \n\n## 实际表现\n\n## 期望表现\n\n## 浏览器与环境\n', 'P2', NULL, 1, NOW(), NOW(), 0),
       (0, 0, 'SYSTEM', 0, '接口异常', '接口调用异常', '## 请求接口\n\n## 请求参数\n\n## 实际响应\n\n## 期望响应\n\n## 影响范围\n', 'P1', NULL, 2, NOW(), NOW(), 0);

-- 列表同时按全局系统模板和工作空间内可见个人模板过滤，避免共享查询退化为全表扫描。
CREATE INDEX idx_template_scope_visibility ON bug_template (scope, workspace_id, shared, deleted, sort_order);
