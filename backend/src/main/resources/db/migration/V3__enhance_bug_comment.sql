-- 扩展既有评论表以支持一级回复与逻辑删除；保留历史评论行，避免破坏审计与回复关联。
-- 不指定 MySQL 专属的 AFTER 排列语法，使本地 H2 集成测试和生产 MySQL 使用同一份迁移。
ALTER TABLE bug_comment ADD COLUMN parent_id BIGINT NULL;
ALTER TABLE bug_comment ADD COLUMN reply_user_id BIGINT NULL;
ALTER TABLE bug_comment ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE bug_comment ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE bug_comment ADD COLUMN deleted_by BIGINT NULL;
ALTER TABLE bug_comment ADD COLUMN deleted_at DATETIME NULL;

-- 读取单个 Bug 的平铺评论、定位父评论状态都依赖这些索引，避免回复数量增长后退化为全表扫描。
CREATE INDEX idx_comment_bug_parent_time ON bug_comment (bug_id, parent_id, created_at);
CREATE INDEX idx_comment_parent ON bug_comment (parent_id);
