/** 本文件定义 Bug 生命周期状态，供业务校验与数据库映射共同使用。 */
package com.wjfz.bugloop.bug.entity;

/** 一期固定状态，CLOSED 为终态。 */
public enum BugStatus {
    TODO, PROCESSING, WAIT_ACCEPTANCE, REOPENED, CLOSED
}
