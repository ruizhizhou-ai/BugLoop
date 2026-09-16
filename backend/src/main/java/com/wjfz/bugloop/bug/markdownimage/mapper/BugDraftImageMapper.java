/**
 * 本文件提供 Markdown 草稿图片的持久化操作；状态条件始终写入 SQL，防止过期清理与 Bug 创建并发互相覆盖。
 */
package com.wjfz.bugloop.bug.markdownimage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.bug.vo.BugAttachmentVO;
import com.wjfz.bugloop.bug.markdownimage.entity.BugDraftImage;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Markdown 草稿图片数据入口。 */
@Mapper
public interface BugDraftImageMapper extends BaseMapper<BugDraftImage> {

    /**
     * 将已绑定的正文图片投影为统一附件视图，供 Bug 详情的“全部附件”按来源分组展示。
     * 图片仍存于独立草稿图片表，避免创建 Bug 前的图片污染普通附件表。
     */
    @Select("""
            SELECT i.id, i.bug_id, 'BUG_DESCRIPTION' AS biz_type, i.bug_id AS biz_id,
                   i.original_name, i.file_size, i.content_type,
                   i.uploader_id, u.display_name AS uploader_name, NULL AS uploader_avatar,
                   i.created_at, FALSE AS can_delete
            FROM bug_draft_image i
            LEFT JOIN sys_user u ON u.id = i.uploader_id
            WHERE i.bug_id = #{bugId} AND i.deleted = 0 AND i.status = 'BOUND'
            ORDER BY i.created_at ASC, i.id ASC
            """)
    List<BugAttachmentVO> selectBoundAttachmentViews(@Param("bugId") Long bugId);

    /** 查询单个有效图片，供内容读取前的状态确认使用。 */
    @Select("SELECT * FROM bug_draft_image WHERE id = #{imageId} AND deleted = 0")
    BugDraftImage selectActiveById(@Param("imageId") Long imageId);

    /** 统计当前用户在一个空间内尚未过期的草稿图片，限制用户放弃编辑时产生的存储占用。 */
    @Select("""
            SELECT COUNT(*) FROM bug_draft_image
            WHERE workspace_id = #{workspaceId} AND uploader_id = #{uploaderId}
              AND deleted = 0 AND status = 'DRAFT' AND expires_at > CURRENT_TIMESTAMP
            """)
    long countActiveDrafts(@Param("workspaceId") Long workspaceId, @Param("uploaderId") Long uploaderId);

    /**
     * 锁定 Markdown 中引用的全部图片，保证创建 Bug 与过期清理不会并发绑定同一图片。
     *
     * @param imageIds Markdown 中提取出的正文图片 ID
     * @return 仍未逻辑删除的图片记录
     */
    @Select("""
            <script>
            SELECT * FROM bug_draft_image
            WHERE deleted = 0 AND id IN
            <foreach collection='imageIds' item='imageId' open='(' separator=',' close=')'>#{imageId}</foreach>
            FOR UPDATE
            </script>
            """)
    List<BugDraftImage> selectActiveByIdsForUpdate(@Param("imageIds") List<Long> imageIds);

    /** 将一张仍有效的草稿图片原子绑定到新建 Bug。 */
    @Update("""
            UPDATE bug_draft_image
            SET bug_id = #{bugId}, status = 'BOUND', expires_at = NULL, updated_at = #{now}
            WHERE id = #{imageId} AND deleted = 0 AND status = 'DRAFT' AND expires_at > #{now}
            """)
    int bindToBug(@Param("imageId") Long imageId, @Param("bugId") Long bugId, @Param("now") LocalDateTime now);

    /** 查询一小批已过期且仍未绑定的草稿图片，供定时任务逐条条件删除。 */
    @Select("""
            SELECT * FROM bug_draft_image
            WHERE deleted = 0 AND status = 'DRAFT' AND expires_at <= #{now}
            ORDER BY expires_at ASC
            LIMIT #{limit}
            """)
    List<BugDraftImage> selectExpiredDrafts(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 仅在图片仍为过期草稿时标记删除，防止并发创建已绑定该图片后误删文件。 */
    @Update("""
            UPDATE bug_draft_image
            SET deleted = 1, updated_at = #{now}
            WHERE id = #{imageId} AND deleted = 0 AND status = 'DRAFT' AND expires_at <= #{now}
            """)
    int markExpiredDraftDeleted(@Param("imageId") Long imageId, @Param("now") LocalDateTime now);
}
