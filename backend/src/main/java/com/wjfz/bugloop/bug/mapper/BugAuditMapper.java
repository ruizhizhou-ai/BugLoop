/**
 * 本文件提供 Bug 审计、描述历史和验收记录的追加写入，以及详情需要的关联元数据查询。
 * 所有写入由 BugService 的事务包裹，禁止独立修改或删除审计记录。
 */
package com.wjfz.bugloop.bug.mapper;

import com.wjfz.bugloop.bug.entity.Bug;
import com.wjfz.bugloop.bug.vo.BugAcceptanceVO;
import com.wjfz.bugloop.bug.vo.BugAttachmentVO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Bug 关联数据访问入口，查询必须在 Bug 所属空间权限校验后执行。 */
@Mapper
public interface BugAuditMapper {

    /** 追加一次业务操作，描述包含人员显示名称和业务含义。 */
    @Insert("""
            INSERT INTO bug_operation_log
                (bug_id, workspace_id, operator_id, operation_type, field_name,
                 old_value, new_value, description, created_at)
            VALUES (#{bug.id}, #{bug.workspaceId}, #{operatorId}, #{type}, #{field},
                    #{oldValue}, #{newValue}, #{description}, #{bug.updatedAt})
            """)
    int insertLog(@Param("bug") Bug bug, @Param("operatorId") Long operatorId,
                  @Param("type") String type, @Param("field") String field,
                  @Param("oldValue") String oldValue, @Param("newValue") String newValue,
                  @Param("description") String description);

    /** 读取下一个描述历史序号；调用方已锁定工作空间，避免同一 Bug 的版本号竞争。 */
    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM bug_description_history WHERE bug_id = #{bugId}")
    int nextDescriptionVersion(Long bugId);

    /** 保存修改前 Markdown 原文，只有内容变化时才调用。 */
    @Insert("""
            INSERT INTO bug_description_history (bug_id, version_no, content_md, operator_id, created_at)
            VALUES (#{bug.id}, #{versionNo}, #{content}, #{operatorId}, #{bug.updatedAt})
            """)
    int insertHistory(@Param("bug") Bug bug, @Param("versionNo") int versionNo,
                      @Param("content") String content, @Param("operatorId") Long operatorId);

    /** 追加实际验收人的验收结论；服务保证来源状态只能为待验收。 */
    @Insert("""
            INSERT INTO bug_acceptance
                (bug_id, acceptor_id, result, comment_md, from_status, to_status, created_at)
            VALUES (#{bug.id}, #{operatorId}, #{result}, #{comment}, 'WAIT_ACCEPTANCE',
                    #{bug.status}, #{bug.updatedAt})
            """)
    int insertAcceptance(@Param("bug") Bug bug, @Param("operatorId") Long operatorId,
                         @Param("result") String result, @Param("comment") String comment);

    /** 按实际写入顺序读取最近一次验收，不受同秒时间戳影响。 */
    @Select("""
            SELECT id, acceptor_id, result, comment_md, from_status, to_status, created_at
            FROM bug_acceptance WHERE bug_id = #{bugId} ORDER BY id DESC LIMIT 1
            """)
    BugAcceptanceVO latestAcceptance(Long bugId);

    /** 仅返回未删除附件的展示字段，避免泄露服务器路径。 */
    @Select("""
            SELECT id, original_name, file_size, content_type, uploader_id, created_at
            FROM bug_attachment WHERE bug_id = #{bugId} AND is_deleted = 0 ORDER BY id
            """)
    List<BugAttachmentVO> attachments(Long bugId);
}
