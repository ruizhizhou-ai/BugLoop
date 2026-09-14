/**
 * 本文件集中处理业务异常、参数校验异常和未知异常，确保接口不会泄露内部堆栈。
 */
package com.wjfz.bugloop.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.wjfz.bugloop.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局 REST 异常处理器，负责把 Java 异常转换为符合 API Spec 的响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 将可预期业务失败转换为对应 HTTP 状态和业务错误码。
     *
     * @param exception 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    /**
     * 汇总 Bean Validation 的第一条提示，避免向前端暴露框架内部异常结构。
     *
     * @param exception 参数校验异常
     * @return 参数错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不合法" : error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(ApiResponse.failure(40001, message));
    }

    /**
     * 把 Sa-Token 抛出的未登录异常转换为 Spec 定义的 40101，不向前端暴露会话实现细节。
     *
     * @param exception 未登录异常
     * @return 未登录响应
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLoginException(NotLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(40101, "未登录或登录状态已失效"));
    }

    /**
     * 记录未知异常的完整堆栈，但仅向客户端返回固定提示，防止敏感实现细节泄露。
     *
     * @param exception 未被业务层识别的异常
     * @return 系统内部错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("未处理的系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(50000, "系统内部异常，请稍后重试"));
    }
}

