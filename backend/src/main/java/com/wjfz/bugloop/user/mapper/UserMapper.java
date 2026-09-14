/**
 * 本文件提供 sys_user 表的数据访问入口。
 */
package com.wjfz.bugloop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjfz.bugloop.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper，常规查询使用 MyBatis-Plus 条件构造器，复杂查询后续再补充 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
