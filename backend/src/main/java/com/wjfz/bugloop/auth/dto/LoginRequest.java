/**
 * 本文件定义登录请求参数。
 */
package com.wjfz.bugloop.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * @param username 登录用户名
 * @param password 登录密码
 */
public record LoginRequest(
        @NotBlank(message = "请输入用户名") String username,
        @NotBlank(message = "请输入密码") String password) {
}
