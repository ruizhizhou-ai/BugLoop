/**
 * 本文件提供评论的分页查询和追加写入。
 * 查询关联用户展示字段，避免服务层逐条读取用户造成 N+1 查询。
 */
package com.wjfz.bugloop.bug.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.bug.comment.entity.BugComment;
import com.wjfz.bugloop.bug.comment.vo.BugCommentVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Bug 评论数据访问入口。 */
@Mapper
public interface BugCommentMapper extends BaseMapper<BugComment> {

    /** 按最新创建时间分页读取指定 Bug 的评论和用户展示信息。 */
    @Select("""
            SELECT c.id, c.user_id, u.username, u.display_name, c.content_md, c.created_at
            FROM bug_comment c
            LEFT JOIN sys_user u ON u.id = c.user_id
            WHERE c.bug_id = #{bugId}
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<BugCommentVO> selectPageByBugId(@Param("bugId") Long bugId,
                                         @Param("limit") int limit, @Param("offset") long offset);

    /** 按主键读取刚创建的评论，用于返回与列表一致的用户展示字段。 */
    @Select("""
            SELECT c.id, c.user_id, u.username, u.display_name, c.content_md, c.created_at
            FROM bug_comment c
            LEFT JOIN sys_user u ON u.id = c.user_id
            WHERE c.id = #{commentId}
            """)
    BugCommentVO selectViewById(@Param("commentId") Long commentId);
}
