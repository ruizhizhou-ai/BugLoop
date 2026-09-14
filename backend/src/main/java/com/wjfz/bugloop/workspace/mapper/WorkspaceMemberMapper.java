/**
 * 本文件提供工作空间成员关系的数据访问和未关闭 Bug 关联检查能力。
 */
package com.wjfz.bugloop.workspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.workspace.entity.WorkspaceMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 工作空间成员 Mapper。
 */
@Mapper
public interface WorkspaceMemberMapper extends BaseMapper<WorkspaceMember> {

    /**
     * 统计用户仍作为负责人或验收人的未关闭 Bug，防止移除成员后产生悬空业务责任。
     *
     * @param workspaceId 工作空间主键
     * @param userId 用户主键
     * @return 关联的未关闭 Bug 数量
     */
    @Select("""
            SELECT COUNT(*) FROM bug
            WHERE workspace_id = #{workspaceId}
              AND status <> 'CLOSED'
              AND (assignee_id = #{userId} OR acceptor_id = #{userId})
            """)
    long countOpenBugResponsibilities(Long workspaceId, Long userId);
}
