/**
 * 本文件定义修改工作空间基础信息的输入，PUT 接口按完整资源语义同时接收名称与描述。
 */
package com.wjfz.bugloop.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改工作空间请求。
 *
 * @param name 新名称
 * @param description 新描述，可以为空
 */
public record UpdateWorkspaceRequest(
        @NotBlank(message = "工作空间名称不能为空")
        @Size(max = 100, message = "工作空间名称不能超过 100 个字符")
        String name,
        @Size(max = 500, message = "工作空间描述不能超过 500 个字符")
        String description) {
}
