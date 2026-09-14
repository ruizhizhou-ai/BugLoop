/** 本文件定义统一分页结果，供列表接口返回记录、总数和实际分页参数。 */
package com.wjfz.bugloop.common.api;

import java.util.List;

/**
 * 分页响应。
 * @param records 当前页记录
 * @param total 符合筛选条件的总数
 * @param page 从 1 开始的页码
 * @param pageSize 每页数量
 * @param <T> 对外响应记录类型
 */
public record PageResponse<T>(List<T> records, long total, int page, int pageSize) {
}
