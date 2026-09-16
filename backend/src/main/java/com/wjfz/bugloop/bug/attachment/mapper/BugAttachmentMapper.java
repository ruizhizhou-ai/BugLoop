/** 本文件提供附件元数据的并发计数、加锁读取和逻辑删除，物理文件不在 Mapper 内处理。 */
package com.wjfz.bugloop.bug.attachment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.bug.attachment.entity.BugAttachment;
import com.wjfz.bugloop.bug.vo.BugAttachmentVO;
import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Bug 附件数据访问入口。 */
@Mapper
public interface BugAttachmentMapper extends BaseMapper<BugAttachment> {

    /** 统计未删除附件，调用方持有工作空间锁后可安全执行上传数量上限判断。 */
    @Select("SELECT COUNT(*) FROM bug_attachment WHERE bug_id = #{bugId} AND is_deleted = 0")
    long countActiveByBugId(@Param("bugId") Long bugId);

    /** 加锁读取附件，防止删除请求并发重复写入操作日志。 */
    @Select("SELECT * FROM bug_attachment WHERE id = #{attachmentId} FOR UPDATE")
    BugAttachment selectByIdForUpdate(@Param("attachmentId") Long attachmentId);

    /** 逻辑删除仍保留文件元数据，满足审计和后续恢复排查需要。 */
    @Update("""
            UPDATE bug_attachment
            SET is_deleted = 1, deleted_by = #{deletedBy}, deleted_at = #{deletedAt}, updated_at = #{deletedAt}
            WHERE id = #{attachmentId} AND is_deleted = 0
            """)
    int markDeleted(@Param("attachmentId") Long attachmentId, @Param("deletedBy") Long deletedBy,
                    @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 一次读取多个验收业务记录下的附件及上传人，供验收历史批量回填使用，避免逐条记录查询附件。
     *
     * @param bugId 当前 Bug 主键
     * @param businessIds 验收记录主键集合，调用方会先保证非空
     * @return 不含存储路径的附件展示数据
     */
    @Select("""
            <script>
            SELECT a.id, a.bug_id, a.biz_type, a.biz_id, a.original_name, a.file_size, a.content_type,
                   a.uploader_id, u.display_name AS uploader_name, NULL AS uploader_avatar,
                   a.created_at, FALSE AS can_delete
            FROM bug_attachment a
            LEFT JOIN sys_user u ON u.id = a.uploader_id
            WHERE a.bug_id = #{bugId}
              AND a.is_deleted = 0
              AND a.biz_type IN ('ACCEPT_REJECT', 'ACCEPT_PASS')
              AND a.biz_id IN
              <foreach collection='businessIds' item='businessId' open='(' separator=',' close=')'>
                #{businessId}
              </foreach>
            ORDER BY a.created_at ASC, a.id ASC
            </script>
            """)
    List<BugAttachmentVO> selectActiveAcceptanceAttachments(@Param("bugId") Long bugId,
                                                             @Param("businessIds") Collection<Long> businessIds);
}
