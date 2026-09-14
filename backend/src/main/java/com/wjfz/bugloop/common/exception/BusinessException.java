/**
 * 本文件定义可预期业务失败的异常模型，用于同时携带 HTTP 状态和稳定业务错误码。
 */
package com.wjfz.bugloop.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常由 Service 在权限、状态或数据规则不满足时抛出。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final int code;

    /**
     * 创建业务异常。
     *
     * @param httpStatus 与业务语义一致的 HTTP 状态
     * @param code Spec 中定义的业务错误码
     * @param message 面向用户的错误信息
     */
    public BusinessException(HttpStatus httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    /**
     * 返回异常对应的 HTTP 状态。
     *
     * @return HTTP 状态
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * 返回稳定的业务错误码。
     *
     * @return 业务错误码
     */
    public int getCode() {
        return code;
    }
}

