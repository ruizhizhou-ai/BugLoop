/**
 * 本文件定义注册请求参数，校验规则与 docs/04-domain-rules.md 第 37.1 节一致。
 */
package com.wjfz.bugloop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * @param username 登录用户名，3-64 位字母、数字或下划线
 * @param displayName 展示名称，1-64 位
 * @param password 登录密码，8-72 位
 */
public record RegisterRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,64}$", message = "用户名需为 3-64 位字母、数字或下划线")
        String username,

        @NotBlank(message = "请输入显示名称")
        @Size(max = 64, message = "显示名称不能超过 64 个字符")
        String displayName,

        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 72, message = "密码长度需为 8-72 位")
        String password) {
}
