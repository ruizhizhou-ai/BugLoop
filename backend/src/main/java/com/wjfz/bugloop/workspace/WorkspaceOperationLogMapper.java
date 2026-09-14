/**
 * 本文件提供 workspace_operation_log 表的数据写入入口。
 */
package com.wjfz.bugloop.workspace;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作空间操作日志 Mapper。
 */
@Mapper
public interface WorkspaceOperationLogMapper extends BaseMapper<WorkspaceOperationLog> {
}
