-- 本迁移移除既有 MySQL 环境中 sys_user、workspace 主键的自增属性。
-- 两类主键改由应用生成 JavaScript 安全整数范围内的随机 BIGINT，已有 ID 和关联数据保持不变。

ALTER TABLE sys_user MODIFY COLUMN id BIGINT NOT NULL;

ALTER TABLE workspace MODIFY COLUMN id BIGINT NOT NULL;
