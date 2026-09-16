-- 为统一附件补充业务来源与业务记录关联，避免验收、评论等附件只能混在 Bug 总附件中。
-- 历史附件缺少来源数据，按产品兼容策略归档为提单附件，并以 Bug 主键作为关联记录。
ALTER TABLE bug_attachment ADD COLUMN biz_type VARCHAR(32) NOT NULL DEFAULT 'BUG_CREATE';
ALTER TABLE bug_attachment ADD COLUMN biz_id BIGINT NULL;
ALTER TABLE bug_attachment ADD COLUMN updated_at DATETIME NULL;

UPDATE bug_attachment
SET biz_id = bug_id,
    updated_at = COALESCE(deleted_at, created_at)
WHERE biz_id IS NULL;

-- 详情分组与验收记录批量回填均按 Bug、来源、业务记录和删除状态读取，建立复合索引避免扫描历史附件。
CREATE INDEX idx_attachment_bug_biz_active ON bug_attachment (bug_id, biz_type, biz_id, is_deleted);
