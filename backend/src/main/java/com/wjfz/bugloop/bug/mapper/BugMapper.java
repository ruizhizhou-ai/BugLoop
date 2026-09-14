/**
 * 本文件提供 Bug 持久化能力。更新使用显式版本条件，确保数据库变更和审计处于同一事务。
 * NULL 字段显式写入，使驳回时清空 closed_at、清空修复草稿等操作不受默认更新策略影响。
 */
package com.wjfz.bugloop.bug.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.bug.entity.Bug;
import org.apache.ibatis.annotations.*;

/** Bug 数据入口，版本冲突由服务根据受影响行数转换为 40902。 */
@Mapper
public interface BugMapper extends BaseMapper<Bug> {

    /** 锁定最新 Bug 快照；必须先锁空间，避免 MySQL 可重复读返回等待锁之前的旧快照。 */
    @Select("SELECT * FROM bug WHERE id = #{bugId} FOR UPDATE")
    Bug selectByIdForUpdate(Long bugId);

    /** 使用数据库生成的主键补全业务编号，只在创建事务内部调用。 */
    @Update("UPDATE bug SET bug_no = #{bugNo} WHERE id = #{id}")
    int setBugNo(@Param("id") Long id, @Param("bugNo") String bugNo);

    /**
     * 更新当前快照并原子递增版本；不允许更改主键、空间、创建者和业务编号。
     * @param bug 含旧 version 和本次新字段的实体
     * @return 1 表示成功，0 表示旧版本已经失效
     */
    @Update("""
            UPDATE bug SET title = #{title}, description_md = #{descriptionMd}, priority = #{priority},
                status = #{status}, assignee_id = #{assigneeId}, acceptor_id = #{acceptorId},
                fix_description_md = #{fixDescriptionMd}, reopen_count = #{reopenCount},
                updated_at = #{updatedAt}, closed_at = #{closedAt}, version = version + 1
            WHERE id = #{id} AND workspace_id = #{workspaceId} AND version = #{version}
            """)
    int updateIfVersionMatches(Bug bug);
}
