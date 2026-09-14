/**
 * 本文件定义创建工作空间的输入，并在进入业务层前完成基础格式校验。
 */
package com.wjfz.bugloop.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建工作空间请求。
 *
 * @param name 工作空间名称，去除首尾空白后长度为 1～100
 * @param description 可选描述，最长 500 字符
 */
public record CreateWorkspaceRequest(
        @NotBlank(message = "工作空间名称不能为空")
        @Size(max = 100, message = "工作空间名称不能超过 100 个字符")
        String name,
        @Size(max = 500, message = "工作空间描述不能超过 500 个字符")
        String description) {
}
