/**
 * 本文件提供 workspace 表的数据访问入口，并提供成员变更所需的行级锁查询。
 */
package com.wjfz.bugloop.workspace;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 工作空间 Mapper。
 */
@Mapper
public interface WorkspaceMapper extends BaseMapper<Workspace> {

    /**
     * 锁定工作空间记录，使成员角色和最后一个 OWNER 校验在并发情况下保持一致。
     *
     * @param workspaceId 工作空间主键
     * @return 被锁定的工作空间，不存在时返回 null
     */
    @Select("SELECT * FROM workspace WHERE id = #{workspaceId} FOR UPDATE")
    Workspace selectByIdForUpdate(Long workspaceId);
}
