/**
 * 本文件提供 sys_user 表的数据访问入口。
 */
package com.wjfz.bugloop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户 Mapper，除基础查询外也提供工作空间成员选择所需的可用用户查询。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查询可加入指定工作空间的启用用户，并排除已经存在成员关系的账号。
     *
     * @param workspaceId 工作空间主键
     * @param keyword 用户名或显示名称关键字，为空时返回首批候选项
     * @param limit 单次返回上限，避免下拉选择加载全部用户
     * @return 按用户名排序的可选用户
     */
    @Select("""
            <script>
            SELECT u.*
            FROM sys_user u
            WHERE u.enabled = 1
              AND NOT EXISTS (
                  SELECT 1
                  FROM workspace_member wm
                  WHERE wm.workspace_id = #{workspaceId}
                    AND wm.user_id = u.id
              )
            <if test="keyword != null and keyword != ''">
              AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.display_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY u.username ASC, u.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<User> selectEnabledUsersAvailableForWorkspace(
            @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword,
            @Param("limit") int limit);
}
