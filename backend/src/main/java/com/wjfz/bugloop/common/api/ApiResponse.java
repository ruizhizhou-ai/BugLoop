/**
 * 本文件定义所有业务接口的统一响应结构，使前端能够用一致方式处理成功和失败结果。
 */
package com.wjfz.bugloop.common.api;

/**
 * API 统一响应对象。
 *
 * @param code 业务错误码，0 表示成功
 * @param message 面向用户的结果说明
 * @param data 业务数据，失败时可以为空
 * @param <T> 业务数据类型
 */
public record ApiResponse<T>(int code, String message, T data) {

    /**
     * 创建成功响应。
     *
     * @param data 返回给调用方的业务数据
     * @param <T> 业务数据类型
     * @return code 为 0 的统一响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    /**
     * 创建失败响应，由异常处理器统一调用，避免业务接口自行拼装错误格式。
     *
     * @param code 业务错误码
     * @param message 可供用户理解的错误信息
     * @return 不包含业务数据的统一响应
     */
    public static ApiResponse<Void> failure(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}

