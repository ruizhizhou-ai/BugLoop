/** 本文件定义创建 Bug 的最小响应，前端通过主键跳转、通过 bugNo 展示业务编号。 */
package com.wjfz.bugloop.bug.vo;

/** 创建结果，id 是数据库主键，bugNo 是全局唯一可读编号。 */
public record BugCreatedVO(Long id, String bugNo) {
}
