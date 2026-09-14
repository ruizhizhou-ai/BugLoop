/**
 * 本文件定义工作空间启停状态，停用状态用于保留历史数据并阻止业务写入。
 */
package com.wjfz.bugloop.workspace.entity;

/**
 * 工作空间状态。
 */
public enum WorkspaceStatus {
    ENABLED,
    DISABLED
}
