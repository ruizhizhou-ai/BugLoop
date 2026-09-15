/**
 * 本文件提供 Bug 评论的数据访问入口。
 * 常规列表读取复用 MyBatis-Plus 条件构造器，只有需要并发安全的删除场景才使用显式行锁 SQL。
 */
package com.wjfz.bugloop.bug.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.bug.comment.entity.BugComment;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Bug 评论数据访问入口。 */
@Mapper
public interface BugCommentMapper extends BaseMapper<BugComment> {

    /**
     * 锁定单条评论，删除前使用以避免两个操作者同时将相同评论记两次审计日志。
     *
     * @param commentId 评论主键
     * @return 评论实体，不存在时返回 null
     */
    @Select("SELECT * FROM bug_comment WHERE id = #{commentId} FOR UPDATE")
    BugComment selectByIdForUpdate(@Param("commentId") Long commentId);

    /**
     * 逻辑删除评论并记录操作者与时间；物理记录保留给一级回复的“原评论已删除”提示使用。
     *
     * @param commentId 评论主键
     * @param deletedBy 删除人
     * @param deletedAt 删除时间
     * @return 实际更新行数
     */
    @Update("""
            UPDATE bug_comment
            SET is_deleted = 1, deleted_by = #{deletedBy}, deleted_at = #{deletedAt}, updated_at = #{deletedAt}
            WHERE id = #{commentId} AND is_deleted = 0
            """)
    int markDeleted(@Param("commentId") Long commentId, @Param("deletedBy") Long deletedBy,
                    @Param("deletedAt") LocalDateTime deletedAt);
}
