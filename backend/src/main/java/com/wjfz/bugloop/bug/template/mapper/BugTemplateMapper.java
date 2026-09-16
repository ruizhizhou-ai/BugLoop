/**
 * 本文件提供 Bug 个人模板的基础持久化入口。
 * 当前任务仅继承 MyBatis-Plus 通用能力，工作空间和创建人隔离查询由后续模板服务实现。
 */
package com.wjfz.bugloop.bug.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.bug.template.entity.BugTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * Bug 个人模板 Mapper，仅提供基础增删改查能力。
 */
@Mapper
public interface BugTemplateMapper extends BaseMapper<BugTemplate> {
}
