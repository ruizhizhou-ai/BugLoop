/** 本文件提供附件元数据的并发计数、加锁读取和逻辑删除，物理文件不在 Mapper 内处理。 */
package com.wjfz.bugloop.bug.attachment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.bug.attachment.entity.BugAttachment;
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
            SET is_deleted = 1, deleted_by = #{deletedBy}, deleted_at = #{deletedAt}
            WHERE id = #{attachmentId} AND is_deleted = 0
            """)
    int markDeleted(@Param("attachmentId") Long attachmentId, @Param("deletedBy") Long deletedBy,
                    @Param("deletedAt") LocalDateTime deletedAt);
}
